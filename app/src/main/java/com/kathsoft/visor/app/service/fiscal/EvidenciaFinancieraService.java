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
    public record ReferenciaRep(long id,LocalDateTime fecha,String uuid,String monedaPago,BigDecimal importePago,String monedaFactura,BigDecimal importeFactura) {
        @Override public String toString(){return fecha+" · "+uuid+" · "+importeFactura+" "+monedaFactura;}
    }
    /** Consulta hasta 200 evidencias REP disponibles del mismo titular, sin asumir su estado fiscal. */
    @SuppressWarnings("unchecked") public List<ReferenciaRep> referencias(Long actor,long titular,long documento) {
        return jpa.consultar(em->{
            AccesoFiscal.exigir(em,actor,titular,Permisos.FISCAL_CONSULTAR);AccesoFiscal.documento(em,titular,documento,false);
            List<Object[]> filas=em.createNativeQuery("""
                SELECT pd.id,p.fecha_pago,rep.uuid,p.moneda,pd.moneda,pd.importe_pagado,pd.equivalencia_dr
                FROM documento_fiscal d JOIN pago_fiscal_documento pd ON pd.uuid_documento=d.uuid
                JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id JOIN documento_fiscal rep ON rep.id=p.documento_id
                JOIN contribuyente_documento cd ON cd.documento_id=rep.id AND cd.contribuyente_id=:t AND cd.archivado_en IS NULL
                WHERE d.id=:d AND NOT EXISTS(SELECT 1 FROM movimiento_pago_complemento m JOIN movimiento_pago mp ON mp.id=m.movimiento_pago_id WHERE m.pago_fiscal_documento_id=pd.id AND mp.contribuyente_id=:t)
                ORDER BY p.fecha_pago,p.id,pd.id
                """).setParameter("t",titular).setParameter("d",documento).setMaxResults(200).getResultList();
            return filas.stream().map(r->{
                BigDecimal valor=(BigDecimal)r[5],eq=r[3].equals(r[4])?BigDecimal.ONE:(BigDecimal)r[6];
                BigDecimal importe=valor==null||eq==null||eq.signum()<=0?null:valor.divide(eq,6,java.math.RoundingMode.HALF_UP);
                return new ReferenciaRep(n(r[0]),fecha(r[1]),r[2].toString(),r[3].toString(),importe,r[4].toString(),valor);
            }).toList();
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
                if(!pago.moneda().equals(rep[2])||!d.moneda().equals(rep[3])||rep[5]==null||pago.importeFactura().compareTo((BigDecimal)rep[5])!=0||!pago.fecha().equals(fecha(rep[1])))throw new IllegalArgumentException("La evidencia no coincide con FechaPago, moneda o ImpPagado del DR");
                BigDecimal eq=pago.moneda().equals(d.moneda())?BigDecimal.ONE:(BigDecimal)rep[6];
                if(eq==null||eq.signum()<=0||pago.importePago().compareTo(pago.importeFactura().divide(eq,6,java.math.RoundingMode.HALF_UP))!=0||pago.importePago().compareTo((BigDecimal)rep[4])>0)throw new IllegalArgumentException("Importe en MonedaP no concilia con el DR");
                if(!em.createNativeQuery("SELECT mc.movimiento_pago_id FROM movimiento_pago_complemento mc JOIN movimiento_pago m ON m.id=mc.movimiento_pago_id WHERE mc.pago_fiscal_documento_id=:dr AND m.contribuyente_id=:t FOR UPDATE").setParameter("dr",pago.documentoRepRelacionado()).setParameter("t",titular).getResultList().isEmpty())throw new IllegalArgumentException("DR ya vinculado a un movimiento");
            }else if(!reps.isEmpty())throw new IllegalArgumentException("Existen REP: vincule el DR correspondiente para evitar duplicación");
            BigDecimal disponible=saldoActual(em,obligacion,d.total());
            DistribucionDecimal.validar(disponible,List.of(pago.importeFactura()));
            MovimientoPagoEntity movimiento=new MovimientoPagoEntity(titular,d.tercero(),d.naturaleza().equals("PAGAR")?"PAGO":"COBRO",pago.fecha(),pago.moneda(),pago.importePago(),pago.referencia(),pago.motivo(),actor);
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
            if(tipo.equals("NOTA_CREDITO")&&!s.tipo().equals("E"))throw new IllegalArgumentException("Nota de crédito requiere CFDI E");
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
