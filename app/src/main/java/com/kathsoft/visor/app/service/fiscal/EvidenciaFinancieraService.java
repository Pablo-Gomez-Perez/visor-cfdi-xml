package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.entity.*;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Escrituras financieras RF-023/024 sobre el modelo existente, con auditoría y límites atómicos. */
public final class EvidenciaFinancieraService {
    private final JpaContext jpa;
    /** Inyecta el contexto existente, sin abrir conexiones adicionales. */
    public EvidenciaFinancieraService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}
    /** Importe en moneda de pago y de factura, con referencia de evidencia externa local. */
    public record Pago(LocalDateTime fecha,String moneda,BigDecimal importePago,BigDecimal importeFactura,
            String referencia,String motivo,Long documentoRepRelacionado) {
        public Pago {
            Objects.requireNonNull(fecha);Objects.requireNonNull(importePago);Objects.requireNonNull(importeFactura);
            if(moneda==null||!moneda.matches("[A-Z]{3}")||moneda.equals("XXX"))throw new IllegalArgumentException("Moneda de pago requerida");
            positivo(importePago);positivo(importeFactura);referencia=texto(referencia,160);motivo=texto(motivo,600);
        }
    }
    /** Opción de vinculación a un DR original; el identificador permanece interno al control. */
    public record ReferenciaRep(long id,LocalDateTime fecha,String uuid,String monedaPago,BigDecimal importePago,
            String monedaFactura,BigDecimal importeFactura,String estadoRep,String estadoFactura) {
        /** Los datos SAT son los últimos guardados; no equivalen a consulta en línea. */
        public boolean candidataAplicable() {
            return "VIGENTE".equals(estadoRep) && "VIGENTE".equals(estadoFactura)
                    && importePago != null && importePago.signum()>0
                    && importeFactura != null && importeFactura.signum()>0;
        }
        @Override public String toString(){
            return fecha+" · REP "+uuid+" · "+(importeFactura==null?"Sin importe":importeFactura.toPlainString())
                    +" "+monedaFactura+" · "+(candidataAplicable()?"Vigente":"Revisar SAT/importes");
        }
    }
    /**
     * Aplicación manual ya persistida que puede conciliarse con un REP posterior.
     * El identificador de movimiento es interno y nunca se introduce manualmente en la vista.
     *
     * @param id identificador del movimiento del titular
     * @param fecha fecha y hora registradas del movimiento
     * @param moneda moneda del pago o cobro
     * @param importePago importe completo del movimiento en su moneda
     * @param importeAplicado importe aplicado en moneda de la factura
     * @param referencia referencia administrativa original, sin modificarla al vincular
     */
    public record AplicacionRegistrada(long id,LocalDateTime fecha,String moneda,
            BigDecimal importePago,BigDecimal importeAplicado,String referencia) {
        @Override public String toString(){
            return fecha+" · "+importePago.toPlainString()+" "+moneda+" · "+(referencia==null?"Sin referencia":referencia);
        }
    }

    /**
     * Lista hasta 200 aplicaciones manuales de esta factura todavía sin vínculo REP.
     * La consulta exige permiso vigente y alcance del titular, también para documentos compartidos.
     * Los movimientos repartidos entre varias obligaciones requieren conciliación específica y no
     * se ofrecen en esta operación de vinculación individual.
     *
     * @param actor usuario activo que consulta
     * @param titular contribuyente explícitamente asignado
     * @param documento factura disponible del titular
     * @return aplicaciones existentes; no crea obligaciones ni movimientos
     */
    @SuppressWarnings("unchecked")
    public List<AplicacionRegistrada> aplicacionesNoVinculadas(Long actor,long titular,long documento){
        return jpa.consultar(em->{
            AccesoFiscal.exigir(em,actor,titular,Permisos.FISCAL_CONSULTAR);
            AccesoFiscal.documento(em,titular,documento,false);
            List<Object[]> filas=em.createNativeQuery("""
                SELECT m.id,m.fecha_movimiento,m.moneda,m.importe,a.importe_moneda_obligacion,m.referencia
                FROM obligacion o JOIN aplicacion_pago a ON a.obligacion_id=o.id
                JOIN movimiento_pago m ON m.id=a.movimiento_pago_id AND m.contribuyente_id=o.contribuyente_id
                WHERE o.contribuyente_id=:t AND o.documento_id=:d AND m.origen='MANUAL'
                  AND NOT EXISTS(SELECT 1 FROM movimiento_pago_complemento mc
                    WHERE mc.movimiento_pago_id=m.id)
                  AND NOT EXISTS(SELECT 1 FROM aplicacion_pago otra
                    WHERE otra.movimiento_pago_id=m.id AND otra.obligacion_id<>o.id)
                ORDER BY m.fecha_movimiento,m.id
                """).setParameter("t",titular).setParameter("d",documento).setMaxResults(200).getResultList();
            return filas.stream().map(r->new AplicacionRegistrada(n(r[0]),fecha(r[1]),r[2].toString(),
                    (BigDecimal)r[3],(BigDecimal)r[4],r[5]==null?null:r[5].toString())).toList();
        });
    }

    /**
     * Vincula un movimiento manual ya aplicado con su DR importado posteriormente.
     * Sólo añade el vínculo y la auditoría; no registra otro movimiento ni otra aplicación.
     * Revalida alcance, disponibilidad, fecha, monedas, importes y equivalencia dentro de la
     * transacción. Un DR compartido puede conciliarse de forma independiente por cada titular.
     *
     * @param actor usuario activo con consulta y registro de evidencia
     * @param titular contribuyente explícitamente asignado
     * @param documento factura disponible a la que ya se aplicó el movimiento
     * @param movimientoId identificador interno del movimiento manual existente
     * @param drId identificador del documento relacionado original del REP
     * @param motivo justificación obligatoria de la conciliación
     * @return identificador del movimiento conservado
     */
    public long vincular(Long actor,long titular,long documento,long movimientoId,long drId,String motivo){
        if(movimientoId<=0||drId<=0)throw new IllegalArgumentException("Seleccione movimiento y DR disponibles");
        String razon=texto(motivo,500);
        return jpa.enTransaccion(em->{
            acceso(em,actor,titular,documento);
            Datos d=datos(em,titular,documento);
            @SuppressWarnings("unchecked") List<Object[]> aplicaciones=em.createNativeQuery("""
                SELECT m.fecha_movimiento,m.moneda,m.importe,a.importe_moneda_pago,
                  a.importe_moneda_obligacion,o.moneda,o.monto_original,m.naturaleza,
                  m.tercero_id,o.tercero_id,o.id
                FROM movimiento_pago m JOIN aplicacion_pago a ON a.movimiento_pago_id=m.id
                JOIN obligacion o ON o.id=a.obligacion_id AND o.contribuyente_id=m.contribuyente_id
                WHERE m.id=:m AND m.contribuyente_id=:t AND m.origen='MANUAL'
                  AND o.documento_id=:d AND o.naturaleza=:n FOR UPDATE
                """).setParameter("m",movimientoId).setParameter("t",titular).setParameter("d",documento)
                .setParameter("n",d.naturaleza()).getResultList();
            if(aplicaciones.size()!=1)throw new SecurityException("Movimiento manual no disponible para esta factura y contribuyente");
            Object[] a=aplicaciones.get(0);
            String naturaleza=d.naturaleza().equals("PAGAR")?"PAGO":"COBRO";
            if(!d.moneda().equals(a[5])||d.total().compareTo((BigDecimal)a[6])!=0
                    ||!naturaleza.equals(a[7])||!Objects.equals(d.tercero(),a[8]==null?null:n(a[8]))
                    ||!Objects.equals(d.tercero(),a[9]==null?null:n(a[9])))
                throw new IllegalArgumentException("Movimiento y obligación no concilian con moneda, importe o tercero de la factura");
            if(!em.createNativeQuery("SELECT obligacion_id FROM aplicacion_pago WHERE movimiento_pago_id=:m AND obligacion_id<>:o FOR UPDATE")
                    .setParameter("m",movimientoId).setParameter("o",n(a[10])).getResultList().isEmpty())
                throw new IllegalArgumentException("Movimiento aplicado a varias obligaciones; requiere conciliación específica");
            if(!em.createNativeQuery("SELECT pago_fiscal_documento_id FROM movimiento_pago_complemento WHERE movimiento_pago_id=:m FOR UPDATE")
                    .setParameter("m",movimientoId).getResultList().isEmpty())
                throw new IllegalArgumentException("Movimiento ya vinculado a un REP");

            List<?> documentosRep=em.createNativeQuery("""
                SELECT p.documento_id FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id
                JOIN contribuyente_documento cd ON cd.documento_id=p.documento_id AND cd.contribuyente_id=:t
                WHERE pd.id=:dr AND pd.uuid_documento=:uuid AND cd.archivado_en IS NULL
                """).setParameter("t",titular).setParameter("dr",drId).setParameter("uuid",d.uuid()).getResultList();
            if(documentosRep.size()!=1)throw new SecurityException("DR no disponible para esta factura y contribuyente");
            AccesoFiscal.documento(em,titular,n(documentosRep.get(0)),true);
            Object[] rep=(Object[])em.createNativeQuery("""
                SELECT p.fecha_pago,p.moneda,pd.moneda,p.monto,pd.importe_pagado,pd.equivalencia_dr,
                  cd.sentido,COALESCE((SELECT s.estado FROM consulta_estado_sat s WHERE s.documento_id=p.documento_id
                    ORDER BY s.fecha_consulta DESC,s.id DESC LIMIT 1),'SIN_CONSULTA'),
                  pd.tipo_cambio_dr,pc.version_complemento
                FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id
                JOIN contribuyente_documento cd ON cd.documento_id=p.documento_id AND cd.contribuyente_id=:t
                LEFT JOIN pago_fiscal_complemento pc ON pc.documento_id=p.documento_id
                WHERE pd.id=:dr AND pd.uuid_documento=:uuid AND cd.archivado_en IS NULL FOR UPDATE
                """).setParameter("t",titular).setParameter("dr",drId).setParameter("uuid",d.uuid()).getSingleResult();
            if("CANCELADO".equals(rep[7])||!d.sentido().equals(rep[6]))
                throw new IllegalArgumentException("REP cancelado o de sentido incompatible con la factura");
            if(!fecha(a[0]).equals(fecha(rep[0]))||!a[1].equals(rep[1])||!d.moneda().equals(rep[2])
                    ||rep[4]==null||((BigDecimal)a[4]).compareTo((BigDecimal)rep[4])!=0)
                throw new IllegalArgumentException("FechaPago, moneda o importe aplicado no coinciden exactamente con el DR");
            BigDecimal equivalencia=(BigDecimal)rep[5];
            if(a[1].equals(d.moneda())){
                if(equivalencia!=null&&equivalencia.compareTo(BigDecimal.ONE)!=0)
                    throw new IllegalArgumentException("Equivalencia distinta de uno entre monedas iguales");
                equivalencia=BigDecimal.ONE;
            }else if(equivalencia==null&&"1.0".equals(rep[9]))equivalencia=(BigDecimal)rep[8];
            if(equivalencia==null||equivalencia.signum()<=0)
                throw new IllegalArgumentException("Equivalencia original insuficiente para conciliar monedas");
            BigDecimal importeAplicado=(BigDecimal)a[4],importePago=(BigDecimal)a[3],importeMovimiento=(BigDecimal)a[2];
            positivo(importeAplicado);positivo(importePago);positivo(importeMovimiento);
            if(importeAplicado.compareTo(d.total())>0||importeMovimiento.compareTo(importePago)!=0
                    ||importePago.compareTo(importeAplicado.divide(equivalencia,6,java.math.RoundingMode.HALF_UP))!=0
                    ||importePago.compareTo((BigDecimal)rep[3])>0)
                throw new IllegalArgumentException("Los importes en MonedaP no concilian exactamente con movimiento y DR");
            if(!em.createNativeQuery("""
                SELECT mc.movimiento_pago_id FROM movimiento_pago_complemento mc
                JOIN movimiento_pago m ON m.id=mc.movimiento_pago_id
                WHERE mc.pago_fiscal_documento_id=:dr AND m.contribuyente_id=:t FOR UPDATE
                """).setParameter("dr",drId).setParameter("t",titular).getResultList().isEmpty())
                throw new IllegalArgumentException("DR ya vinculado para este contribuyente");
            em.createNativeQuery("INSERT INTO movimiento_pago_complemento(movimiento_pago_id,pago_fiscal_documento_id) VALUES (:m,:dr)")
                .setParameter("m",movimientoId).setParameter("dr",drId).executeUpdate();
            auditar(em,actor,titular,documento,"VINCULAR_REP",razon+"; movimiento="+movimientoId+"; DR="+drId);
            return movimientoId;
        });
    }
    /** Consulta hasta 200 evidencias REP disponibles del mismo titular, sin asumir su estado fiscal. */
    @SuppressWarnings("unchecked") public List<ReferenciaRep> referencias(Long actor,long titular,long documento) {
        return jpa.consultar(em->{
            AccesoFiscal.exigir(em,actor,titular,Permisos.FISCAL_CONSULTAR);AccesoFiscal.documento(em,titular,documento,false);
            List<Object[]> filas=em.createNativeQuery("""
                SELECT pd.id,p.fecha_pago,rep.uuid,p.moneda,pd.moneda,pd.importe_pagado,pd.equivalencia_dr,
                  COALESCE((SELECT es.estado FROM consulta_estado_sat es WHERE es.documento_id=rep.id
                    ORDER BY es.fecha_consulta DESC,es.id DESC LIMIT 1),'SIN_CONSULTA'),
                  COALESCE((SELECT es.estado FROM consulta_estado_sat es WHERE es.documento_id=d.id
                    ORDER BY es.fecha_consulta DESC,es.id DESC LIMIT 1),'SIN_CONSULTA')
                FROM documento_fiscal d JOIN pago_fiscal_documento pd ON pd.uuid_documento=d.uuid
                JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id JOIN documento_fiscal rep ON rep.id=p.documento_id
                JOIN contribuyente_documento cd ON cd.documento_id=rep.id AND cd.contribuyente_id=:t AND cd.archivado_en IS NULL
                JOIN contribuyente_documento df ON df.documento_id=d.id AND df.contribuyente_id=:t AND df.archivado_en IS NULL
                WHERE d.id=:d AND NOT EXISTS(SELECT 1 FROM movimiento_pago_complemento m JOIN movimiento_pago mp ON mp.id=m.movimiento_pago_id WHERE m.pago_fiscal_documento_id=pd.id AND mp.contribuyente_id=:t)
                ORDER BY p.fecha_pago,p.id,pd.id
                """).setParameter("t",titular).setParameter("d",documento).setMaxResults(200).getResultList();
            return filas.stream().map(r->{
                BigDecimal valor=(BigDecimal)r[5],eq=r[3].equals(r[4])?BigDecimal.ONE:(BigDecimal)r[6];
                BigDecimal importe=valor==null||eq==null||eq.signum()<=0?null:valor.divide(eq,6,java.math.RoundingMode.HALF_UP);
                return new ReferenciaRep(n(r[0]),fecha(r[1]),r[2].toString(),r[3].toString(),importe,r[4].toString(),valor,r[7].toString(),r[8].toString());
            }).toList();
        });
    }
    /**
     * Confirma una sugerencia REP de la factura, sin introducir UUID, fecha ni importe.
     * Si existe una aplicación manual exactamente coincidente, sólo la vincula:
     * jamás crea un segundo cobro/pago. En otro caso reutiliza registrar() y
     * todas sus validaciones de saldo, divisa, fecha y control transaccional.
     *
     * <p>La detección es automática; la modificación de saldo requiere
     * confirmación expresa del usuario. No constituye validación fiscal de IVA.</p>
     *
     * @return ID del movimiento creado o del movimiento manual conciliado
     */
    public long confirmarRep(Long actor,long titular,long documento,long drId){
        ReferenciaRep r=referencias(actor,titular,documento).stream()
            .filter(x->x.id()==drId).findFirst()
            .orElseThrow(()->new IllegalArgumentException("El REP ya no está disponible; actualice Financiero"));
        if(!r.candidataAplicable())
            throw new IllegalArgumentException("El REP y la factura deben tener estado SAT VIGENTE registrado e importes conciliables");
        List<AplicacionRegistrada> manuales=aplicacionesNoVinculadas(actor,titular,documento).stream()
            .filter(a->a.fecha().equals(r.fecha()) && a.moneda().equals(r.monedaPago())
                && a.importePago().compareTo(r.importePago())==0
                && a.importeAplicado().compareTo(r.importeFactura())==0).toList();
        if(manuales.size()>1)
            throw new IllegalArgumentException("Varias aplicaciones manuales coinciden con el REP; concilie individualmente");
        String motivo="Conciliación REP confirmada por el usuario; DR="+drId;
        if(manuales.size()==1)
            return vincular(actor,titular,documento,manuales.get(0).id(),drId,motivo);
        Pago pago=new Pago(r.fecha(),r.monedaPago(),r.importePago(),r.importeFactura(),
            "REP-DR-"+drId,motivo,drId);
        return registrarInterno(actor,titular,documento,pago,true);
    }

    /**
     * Propuestas de notas de crédito relacionadas de forma explícita mediante
     * CfdiRelacionados TipoRelacion=01 (SAT). No calcula efectos fiscales.
     * La nota y la factura deben estar disponibles para el mismo titular.
     */
    public record NotaRelacionada(long documento,String uuid,LocalDate emision,String moneda,
        BigDecimal total,BigDecimal disponible,String estadoSat) {
        /** Disponible no equivale a saldo de la factura; el usuario confirma su aplicación. */
        public boolean aplicable(){return "VIGENTE".equals(estadoSat)&&disponible.signum()>0;}
        @Override public String toString(){
            return emision+" · "+uuid+" · "+disponible.toPlainString()+" "+moneda
                +(aplicable()?"":" · Revisar SAT/saldo");
        }
    }

    /** Lista NC relacionadas por UUID, incluso si ya tienen aplicaciones parciales. */
    @SuppressWarnings("unchecked")
    public List<NotaRelacionada> notasRelacionadas(Long actor,long titular,long documento){
        return jpa.consultar(em->{
            AccesoFiscal.exigir(em,actor,titular,Permisos.FISCAL_CONSULTAR);
            AccesoFiscal.documento(em,titular,documento,false);
            List<Object[]> r=em.createNativeQuery("""
                SELECT nc.id,nc.uuid,nc.fecha_emision,c.moneda,c.total,
                    c.total-COALESCE((SELECT SUM(a.importe_moneda_obligacion)
                      FROM ajuste_obligacion a JOIN obligacion o ON o.id=a.obligacion_id
                      WHERE a.documento_soporte_id=nc.id AND o.contribuyente_id=:t),0) disponible,
                    COALESCE((SELECT es.estado FROM consulta_estado_sat es WHERE es.documento_id=nc.id
                       ORDER BY es.fecha_consulta DESC,es.id DESC LIMIT 1),'SIN_CONSULTA')
                FROM documento_fiscal factura
                JOIN cfdi_relacion_uuid ru ON ru.uuid_relacionado=factura.uuid
                JOIN cfdi_relacion_grupo g ON g.id=ru.grupo_id AND g.tipo_relacion='01'
                JOIN documento_fiscal nc ON nc.id=g.documento_id
                JOIN cfdi c ON c.documento_id=nc.id AND c.tipo='E'
                JOIN contribuyente_documento cd ON cd.documento_id=nc.id
                      AND cd.contribuyente_id=:t AND cd.archivado_en IS NULL
                WHERE factura.id=:d AND EXISTS (
                  SELECT 1 FROM contribuyente_documento fc
                  WHERE fc.documento_id=factura.id AND fc.contribuyente_id=:t
                        AND fc.archivado_en IS NULL)
                ORDER BY nc.fecha_emision,nc.id
                """).setParameter("t",titular).setParameter("d",documento).setMaxResults(200).getResultList();
            return r.stream().map(x->new NotaRelacionada(n(x[0]),x[1].toString(),
                fecha(x[2]).toLocalDate(),x[3].toString(),(BigDecimal)x[4],
                (BigDecimal)x[5],x[6].toString())).toList();
        });
    }

    /** Resuelve el soporte por UUID dentro del alcance autorizado antes de aplicar el ajuste. */
    public long ajustarPorUuid(Long actor,long titular,long documento,String uuid,LocalDate fecha,String tipo,BigDecimal importe,String motivo) {
        Long id=jpa.consultar(em->{
            AccesoFiscal.exigir(em,actor,titular,Permisos.FISCAL_REGISTRAR_EVIDENCIA);
            List<?> r=em.createNativeQuery("SELECT d.id FROM documento_fiscal d JOIN contribuyente_documento cd ON cd.documento_id=d.id WHERE cd.contribuyente_id=:t AND cd.archivado_en IS NULL AND d.uuid=:uuid").setParameter("t",titular).setParameter("uuid",uuid).getResultList();
            if(r.size()!=1)throw new IllegalArgumentException("Soporte no disponible para el contribuyente");return n(r.get(0));
        });
        return ajustar(actor,titular,documento,id,fecha,tipo,importe,motivo);
    }
    /**
     * Registra pago/cobro y aplicación en una transacción. Un REP debe vincularse explícitamente;
     * las mismas evidencias no se suman dos veces. Referencias repetidas se rechazan antes de escribir.
     */
    public long registrar(Long actor,long titular,long documento,Pago pago){
        return registrarInterno(actor,titular,documento,pago,false);
    }

    private long registrarInterno(Long actor,long titular,long documento,Pago pago,boolean requiereEstadoVigente){
        Objects.requireNonNull(pago);
        return jpa.enTransaccion(em->{
            acceso(em,actor,titular,documento);
            Datos d=datos(em,titular,documento);long obligacion=obligacion(em,titular,documento,d);
            if(pago.moneda().equals(d.moneda())&&pago.importePago().compareTo(pago.importeFactura())!=0)throw new IllegalArgumentException("Importes distintos para una misma moneda");
            if(pago.fecha().toLocalDate().isBefore(d.fecha()))throw new IllegalArgumentException("Anticipo anterior a factura requiere revisión y soporte específico");
            var repetidos=em.createNativeQuery("SELECT m.id FROM movimiento_pago m JOIN aplicacion_pago a ON a.movimiento_pago_id=m.id WHERE a.obligacion_id=:o AND m.referencia=:r FOR UPDATE")
                .setParameter("o",obligacion).setParameter("r",pago.referencia()).getResultList();
            if(!repetidos.isEmpty())throw new IllegalArgumentException("Referencia ya aplicada a esta factura");
            @SuppressWarnings("unchecked") List<Object[]> reps=em.createNativeQuery("""
                SELECT pd.id,p.fecha_pago,p.moneda,pd.moneda,p.monto,pd.importe_pagado,pd.equivalencia_dr
                FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id
                JOIN contribuyente_documento cd ON cd.documento_id=p.documento_id AND cd.contribuyente_id=:t AND cd.archivado_en IS NULL
                WHERE pd.uuid_documento=:uuid AND COALESCE((SELECT s.estado FROM consulta_estado_sat s WHERE s.documento_id=p.documento_id ORDER BY s.fecha_consulta DESC,s.id DESC LIMIT 1),'SIN_CONSULTA')<>'CANCELADO'
                FOR UPDATE
                """).setParameter("t",titular).setParameter("uuid",d.uuid()).getResultList();
            Object[] rep=null;
            if(pago.documentoRepRelacionado()!=null){
                rep=reps.stream().filter(r->n(r[0])==pago.documentoRepRelacionado()).findFirst().orElseThrow(()->new IllegalArgumentException("DR no disponible para esta factura"));
                if(requiereEstadoVigente){
                    if(!"VIGENTE".equals(d.sat())) throw new IllegalArgumentException("Factura sin estado SAT VIGENTE confirmado");
                    String repSat=(String)em.createNativeQuery("""
                        SELECT COALESCE((SELECT s.estado FROM consulta_estado_sat s WHERE s.documento_id=p.documento_id
                            ORDER BY s.fecha_consulta DESC,s.id DESC LIMIT 1),'SIN_CONSULTA')
                        FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE pd.id=:dr
                        """).setParameter("dr",pago.documentoRepRelacionado()).getSingleResult();
                    if(!"VIGENTE".equals(repSat)) throw new IllegalArgumentException("REP sin estado SAT VIGENTE confirmado");
                }
                if(!pago.moneda().equals(rep[2])||!d.moneda().equals(rep[3])||rep[5]==null||pago.importeFactura().compareTo((BigDecimal)rep[5])!=0||!pago.fecha().equals(fecha(rep[1])))throw new IllegalArgumentException("La evidencia no coincide con FechaPago, moneda o ImpPagado del DR");
                BigDecimal eq=pago.moneda().equals(d.moneda())?BigDecimal.ONE:(BigDecimal)rep[6];
                if(eq==null||eq.signum()<=0||pago.importePago().compareTo(pago.importeFactura().divide(eq,6,java.math.RoundingMode.HALF_UP))!=0||pago.importePago().compareTo((BigDecimal)rep[4])>0)throw new IllegalArgumentException("Importe en MonedaP no concilia con el DR");
                if(!em.createNativeQuery("SELECT mc.movimiento_pago_id FROM movimiento_pago_complemento mc JOIN movimiento_pago m ON m.id=mc.movimiento_pago_id WHERE mc.pago_fiscal_documento_id=:dr AND m.contribuyente_id=:t FOR UPDATE").setParameter("dr",pago.documentoRepRelacionado()).setParameter("t",titular).getResultList().isEmpty())throw new IllegalArgumentException("DR ya vinculado a un movimiento");
            }else if(!reps.isEmpty())throw new IllegalArgumentException("Existen REP: vincule el DR correspondiente para evitar duplicación");
            BigDecimal disponible=saldoActual(em,obligacion,d.total());
            DistribucionDecimal.validar(disponible,List.of(pago.importeFactura()));
            MovimientoPagoEntity movimiento=new MovimientoPagoEntity(titular,d.tercero(),d.naturaleza().equals("PAGAR")?"PAGO":"COBRO",pago.fecha(),pago.moneda(),pago.importePago(),pago.referencia(),pago.motivo(),actor,requiereEstadoVigente?"COMPLEMENTO":"MANUAL");
            em.persist(movimiento);em.flush();
            em.persist(new AplicacionPagoEntity(movimiento.getId(),obligacion,pago.importePago(),pago.importeFactura()));em.flush();
            if(rep!=null)em.createNativeQuery("INSERT INTO movimiento_pago_complemento(movimiento_pago_id,pago_fiscal_documento_id) VALUES (:m,:dr)").setParameter("m",movimiento.getId()).setParameter("dr",pago.documentoRepRelacionado()).executeUpdate();
            auditar(em,actor,titular,documento,"REGISTRAR_PAGO",pago.motivo()+"; movimiento="+movimiento.getId());
            return movimiento.getId();
        });
    }
    /** Registra reducción de saldo con soporte, sin inferir devolución efectiva ni efecto fiscal automático. */
    public long ajustar(Long actor,long titular,long documento,long soporte,LocalDate fecha,String tipo,BigDecimal importe,String motivo){
        positivo(importe);String razon=texto(motivo,500);Objects.requireNonNull(fecha);
        if(!Set.of("NOTA_CREDITO","DESCUENTO","DEVOLUCION","ANTICIPO","OTRO").contains(tipo))throw new IllegalArgumentException("Tipo de ajuste inválido");
        return jpa.enTransaccion(em->{
            // Orden común de bloqueo evita interbloqueo entre dos ajustes cruzados.
            AccesoFiscal.exigir(em,actor,titular,Permisos.FISCAL_REGISTRAR_EVIDENCIA);
            AccesoFiscal.exigir(em,actor,titular,Permisos.FISCAL_CONSULTAR);
            AccesoFiscal.documento(em,titular,Math.min(documento,soporte),true);AccesoFiscal.documento(em,titular,Math.max(documento,soporte),true);
            Datos d=datos(em,titular,documento),s=datosSoporte(em,titular,soporte);
            if(!d.moneda().equals(s.moneda())||!Objects.equals(d.tercero(),s.tercero())||!d.sentido().equals(s.sentido())||documento==soporte||fecha.isBefore(d.fecha())||fecha.isBefore(s.fecha()))throw new IllegalArgumentException("Soporte, fecha o moneda del ajuste no válidos");
            if(tipo.equals("NOTA_CREDITO")){
                if(!s.tipo().equals("E"))throw new IllegalArgumentException("Nota de crédito requiere CFDI E");
                List<?> relaciones=em.createNativeQuery("""
                    SELECT g.id FROM cfdi_relacion_grupo g JOIN cfdi_relacion_uuid u ON u.grupo_id=g.id
                    WHERE g.documento_id=:soporte AND g.tipo_relacion='01' AND u.uuid_relacionado=:uuid
                    """).setParameter("soporte",soporte).setParameter("uuid",d.uuid()).getResultList();
                if(relaciones.isEmpty())throw new IllegalArgumentException("La nota de crédito no identifica esta factura como relacionada (TipoRelacion 01)");
                if(!"VIGENTE".equals(s.sat()))throw new IllegalArgumentException("Nota de crédito sin SAT VIGENTE; revisar antes de aplicar");
            }
            long obligacion=obligacion(em,titular,documento,d);
            if(!em.createNativeQuery("SELECT id FROM ajuste_obligacion WHERE obligacion_id=:o AND documento_soporte_id=:s FOR UPDATE").setParameter("o",obligacion).setParameter("s",soporte).getResultList().isEmpty())throw new IllegalArgumentException("Soporte ya aplicado a esta obligación");
            DistribucionDecimal.validar(saldoActual(em,obligacion,d.total()),List.of(importe));
            BigDecimal aplicado=BigDecimal.ZERO;
            for(Object x:em.createNativeQuery("SELECT a.importe_moneda_obligacion FROM ajuste_obligacion a JOIN obligacion o ON o.id=a.obligacion_id WHERE a.documento_soporte_id=:s AND o.contribuyente_id=:t FOR UPDATE").setParameter("s",soporte).setParameter("t",titular).getResultList())aplicado=aplicado.add((BigDecimal)x);
            DistribucionDecimal.validar(s.total(),List.of(aplicado,importe));
            AjusteObligacionEntity ajuste=new AjusteObligacionEntity(obligacion,soporte,tipo,fecha,importe,razon,actor);em.persist(ajuste);em.flush();
            auditar(em,actor,titular,documento,"REGISTRAR_AJUSTE",razon+"; ajuste="+ajuste.getId());return ajuste.getId();
        });
    }
    private record Datos(String uuid,String tipo,String sentido,String moneda,BigDecimal total,LocalDate fecha,Long tercero,String sat){
        String naturaleza(){return sentido.equals("RECIBIDO")?"PAGAR":"COBRAR";}
    }
    private static Datos datosSoporte(EntityManager em,long titular,long documento){
        Object[] r=(Object[])em.createNativeQuery("""
            SELECT d.uuid,c.tipo,cd.sentido,c.moneda,c.total,d.fecha_emision,p.tercero_id,
            COALESCE((SELECT s.estado FROM consulta_estado_sat s WHERE s.documento_id=d.id ORDER BY s.fecha_consulta DESC,s.id DESC LIMIT 1),'SIN_CONSULTA')
            FROM documento_fiscal d JOIN cfdi c ON c.documento_id=d.id JOIN contribuyente_documento cd ON cd.documento_id=d.id AND cd.contribuyente_id=:t
            LEFT JOIN documento_participante p ON p.documento_id=d.id AND p.rol=CASE WHEN cd.sentido='RECIBIDO' THEN 'E' ELSE 'R' END WHERE d.id=:d
            """).setParameter("t",titular).setParameter("d",documento).getSingleResult();
        Datos datos=new Datos(r[0].toString(),r[1].toString(),r[2].toString(),r[3].toString(),(BigDecimal)r[4],fecha(r[5]).toLocalDate(),r[6]==null?null:n(r[6]),r[7].toString());
        if(datos.sat().equals("CANCELADO")||datos.moneda().equals("XXX")||datos.sentido().equals("AMBOS"))throw new IllegalArgumentException("Documento cancelado, sin moneda o de sentido ambiguo");
        return datos;
    }
    private static Datos datos(EntityManager em,long t,long doc){Datos d=datosSoporte(em,t,doc);if(!d.tipo().equals("I"))throw new IllegalArgumentException("La obligación requiere CFDI I; otros tipos necesitan revisión específica");return d;}
    private static long obligacion(EntityManager em,long titular,long documento,Datos d){
        List<?> r=em.createNativeQuery("SELECT id,moneda,monto_original,tercero_id FROM obligacion WHERE contribuyente_id=:t AND documento_id=:d AND naturaleza=:n FOR UPDATE").setParameter("t",titular).setParameter("d",documento).setParameter("n",d.naturaleza()).getResultList();
        if(!r.isEmpty()){
            Object[] o=(Object[])r.get(0);
            if(!d.moneda().equals(o[1])||d.total().compareTo((BigDecimal)o[2])!=0||!Objects.equals(d.tercero(),o[3]==null?null:n(o[3])))throw new IllegalArgumentException("La obligación existente no concilia con total, moneda o tercero del CFDI; requiere revisión");
            return n(o[0]);
        }
        ObligacionEntity o=new ObligacionEntity(titular,documento,d.tercero(),d.naturaleza(),d.moneda(),d.total(),d.fecha());em.persist(o);em.flush();return o.getId();
    }
    private static BigDecimal saldoActual(EntityManager em,long obligacion,BigDecimal original){
        BigDecimal saldo=original;
        for(Object valor:em.createNativeQuery("SELECT importe_moneda_obligacion FROM aplicacion_pago WHERE obligacion_id=:o FOR UPDATE").setParameter("o",obligacion).getResultList())saldo=saldo.subtract((BigDecimal)valor);
        for(Object valor:em.createNativeQuery("SELECT importe_moneda_obligacion FROM ajuste_obligacion WHERE obligacion_id=:o FOR UPDATE").setParameter("o",obligacion).getResultList())saldo=saldo.subtract((BigDecimal)valor);
        return saldo;
    }
    private static void acceso(EntityManager em,Long actor,long t,long d){AccesoFiscal.exigir(em,actor,t,Permisos.FISCAL_CONSULTAR);AccesoFiscal.exigir(em,actor,t,Permisos.FISCAL_REGISTRAR_EVIDENCIA);AccesoFiscal.documento(em,t,d,true);}
    private static void auditar(EntityManager em,Long actor,long t,long d,String accion,String motivo){
        em.createNativeQuery("INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo) VALUES (:u,:t,'EVIDENCIA_FINANCIERA',:d,:a,:m)").setParameter("u",actor).setParameter("t",t).setParameter("d",d).setParameter("a",accion).setParameter("m",motivo.length()>600?motivo.substring(0,600):motivo).executeUpdate();
    }
    private static String texto(String s,int max){if(s==null||s.isBlank()||s.strip().length()>max)throw new IllegalArgumentException("Referencia y motivo obligatorios de hasta "+max+" caracteres");return s.strip();}
    private static void positivo(BigDecimal n){if(n==null||n.signum()<=0)throw new IllegalArgumentException("Importe positivo requerido");DistribucionDecimal.validar(n,List.of(n));}
    private static long n(Object n){return ((Number)n).longValue();}
    private static LocalDateTime fecha(Object o){return o instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)o;}
}
