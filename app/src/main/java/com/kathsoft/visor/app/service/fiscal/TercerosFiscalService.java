package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import java.util.List;
import java.util.Objects;

/** RF-024: consolidación documental y saldos al corte, reutilizando obligaciones y aplicaciones. */
public final class TercerosFiscalService {
    private final JpaContext jpa;
    /** Inyecta contexto compartido. */
    public TercerosFiscalService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}
    static final String SALDOS="""
        ,aplicaciones AS (
          SELECT a.*,m.fecha_movimiento,
            CASE WHEN NOT EXISTS (
              SELECT 1 FROM movimiento_pago_complemento mc
              JOIN pago_fiscal_documento pd ON pd.id=mc.pago_fiscal_documento_id JOIN pago_fiscal pf ON pf.id=pd.pago_fiscal_id
              LEFT JOIN contribuyente_documento cd ON cd.documento_id=pf.documento_id AND cd.contribuyente_id=:t
              WHERE mc.movimiento_pago_id=m.id AND (cd.documento_id IS NULL OR cd.archivado_en IS NOT NULL OR
                COALESCE((SELECT s.estado FROM consulta_estado_sat s WHERE s.documento_id=pf.documento_id ORDER BY s.fecha_consulta DESC,s.id DESC LIMIT 1),'SIN_CONSULTA')<>'VIGENTE')
            ) THEN 1 ELSE 0 END evidencia_disponible
          FROM aplicacion_pago a JOIN movimiento_pago m ON m.id=a.movimiento_pago_id
          JOIN obligacion o ON o.id=a.obligacion_id AND o.contribuyente_id=m.contribuyente_id
          WHERE o.contribuyente_id=:t AND m.fecha_movimiento<:hasta
        ),pagos AS (
          SELECT a.obligacion_id,SUM(CASE WHEN a.evidencia_disponible=1 AND a.fecha_movimiento<:desde THEN a.importe_moneda_obligacion ELSE 0 END) anterior,
            SUM(CASE WHEN a.evidencia_disponible=1 AND a.fecha_movimiento>=:desde THEN a.importe_moneda_obligacion ELSE 0 END) periodo,
            SUM(CASE WHEN a.evidencia_disponible=1 THEN a.importe_moneda_obligacion ELSE 0 END) cierre,
            SUM(CASE WHEN a.evidencia_disponible=0 THEN a.importe_moneda_obligacion ELSE 0 END) sin_confirmar
          FROM aplicaciones a GROUP BY a.obligacion_id
        ),ajustes AS (
          SELECT a.obligacion_id,SUM(CASE WHEN a.fecha_ajuste<DATE(:desde) THEN a.importe_moneda_obligacion ELSE 0 END) anterior,
            SUM(CASE WHEN a.fecha_ajuste>=DATE(:desde) THEN a.importe_moneda_obligacion ELSE 0 END) periodo,
            SUM(a.importe_moneda_obligacion) cierre
          FROM ajuste_obligacion a JOIN obligacion o ON o.id=a.obligacion_id WHERE o.contribuyente_id=:t AND a.fecha_ajuste<DATE(:hasta) GROUP BY a.obligacion_id
        ),obligaciones AS (
          SELECT u.documento_id,o.id obligacion_id,o.naturaleza,o.moneda,o.monto_original,o.fecha_origen,
            CASE WHEN o.fecha_origen<DATE(:desde) THEN o.monto_original-COALESCE(p.anterior,0)-COALESCE(a.anterior,0) ELSE NULL END saldo_inicial,
            COALESCE(p.periodo,0) pago_periodo,COALESCE(a.periodo,0) ajuste_periodo,
            o.monto_original-COALESCE(a.cierre,0) ajustado,
            o.monto_original-COALESCE(p.cierre,0)-COALESCE(a.cierre,0) saldo_cierre,COALESCE(p.sin_confirmar,0) pagos_sin_confirmar
          FROM activos u JOIN obligacion o ON o.documento_id=u.documento_id AND o.contribuyente_id=:t
          LEFT JOIN pagos p ON p.obligacion_id=o.id LEFT JOIN ajustes a ON a.obligacion_id=o.id WHERE o.fecha_origen<DATE(:hasta)
        )
        """;
    /** Agrupa documentos del periodo por tercero, moneda, sentido, tipo y estado, sin netear notas de crédito. */
    public TablaFiscal consultar(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+"""
                ,impuestos AS (
                  SELECT cc.documento_id,
                    SUM(CASE WHEN i.impuesto='002' AND i.movimiento='TRASLADO' THEN i.importe ELSE 0 END) iva,
                    SUM(CASE WHEN i.impuesto='002' AND i.movimiento='RETENCION' THEN i.importe ELSE 0 END) iva_ret,
                    SUM(CASE WHEN i.impuesto='001' AND i.movimiento='RETENCION' THEN i.importe ELSE 0 END) isr_ret,
                    SUM(CASE WHEN i.impuesto='003' THEN i.importe ELSE 0 END) ieps
                  FROM conceptos cc JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id GROUP BY cc.documento_id
                )
                SELECT u.tercero_id,u.rfc_tercero,u.nombre_tercero,u.sentido,u.tipo,u.moneda,u.estado_sat,
                  (SELECT GROUP_CONCAT(DISTINCT r.rol ORDER BY r.rol SEPARATOR ', ') FROM contribuyente_tercero_rol r WHERE r.contribuyente_id=:t AND r.tercero_id=u.tercero_id) relacion,
                  COUNT(*) documentos,SUM(u.subtotal) subtotal,SUM(u.descuento) descuentos,SUM(u.total) total,
                  SUM(i.iva) iva,SUM(i.iva_ret) iva_retenido,SUM(i.isr_ret) isr_retenido,SUM(i.ieps) ieps
                FROM universo u LEFT JOIN impuestos i ON i.documento_id=u.documento_id WHERE u.tipo IN ('I','E','N')
                """+FiscalSql.filtros(f,"u")+" GROUP BY u.tercero_id,u.rfc_tercero,u.nombre_tercero,u.sentido,u.tipo,u.moneda,u.estado_sat ORDER BY u.rfc_tercero,u.moneda,u.sentido,u.tipo,u.estado_sat";
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Tercero ID","RFC","Nombre","Sentido","Tipo","Moneda","SAT","Relación comercial","Documentos","Subtotal original","Descuentos","Total documental","IVA conceptos","IVA retenido","ISR retenido","IEPS conceptos"),"Notas de crédito y cancelados separados. Impuestos de conceptos no prueban realización. Saldos históricos en pestaña Financiero.");
        });
    }
    /** Saldo inicial sólo de obligaciones documentadas anteriores; no presume integridad de toda la historia. */
    public TablaFiscal saldos(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+SALDOS+"""
                ,rep_sugerencias AS (
                  SELECT fac.documento_id,
                    COUNT(*) rep_detectados,
                    SUM(CASE WHEN rep.estado_sat='VIGENTE' AND fac.estado_sat='VIGENTE'
                        AND pd.moneda=fac.moneda AND pd.importe_pagado>0
                        AND (pd.moneda=p.moneda OR pd.equivalencia_dr>0)
                      THEN 1 ELSE 0 END) rep_con_estado_vigente,
                    SUM(CASE WHEN rep.estado_sat='VIGENTE' AND fac.estado_sat='VIGENTE'
                        AND pd.moneda=fac.moneda AND pd.importe_pagado>0
                        AND (pd.moneda=p.moneda OR pd.equivalencia_dr>0)
                      THEN pd.importe_pagado ELSE 0 END) importe_rep_sugerido
                  FROM activos fac JOIN pago_fiscal_documento pd ON pd.uuid_documento=fac.uuid
                  JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id
                  JOIN activos rep ON rep.documento_id=p.documento_id AND rep.tipo='P'
                  WHERE fac.tipo='I' AND NOT EXISTS (
                    SELECT 1 FROM movimiento_pago_complemento mc
                    JOIN movimiento_pago m ON m.id=mc.movimiento_pago_id
                    WHERE mc.pago_fiscal_documento_id=pd.id AND m.contribuyente_id=:t
                  )
                  GROUP BY fac.documento_id
                )
                SELECT u.documento_id,u.uuid,u.tercero_id,u.rfc_tercero,u.nombre_tercero,u.sentido,u.tipo,u.moneda,u.estado_sat,
                    o.obligacion_id,o.naturaleza,u.total,o.monto_original,o.saldo_inicial,o.pago_periodo,o.ajuste_periodo,o.ajustado,o.saldo_cierre,
                    CASE WHEN o.obligacion_id IS NULL THEN 'SALDO_PENDIENTE_CORROBORAR' ELSE 'SALDO_DOCUMENTADO_HISTORIA_PARCIAL' END estado,
                    u.naturaleza,u.categoria,o.moneda moneda_obligacion,o.pagos_sin_confirmar,
                    COALESCE(rs.rep_detectados,0) rep_detectados,
                    COALESCE(rs.rep_con_estado_vigente,0) rep_aplicables,
                    CASE WHEN rs.rep_con_estado_vigente>0 THEN rs.importe_rep_sugerido END rep_pendiente_obligacion
                FROM activos u LEFT JOIN obligaciones o ON o.documento_id=u.documento_id
                LEFT JOIN rep_sugerencias rs ON rs.documento_id=u.documento_id
                WHERE u.fecha_emision<:hasta AND u.tipo='I'
                """+FiscalSql.filtros(f,"u")+" ORDER BY u.rfc_tercero,u.fecha_emision,u.documento_id,o.obligacion_id";
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Documento","UUID","Tercero ID","RFC","Tercero","Sentido","Tipo","Moneda CFDI","SAT","Obligación ID","Naturaleza financiera","Total documental","Obligación original","Saldo inicial documentado","Pagos/cobros del periodo","Ajustes del periodo","Importe ajustado","Saldo al corte documentado","Estado del saldo","Naturaleza general","Categoría general","Moneda obligación","Pagos REP excluidos por estado / archivado","REP sin aplicar","REP elegibles por SAT","Importe REP sugerido (moneda factura)"),"Los REP se detectan mediante UUID y se muestran aunque no exista obligación; NO reducen saldos sin confirmación. Elegibles: último SAT guardado VIGENTE en factura y REP, moneda y equivalencia coherentes. Pulse Aplicar REP para conciliar sin capturar UUID. Estados desconocidos y ajustes requieren revisión.");
        });
    }
}
