package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import java.util.List;
import java.util.Objects;

/** Papeles de trabajo del MVP: agregados SQL reconstruibles, originales y evidencia separados. */
public final class ResumenFiscalService {
    private final JpaContext jpa;
    /** Contexto de la aplicación; constructor sin consultas. */
    public ResumenFiscalService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}
    /** Vistas consolidadas, incluyendo originales y conciliación RF-022. */
    public enum Seccion { GENERALES, INGRESOS, EGRESOS, IVA, IEPS, RETENCIONES, TERCEROS, FINANCIERO, INCIDENCIAS, ORIGENES, IMPUESTOS, GLOBALES, IMPUESTOS_PAGO, TOTALES, CONCILIACION, IVA_RESUMEN, IVA_MANUAL, IMPUESTOS_DR, CONCILIACION_DR_P, CONCILIACION_TOTALES }

    /** Lista únicamente contribuyentes asignados y revalida el permiso fiscal. */
    @SuppressWarnings("unchecked")
    public java.util.List<com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Titular> titulares(Long actor) {
        return jpa.consultar(em->{
            com.kathsoft.visor.app.security.AutorizacionService.exigir(em,actor,Permisos.FISCAL_CONSULTAR);
            java.util.List<Object[]> r=em.createNativeQuery("SELECT c.id,c.rfc,c.nombre_razon_social FROM contribuyente c JOIN usuario_contribuyente uc ON uc.contribuyente_id=c.id WHERE uc.usuario_id=:u ORDER BY c.nombre_razon_social,c.id").setParameter("u",actor).getResultList();
            return r.stream().map(x->new com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Titular(((Number)x[0]).longValue(),x[1].toString(),x[2].toString())).toList();
        });
    }

    /** Despacha servicios de negocio; ninguna cifra se calcula desde el TableModel. */
    public TablaFiscal consultar(Long actor,FiltroFiscal f,Seccion seccion,int pagina){
        return switch(seccion){
            case CONCILIACION_DR_P->new ConciliacionPagosService(jpa).documentosContraPago(actor,f,pagina);
            case CONCILIACION_TOTALES->new ConciliacionPagosService(jpa).totales(actor,f,pagina);
            case IVA_RESUMEN->new AuxiliarIvaService(jpa).resumir(actor,f,pagina);
            case IVA_MANUAL->new AuxiliarIvaService(jpa).consultarManuales(actor,f,pagina);
            case IMPUESTOS_DR->new ImpuestosService(jpa).consultar(actor,f,ImpuestosService.Nivel.PAGO_DOCUMENTO,pagina);
            case IVA->new AuxiliarIvaService(jpa).consultar(actor,f,pagina);
            case TERCEROS->new TercerosFiscalService(jpa).consultar(actor,f,pagina);
            case FINANCIERO->new TercerosFiscalService(jpa).saldos(actor,f,pagina);
            case IMPUESTOS->new ImpuestosService(jpa).consultar(actor,f,ImpuestosService.Nivel.CONCEPTO,pagina);
            case GLOBALES->new ImpuestosService(jpa).consultar(actor,f,ImpuestosService.Nivel.GLOBAL,pagina);
            case IMPUESTOS_PAGO->new ImpuestosService(jpa).consultar(actor,f,ImpuestosService.Nivel.PAGO,pagina);
            case TOTALES->new ImpuestosService(jpa).consultar(actor,f,ImpuestosService.Nivel.TOTALES,pagina);
            case CONCILIACION->new ImpuestosService(jpa).conciliar(actor,f,pagina);
            default->jpa.enTransaccion(em->{
                AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
                String sql;List<String> columnas;
                if(seccion==Seccion.GENERALES||seccion==Seccion.INGRESOS){
                    sql=FiscalSql.BASE+" SELECT u.sentido,u.tipo,u.moneda,u.estado_sat,COUNT(*),SUM(u.subtotal),SUM(u.descuento),SUM(u.subtotal-u.descuento),SUM(u.total) FROM universo u WHERE u.tipo IN ('I','E','N')"+(seccion==Seccion.INGRESOS?" AND u.sentido='EMITIDO'":"")+FiscalSql.filtros(f,"u")+" GROUP BY u.sentido,u.tipo,u.moneda,u.estado_sat ORDER BY u.sentido,u.tipo,u.moneda,u.estado_sat";
                    columnas=List.of("Sentido","Tipo","Moneda","SAT","Documentos","Subtotal documental","Descuentos documentales","Neto documental","Total documental");
                }else if(seccion==Seccion.EGRESOS){
                    sql=FiscalSql.BASE+FiscalSql.IMPUESTOS_DISTRIBUIDOS+"""
                      ,iva_partes AS (
                        SELECT concepto_id,categoria_id,SUM(base_distribuida) base_distribuida,SUM(impuesto_distribuido) impuesto_distribuido FROM impuestos_distribuidos WHERE impuesto='002' AND movimiento='TRASLADO' GROUP BY concepto_id,categoria_id
                      ),detalle AS (
                        SELECT p.*,u.sentido,u.tipo,u.moneda,u.estado_sat,cc.objeto_impuesto,
                          CASE WHEN ic.lineas=1 AND p.original_neto>0 AND cc.objeto_impuesto<>'01' THEN ic.factor ELSE NULL END factor,
                          ic.tasa,
                          CASE WHEN ic.lineas=1 AND p.original_neto>0 THEN ip.base_distribuida ELSE NULL END base_derivada,
                          ip.impuesto_distribuido iva_derivado,
                          CASE WHEN p.original_neto>0 THEN p.neto/p.original_neto ELSE NULL END proporcion
                        FROM partes p JOIN universo u ON u.documento_id=p.documento_id LEFT JOIN conceptos cc ON cc.id=p.concepto_id LEFT JOIN iva_concepto ic ON ic.concepto_id=p.concepto_id LEFT JOIN iva_partes ip ON ip.concepto_id=p.concepto_id AND ip.categoria_id<=>p.categoria_id
                      )
                      SELECT p.naturaleza,p.categoria,p.sentido,p.tipo,p.moneda,p.estado_sat,SUM(p.neto),
                        SUM(CASE WHEN p.factor='Exento' THEN p.base_derivada END),
                        SUM(CASE WHEN p.factor='Tasa' AND p.tasa=0 THEN p.base_derivada END),
                        SUM(CASE WHEN p.factor='Tasa' AND p.tasa=0.16 THEN p.base_derivada END),
                        SUM(CASE WHEN p.factor='Tasa' AND p.tasa NOT IN (0,0.16) THEN p.base_derivada END),
                        SUM(CASE WHEN p.objeto_impuesto='01' THEN p.neto END),
                        SUM(CASE WHEN p.factor IS NULL AND COALESCE(p.objeto_impuesto,'')<>'01' THEN p.neto END),
                        SUM(p.iva_derivado)
                      FROM detalle p JOIN universo u ON u.documento_id=p.documento_id WHERE p.sentido='RECIBIDO'
                      """+FiscalSql.filtros(f,"u")+(!f.naturaleza().isEmpty()?" AND p.naturaleza=:naturaleza":"")+(f.categoria()>0?" AND p.categoria_id=:categoria":"")+" GROUP BY p.naturaleza,p.categoria,p.sentido,p.tipo,p.moneda,p.estado_sat ORDER BY p.naturaleza,p.categoria,p.tipo,p.moneda,p.estado_sat";
                    columnas=List.of("Naturaleza","Categoría","Sentido","Tipo","Moneda","SAT","Neto clasificado","Base exenta distribuida","Base 0% distribuida","Base 16% distribuida","Base otras tasas distribuida","No objeto","Sin desglose / revisar","IVA declarado distribuido");
                }else if(seccion==Seccion.ORIGENES){
                    sql=FiscalSql.BASE+FiscalSql.IMPUESTOS_DISTRIBUIDOS+" SELECT u.documento_id,u.uuid,p.concepto_id,cc.ordinal,cc.clave_prod_serv,cc.descripcion,p.naturaleza,p.categoria,p.origen,p.neto,p.original_neto,u.moneda,u.sentido,u.tipo,u.estado_sat,i.impuesto_id,i.impuesto,i.movimiento,i.tipo_factor,i.base_original,i.tasa_cuota,i.impuesto_original,i.base_distribuida,i.impuesto_distribuido FROM partes p JOIN universo u ON u.documento_id=p.documento_id LEFT JOIN conceptos cc ON cc.id=p.concepto_id LEFT JOIN impuestos_distribuidos i ON i.concepto_id=p.concepto_id AND i.categoria_id<=>p.categoria_id WHERE 1=1"+FiscalSql.filtros(f,"u")+(!f.naturaleza().isEmpty()?" AND p.naturaleza=:naturaleza":"")+(f.categoria()>0?" AND p.categoria_id=:categoria":"")+" ORDER BY u.documento_id,p.concepto_id,p.categoria_id,i.impuesto_id";
                    columnas=List.of("Documento","UUID","Concepto ID","Ordinal","Producto","Descripción","Naturaleza","Categoría","Origen clasificación","Neto asignado","Neto original concepto","Moneda","Sentido","Tipo","SAT","Impuesto ID","Código","Movimiento","Factor","Base original","Tasa/cuota","Impuesto original","Base distribuida derivada","Impuesto distribuido derivado");
                }else if(seccion==Seccion.IEPS||seccion==Seccion.RETENCIONES){
                    sql=FiscalSql.BASE+" SELECT u.sentido,u.tipo,u.moneda,u.estado_sat,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota,COUNT(DISTINCT u.documento_id),SUM(i.base),SUM(i.importe) FROM universo u JOIN conceptos cc ON cc.documento_id=u.documento_id JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id WHERE "+(seccion==Seccion.IEPS?"i.impuesto='003'":"i.movimiento='RETENCION'")+FiscalSql.filtros(f,"u")+" GROUP BY u.sentido,u.tipo,u.moneda,u.estado_sat,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota ORDER BY u.sentido,u.tipo,u.moneda,u.estado_sat,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota";
                    columnas=List.of("Sentido","Tipo","Moneda","SAT","Impuesto","Movimiento","Factor","Tasa/cuota","Documentos","Bases originales","Importes originales");
                }else{
                    sql=FiscalSql.BASE+"""
                        SELECT u.documento_id,u.uuid,u.sentido,u.tipo,u.moneda,u.estado_sat,u.naturaleza,
                          CONCAT_WS('; ',CASE WHEN u.estado_sat<>'VIGENTE' THEN CONCAT('SAT_',u.estado_sat) END,
                            CASE WHEN u.categoria_id IS NULL THEN 'CLASIFICACION_GENERAL_PENDIENTE' END,
                            CASE WHEN NOT EXISTS(SELECT 1 FROM cfdi_concepto cc WHERE cc.documento_id=u.documento_id) THEN 'SIN_CONCEPTOS' END,
                            CASE WHEN NOT EXISTS(SELECT 1 FROM obligacion o WHERE o.contribuyente_id=:t AND o.documento_id=u.documento_id) THEN 'SALDO_SIN_EVIDENCIA_APLICADA' END,
                            CASE WHEN u.tipo='E' THEN 'AJUSTE_SIN_RESTITUCION_FISCAL_CONFIRMADA' END,
                            CASE WHEN EXISTS(SELECT 1 FROM cfdi_relacion_grupo g WHERE g.documento_id=u.documento_id AND g.tipo_relacion='04') THEN 'SUSTITUCION_NO_IMPLICA_CANCELACION' END,
                            'REVISAR_CONCILIACION_Y_REQUISITOS_FISCALES') incidencias
                        FROM universo u WHERE u.tipo IN ('I','E','N')
                        """+FiscalSql.filtros(f,"u")+" ORDER BY u.documento_id";
                    columnas=List.of("Documento","UUID","Sentido","Tipo","Moneda","SAT","Naturaleza general","Incidencias / tareas de revisión");
                }
                return ImpuestosService.tabla(em,sql,f,pagina,columnas,"Papel de trabajo preliminar. Monedas, tipos y estados separados. Orígenes reconstruye distribuciones; originales disponibles en detalle. Sin determinación definitiva de ISR, IVA o IEPS.");
            });
        };
    }
}
