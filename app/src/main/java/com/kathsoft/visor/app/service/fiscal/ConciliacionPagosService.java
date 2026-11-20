package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import java.util.List;
import java.util.Objects;

/** Conciliación RF-022 de niveles REP; ninguna diferencia modifica el original ni genera realización adicional. */
public final class ConciliacionPagosService {
    private final JpaContext jpa;
    /** Contexto compartido de la sesión. */
    public ConciliacionPagosService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}

    /** Convierte DR a MonedaP para comparar firmas de impuestos, sin mezclar tasas ni factores. */
    public TablaFiscal documentosContraPago(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+"""
                ,pagos_periodo AS (
                  SELECT u.documento_id,u.uuid,u.estado_sat,p.id pago_id,p.fecha_pago,p.moneda
                  FROM activos u JOIN pago_fiscal p ON p.documento_id=u.documento_id WHERE p.fecha_pago>=:desde AND p.fecha_pago<:hasta
                """+FiscalSql.filtros(f,"u")+"""
                ),dr_convertidos AS (
                  SELECT p.pago_id,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota,
                    i.base/CASE WHEN pd.moneda=p.moneda THEN 1 ELSE NULLIF(pd.equivalencia_dr,0) END base_p,
                    i.importe/CASE WHEN pd.moneda=p.moneda THEN 1 ELSE NULLIF(pd.equivalencia_dr,0) END importe_p,
                    CASE WHEN pd.moneda<>p.moneda AND (pd.equivalencia_dr IS NULL OR pd.equivalencia_dr<=0) THEN 1 ELSE 0 END sin_factor
                  FROM pagos_periodo p JOIN pago_fiscal_documento pd ON pd.pago_fiscal_id=p.pago_id
                  JOIN pago_fiscal_impuesto_documento i ON i.pago_documento_id=pd.id
                ),dr AS (
                  SELECT pago_id,impuesto,movimiento,tipo_factor,tasa_cuota,SUM(base_p) base_p,SUM(importe_p) importe_p,SUM(sin_factor) sin_factor
                  FROM dr_convertidos GROUP BY pago_id,impuesto,movimiento,tipo_factor,tasa_cuota
                ),ip AS (
                  SELECT p.pago_id,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota,SUM(i.base) base_p,SUM(i.importe) importe_p
                  FROM pagos_periodo p JOIN pago_fiscal_impuesto_pago i ON i.pago_fiscal_id=p.pago_id GROUP BY p.pago_id,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota
                ),firmas AS (
                  SELECT pago_id,impuesto,movimiento,tipo_factor,tasa_cuota FROM dr
                  UNION SELECT pago_id,impuesto,movimiento,tipo_factor,tasa_cuota FROM ip
                )
                SELECT p.documento_id,p.uuid,p.pago_id,p.fecha_pago,p.moneda,p.estado_sat,f.impuesto,f.movimiento,f.tipo_factor,f.tasa_cuota,
                  CASE WHEN dr.sin_factor=0 THEN dr.base_p END base_dr_convertida,ip.base_p,
                  CASE WHEN dr.sin_factor=0 THEN dr.base_p-ip.base_p END diferencia_base,
                  CASE WHEN dr.sin_factor=0 THEN dr.importe_p END impuesto_dr_convertido,ip.importe_p,
                  CASE WHEN dr.sin_factor=0 THEN dr.importe_p-ip.importe_p END diferencia_impuesto,dr.sin_factor
                FROM firmas f JOIN pagos_periodo p ON p.pago_id=f.pago_id
                LEFT JOIN dr ON dr.pago_id=f.pago_id AND dr.impuesto=f.impuesto AND dr.movimiento=f.movimiento AND dr.tipo_factor<=>f.tipo_factor AND dr.tasa_cuota<=>f.tasa_cuota
                LEFT JOIN ip ON ip.pago_id=f.pago_id AND ip.impuesto=f.impuesto AND ip.movimiento=f.movimiento AND ip.tipo_factor<=>f.tipo_factor AND ip.tasa_cuota<=>f.tasa_cuota
                ORDER BY p.fecha_pago,p.pago_id,f.impuesto,f.movimiento,f.tipo_factor,f.tasa_cuota
                """;
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Documento REP","UUID REP","Pago ID","FechaPago","MonedaP","SAT REP","Impuesto","Movimiento","Factor","Tasa/cuota","Base DR convertida derivada","BaseP original","Diferencia base","Impuesto DR convertido derivado","ImpuestoP original","Diferencia impuesto","Líneas sin factor"),"Conciliación DR/P. Todas las cifras comparables están en MonedaP. Null conserva ausencia de nivel o factor; revisar diferencias, no corregir XML.");
        });
    }

    /** Compara nodos Pago completos con Totales MXN, incluso cuando un REP contiene meses distintos. */
    public TablaFiscal totales(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+"""
                ,rep_periodo AS (
                  SELECT u.* FROM activos u WHERE EXISTS(SELECT 1 FROM pago_fiscal p WHERE p.documento_id=u.documento_id AND p.fecha_pago>=:desde AND p.fecha_pago<:hasta)
                """+FiscalSql.filtros(f,"u")+"""
                ),nodos AS (
                  SELECT r.documento_id,COUNT(*) pagos,
                    SUM(CASE WHEN p.moneda='MXN' THEN p.monto WHEN p.tipo_cambio>0 THEN p.monto*p.tipo_cambio END) monto_mxn,
                    SUM(CASE WHEN p.moneda<>'MXN' AND (p.tipo_cambio IS NULL OR p.tipo_cambio<=0) THEN 1 ELSE 0 END) sin_tipo_cambio
                  FROM rep_periodo r JOIN pago_fiscal p ON p.documento_id=r.documento_id GROUP BY r.documento_id
                ),impuestos_p AS (
                  SELECT r.documento_id,
                    CASE
                      WHEN i.movimiento='RETENCION' THEN CASE i.impuesto WHEN '001' THEN 'TotalRetencionesISR' WHEN '002' THEN 'TotalRetencionesIVA' WHEN '003' THEN 'TotalRetencionesIEPS' END
                      WHEN i.impuesto='002' AND i.tipo_factor='Exento' THEN 'TotalTrasladosBaseIVAExento'
                      WHEN i.impuesto='002' AND i.tipo_factor='Tasa' THEN CASE i.tasa_cuota WHEN 0 THEN 'TotalTrasladosImpuestoIVA0' WHEN 0.08 THEN 'TotalTrasladosImpuestoIVA8' WHEN 0.16 THEN 'TotalTrasladosImpuestoIVA16' END
                    END atributo,
                    CASE WHEN i.tipo_factor='Exento' THEN i.base ELSE i.importe END * CASE WHEN p.moneda='MXN' THEN 1 WHEN p.tipo_cambio>0 THEN p.tipo_cambio END importe_mxn,
                    CASE WHEN p.moneda<>'MXN' AND (p.tipo_cambio IS NULL OR p.tipo_cambio<=0) THEN 1 ELSE 0 END sin_factor
                  FROM rep_periodo r JOIN pago_fiscal p ON p.documento_id=r.documento_id JOIN pago_fiscal_impuesto_pago i ON i.pago_fiscal_id=p.id
                  UNION ALL
                  SELECT r.documento_id,CASE i.tasa_cuota WHEN 0 THEN 'TotalTrasladosBaseIVA0' WHEN 0.08 THEN 'TotalTrasladosBaseIVA8' WHEN 0.16 THEN 'TotalTrasladosBaseIVA16' END,
                    i.base*CASE WHEN p.moneda='MXN' THEN 1 WHEN p.tipo_cambio>0 THEN p.tipo_cambio END,
                    CASE WHEN p.moneda<>'MXN' AND (p.tipo_cambio IS NULL OR p.tipo_cambio<=0) THEN 1 ELSE 0 END
                  FROM rep_periodo r JOIN pago_fiscal p ON p.documento_id=r.documento_id JOIN pago_fiscal_impuesto_pago i ON i.pago_fiscal_id=p.id
                  WHERE i.movimiento='TRASLADO' AND i.impuesto='002' AND i.tipo_factor='Tasa'
                ),sumas AS (
                  SELECT documento_id,atributo,SUM(importe_mxn) importe_mxn,SUM(sin_factor) sin_factor FROM impuestos_p WHERE atributo IS NOT NULL GROUP BY documento_id,atributo
                ),atributos AS (
                  SELECT s.documento_id,s.atributo FROM sumas s UNION SELECT r.documento_id,t.atributo FROM rep_periodo r JOIN pago_fiscal_total_impuesto t ON t.documento_id=r.documento_id
                ),resultado AS (
                  SELECT r.documento_id,r.uuid,r.estado_sat,'MontoTotalPagos' atributo,
                    CASE WHEN n.sin_tipo_cambio=0 THEN n.monto_mxn END derivado,t.monto_total_pagos original,n.sin_tipo_cambio sin_factor
                  FROM rep_periodo r JOIN nodos n ON n.documento_id=r.documento_id LEFT JOIN pago_fiscal_totales t ON t.documento_id=r.documento_id
                  UNION ALL
                  SELECT r.documento_id,r.uuid,r.estado_sat,a.atributo,CASE WHEN s.sin_factor=0 THEN s.importe_mxn END,t.importe_mxn,s.sin_factor
                  FROM atributos a JOIN rep_periodo r ON r.documento_id=a.documento_id LEFT JOIN sumas s ON s.documento_id=a.documento_id AND s.atributo=a.atributo
                  LEFT JOIN pago_fiscal_total_impuesto t ON t.documento_id=a.documento_id AND t.atributo=a.atributo
                )
                SELECT documento_id,uuid,estado_sat,atributo,derivado,original,derivado-original diferencia,sin_factor FROM resultado ORDER BY documento_id,atributo
                """;
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Documento REP","UUID REP","SAT REP","Atributo Totales","Derivado nodos Pago MXN","Original Totales MXN","Diferencia MXN","Líneas sin factor"),"Conciliación del REP completo, no reconocimiento del mes. Usa todos sus nodos Pago para comparar Totales. Conversiones derivadas; las tasas no representadas por Totales permanecen en DR/P originales.");
        });
    }
}
