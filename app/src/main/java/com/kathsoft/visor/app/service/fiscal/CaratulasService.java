package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import java.util.List;
import java.util.Objects;

/** Carátulas documentales y financieras en filas separadas por fuente temporal y moneda. */
public final class CaratulasService {
    private final JpaContext jpa;
    /** Reutiliza el contexto de la sesión y los auxiliares existentes. */
    public CaratulasService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}

    /**
     * Egresos por naturaleza efectiva. La distribución documental y la proporción administrativa
     * de pagos se presentan en filas separadas; el IVA efectivamente evidenciado se consulta en RF-023.
     */
    public TablaFiscal egresos(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String base=FiscalSql.BASE.replace("SELECT * FROM activos u WHERE ((:documento=0 AND u.fecha_emision>=:desde AND u.fecha_emision<:hasta) OR u.documento_id=:documento)","SELECT * FROM activos u WHERE (:documento=0 OR u.documento_id=:documento)");
            String sql=base+FiscalSql.IMPUESTOS_DISTRIBUIDOS+TercerosFiscalService.SALDOS+"""
                ,iva_partes AS (
                  SELECT concepto_id,categoria_id,SUM(base_distribuida) base_distribuida,SUM(impuesto_distribuido) impuesto_distribuido
                  FROM impuestos_distribuidos WHERE impuesto='002' AND movimiento='TRASLADO' GROUP BY concepto_id,categoria_id
                ),detalle AS (
                  SELECT p.*,u.sentido,u.tipo,u.moneda,u.estado_sat,cc.objeto_impuesto,
                    CASE WHEN ic.lineas=1 AND p.original_neto>0 AND COALESCE(cc.objeto_impuesto,'')<>'01' THEN ic.factor END factor,ic.tasa,
                    CASE WHEN ic.lineas=1 AND p.original_neto>0 THEN ip.base_distribuida END base_derivada,ip.impuesto_distribuido iva_derivado
                  FROM partes p JOIN universo u ON u.documento_id=p.documento_id LEFT JOIN conceptos cc ON cc.id=p.concepto_id
                  LEFT JOIN iva_concepto ic ON ic.concepto_id=p.concepto_id LEFT JOIN iva_partes ip ON ip.concepto_id=p.concepto_id AND ip.categoria_id<=>p.categoria_id
                ),caratula AS (
                  SELECT p.naturaleza,p.categoria,p.sentido,p.tipo,p.moneda,p.estado_sat,SUM(p.neto) neto,
                    SUM(CASE WHEN p.factor='Exento' THEN p.base_derivada END) exento,
                    SUM(CASE WHEN p.factor='Tasa' AND p.tasa=0 THEN p.base_derivada END) tasa0,
                    SUM(CASE WHEN p.factor='Tasa' AND p.tasa=0.16 THEN p.base_derivada END) tasa16,
                    SUM(CASE WHEN p.factor='Tasa' AND p.tasa NOT IN (0,0.16) THEN p.base_derivada END) otras,
                    SUM(CASE WHEN p.objeto_impuesto='01' THEN p.neto END) no_objeto,
                    SUM(CASE WHEN p.factor IS NULL AND COALESCE(p.objeto_impuesto,'')<>'01' THEN p.neto END) revisar,
                    SUM(p.iva_derivado) iva,p.categoria_id,NULL neto_pagado,NULL iva_pago,NULL ajuste,'DOCUMENTAL_EMISION' origen
                  FROM detalle p JOIN universo u ON u.documento_id=p.documento_id
                  WHERE p.sentido='RECIBIDO' AND p.tipo IN ('I','E','N') AND ((:documento=0 AND u.fecha_emision>=:desde AND u.fecha_emision<:hasta) OR u.documento_id=:documento)
                """+FiscalSql.filtros(f,"u")+filtrosParte(f)+"""
                  GROUP BY p.naturaleza,p.categoria,p.categoria_id,p.sentido,p.tipo,p.moneda,p.estado_sat
                  UNION ALL
                  SELECT p.naturaleza,p.categoria,p.sentido,p.tipo,p.moneda,p.estado_sat,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,p.categoria_id,
                    SUM(CASE WHEN u.total>0 THEN p.neto*o.pago_periodo/u.total END),
                    SUM(CASE WHEN u.total>0 THEN p.iva_derivado*o.pago_periodo/u.total END),
                    SUM(CASE WHEN u.total>0 THEN p.neto*o.ajuste_periodo/u.total END),'FINANCIERO_MOVIMIENTO_DERIVADO'
                  FROM detalle p JOIN universo u ON u.documento_id=p.documento_id JOIN obligaciones o ON o.documento_id=u.documento_id AND o.naturaleza='PAGAR'
                  WHERE p.sentido='RECIBIDO' AND p.tipo='I' AND (o.pago_periodo<>0 OR o.ajuste_periodo<>0)
                """+FiscalSql.filtros(f,"u")+filtrosParte(f)+"""
                  GROUP BY p.naturaleza,p.categoria,p.categoria_id,p.sentido,p.tipo,p.moneda,p.estado_sat
                )
                SELECT * FROM caratula ORDER BY origen,naturaleza,categoria,tipo,moneda,estado_sat
                """;
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Naturaleza","Categoría","Sentido","Tipo","Moneda","SAT","Neto clasificado","Base exenta distribuida","Base 0% distribuida","Base 16% distribuida","Base otras tasas distribuida","No objeto","Sin desglose / revisar","IVA declarado distribuido","Categoría ID","Neto aplicado a pagos DERIVADO","IVA proporcional a pagos DERIVADO","Neto aplicado a ajustes DERIVADO","Origen del periodo"),
                "Documental por emisión y proporciones administrativas por fecha de movimiento separadas. Incluye pagos de facturas anteriores. IVA proporcional no es IVA acreditable confirmado; consulte REP, manuales y tratamientos.");
        });
    }
    private static String filtrosParte(FiltroFiscal f){return (!f.naturaleza().isEmpty()?" AND p.naturaleza=:naturaleza":"")+(f.categoria()>0?" AND p.categoria_id=:categoria":"");}

    /**
     * Ingresos emitidos: emisión para originales, movimiento para cobros y ajustes de saldo.
     * No compensa notas de crédito automáticamente ni suma ambas dimensiones como un mismo ingreso.
     * Las filas financieras incluyen obligaciones de facturas de meses anteriores.
     */
    public TablaFiscal ingresos(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+TercerosFiscalService.SALDOS+"""
                ,bases AS (
                  SELECT cc.documento_id,
                    SUM(CASE WHEN i.lineas=1 AND COALESCE(cc.objeto_impuesto,'')<>'01' AND i.factor='Tasa' AND i.tasa=0.16 THEN i.base END) base16,
                    SUM(CASE WHEN i.lineas=1 AND COALESCE(cc.objeto_impuesto,'')<>'01' AND i.factor='Tasa' AND i.tasa=0 THEN i.base END) base0,
                    SUM(CASE WHEN i.lineas=1 AND COALESCE(cc.objeto_impuesto,'')<>'01' AND i.factor='Exento' THEN i.base END) exento,
                    SUM(CASE WHEN i.lineas=1 AND COALESCE(cc.objeto_impuesto,'')<>'01' AND i.factor='Tasa' AND i.tasa NOT IN (0,0.16) THEN i.base END) otras,
                    SUM(CASE WHEN cc.objeto_impuesto='01' THEN cc.neto END) no_objeto,
                    SUM(CASE WHEN COALESCE(cc.objeto_impuesto,'')<>'01' AND (i.lineas IS NULL OR i.lineas<>1) THEN cc.neto END) revisar
                  FROM conceptos cc LEFT JOIN iva_concepto i ON i.concepto_id=cc.id GROUP BY cc.documento_id
                ),tributos AS (
                  SELECT cc.documento_id,
                    SUM(CASE WHEN i.impuesto='002' AND i.movimiento='TRASLADO' THEN i.importe END) iva,
                    SUM(CASE WHEN i.impuesto='003' AND i.movimiento='TRASLADO' THEN i.importe END) ieps,
                    SUM(CASE WHEN i.impuesto='001' AND i.movimiento='RETENCION' THEN i.importe END) isr_ret,
                    SUM(CASE WHEN i.impuesto='002' AND i.movimiento='RETENCION' THEN i.importe END) iva_ret
                  FROM conceptos cc JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id GROUP BY cc.documento_id
                ),caratula AS (
                  SELECT u.sentido,u.tipo,u.moneda,u.estado_sat,COUNT(*) documentos,SUM(u.subtotal) subtotal,SUM(u.descuento) descuento,
                    SUM(u.subtotal-u.descuento) neto,SUM(u.total) total,SUM(b.base16) base16,SUM(b.base0) base0,SUM(b.exento) exento,
                    SUM(b.otras) otras,SUM(b.no_objeto) no_objeto,SUM(b.revisar) revisar,
                    SUM(t.iva) iva,SUM(t.ieps) ieps,SUM(t.isr_ret) isr_ret,SUM(t.iva_ret) iva_ret,
                    NULL cobros,NULL saldo,NULL ajustes,'DOCUMENTAL_EMISION' origen
                  FROM universo u LEFT JOIN bases b ON b.documento_id=u.documento_id LEFT JOIN tributos t ON t.documento_id=u.documento_id
                  WHERE u.sentido='EMITIDO' AND u.tipo IN ('I','E')
                """+FiscalSql.filtros(f,"u")+"""
                  GROUP BY u.sentido,u.tipo,u.moneda,u.estado_sat
                  UNION ALL
                  SELECT u.sentido,u.tipo,o.moneda,u.estado_sat,COUNT(DISTINCT u.documento_id),NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,
                    SUM(o.pago_periodo),SUM(o.saldo_cierre),SUM(o.ajuste_periodo),'FINANCIERO_MOVIMIENTO'
                  FROM activos u JOIN obligaciones o ON o.documento_id=u.documento_id
                  WHERE u.sentido='EMITIDO' AND u.tipo='I' AND (o.pago_periodo<>0 OR o.ajuste_periodo<>0 OR o.saldo_cierre<>0)
                """+FiscalSql.filtros(f,"u")+"""
                  GROUP BY u.sentido,u.tipo,o.moneda,u.estado_sat
                )
                SELECT * FROM caratula ORDER BY origen,sentido,tipo,moneda,estado_sat
                """;
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Sentido","Tipo","Moneda","SAT","Documentos","Subtotal documental","Descuentos documentales","Neto documental","Total documental","Base 16%","Base 0%","Base exenta","Bases otras tasas","No objeto","Sin desglose / revisar","IVA trasladado documental","IEPS trasladado documental","ISR retenido documental","IVA retenido documental","Cobros documentados del periodo","Saldo pendiente documentado","Ajustes de saldo del periodo","Origen del periodo"),
                "Emisión y realización financiera son dimensiones separadas. Cobros y saldos usan aplicaciones documentadas; un saldo no cubre historia ausente. IVA cobrado se analiza en Carátula IVA. E conserva notas y devoluciones sin presumir restitución.");
        });
    }
}
