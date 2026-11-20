package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** RF-022: consulta original de todos los niveles sin sumar niveles equivalentes. */
public final class ImpuestosService {
    private final JpaContext jpa;
    /** Usa exclusivamente el contexto de la sesión. */
    public ImpuestosService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}
    /** Niveles originales independientes; DR usa FechaPago, no la emisión de la factura. */
    public enum Nivel { CONCEPTO, GLOBAL, PAGO_DOCUMENTO, PAGO, TOTALES }

    /** Consulta paginada y trazable de bases, factores, tasas, cuotas e importes declarados. */
    public TablaFiscal consultar(Long actor,FiltroFiscal f,Nivel nivel,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql;
            if(nivel==Nivel.CONCEPTO||nivel==Nivel.GLOBAL){
                String origen=nivel==Nivel.CONCEPTO?"JOIN cfdi_concepto cc ON cc.documento_id=u.documento_id JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id":"JOIN cfdi_impuesto_global i ON i.documento_id=u.documento_id";
                String concepto=nivel==Nivel.CONCEPTO?"cc.id":"NULL";
                String tratamientos=nivel==Nivel.CONCEPTO?"(SELECT GROUP_CONCAT(CONCAT(ti.clave,': ',cl.importe_asignado) ORDER BY cl.id SEPARATOR '; ') FROM clasificacion_impuesto_concepto cl JOIN tratamiento_impuesto ti ON ti.id=cl.tratamiento_id WHERE cl.contribuyente_id=:t AND cl.impuesto_concepto_id=i.id)":"NULL";
                sql=FiscalSql.BASE+" SELECT u.documento_id,u.uuid,"+concepto+",i.id,u.fecha_emision,u.sentido,u.tipo,u.rfc_tercero,u.nombre_tercero,u.moneda,u.estado_sat,u.naturaleza,u.categoria,i.impuesto,CASE i.impuesto WHEN '001' THEN 'ISR' WHEN '002' THEN 'IVA' WHEN '003' THEN 'IEPS' ELSE 'REVISAR_CATALOGO' END,i.movimiento,i.tipo_factor,i.base,i.tasa_cuota,i.importe,"+tratamientos+" tratamientos FROM universo u "+origen+" WHERE 1=1"+FiscalSql.filtros(f,"u")+" ORDER BY u.fecha_emision,u.documento_id,i.id";
            }else if(nivel==Nivel.TOTALES){
                sql=FiscalSql.BASE+" SELECT u.documento_id,u.uuid,u.moneda,u.estado_sat,t.total_trasladados,t.total_retenidos,p.monto_total_pagos FROM universo u LEFT JOIN cfdi_impuesto_totales t ON t.documento_id=u.documento_id LEFT JOIN pago_fiscal_totales p ON p.documento_id=u.documento_id WHERE (t.documento_id IS NOT NULL OR p.documento_id IS NOT NULL)"+FiscalSql.filtros(f,"u")+" ORDER BY u.documento_id";
            }else{
                boolean dr=nivel==Nivel.PAGO_DOCUMENTO;
                sql=FiscalSql.BASE+" SELECT u.documento_id,u.uuid,p.id,p.fecha_pago,p.moneda moneda_pago,p.tipo_cambio,"+(dr?"pd.id,pd.uuid_documento,pd.moneda moneda_dr,pd.equivalencia_dr,":"")+"i.id,i.impuesto,i.movimiento,i.tipo_factor,i.base,i.tasa_cuota,i.importe,u.estado_sat FROM activos u JOIN pago_fiscal p ON p.documento_id=u.documento_id "+(dr?"JOIN pago_fiscal_documento pd ON pd.pago_fiscal_id=p.id JOIN pago_fiscal_impuesto_documento i ON i.pago_documento_id=pd.id":"JOIN pago_fiscal_impuesto_pago i ON i.pago_fiscal_id=p.id")+" WHERE p.fecha_pago>=:desde AND p.fecha_pago<:hasta"+FiscalSql.filtros(new FiltroFiscal(f.titular(),f.desde(),f.hasta(),f.sentido(),f.rfc(),f.naturaleza(),f.categoria(),"",f.tipo(),f.estadoSat()),"u")+(!f.moneda().isEmpty()?(dr?" AND pd.moneda=:moneda":" AND p.moneda=:moneda"):"")+" ORDER BY p.fecha_pago,p.id,i.id";
            }
            List<String> columnas=switch(nivel){
                case CONCEPTO,GLOBAL->List.of("Documento","UUID","Concepto","Impuesto ID","Emisión","Sentido","Tipo","RFC tercero","Tercero","Moneda","SAT","Naturaleza general","Categoría general","Código","Impuesto","Movimiento","Factor","Base original","Tasa/cuota","Importe original","Tratamientos asignados");
                case TOTALES->List.of("Documento","UUID","Moneda CFDI","SAT","Traslados originales","Retenciones originales","Total pagos MXN (REP 2.0)");
                case PAGO_DOCUMENTO->List.of("Documento REP","UUID REP","Pago ID","FechaPago","MonedaP","TipoCambioP","DR ID","UUID relacionado","MonedaDR","EquivalenciaDR","Impuesto ID","Código","Movimiento","Factor","BaseDR","Tasa/cuota","ImporteDR","SAT REP");
                case PAGO->List.of("Documento REP","UUID REP","Pago ID","FechaPago","MonedaP","TipoCambioP","Impuesto ID","Código","Movimiento","Factor","BaseP","Tasa/cuota","ImporteP","SAT REP");
            };
            return tabla(em,sql,f,pagina,columnas,"Valores originales independientes por nivel. Sin traslado IVA no significa exento. Tratamiento asignado no acredita requisitos legales.");
        });
    }

    /** Diferencias por documento y movimiento; null conserva un nivel ausente. */
    public TablaFiscal conciliar(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+"""
                ,ci AS (SELECT cc.documento_id,i.movimiento,SUM(i.importe) importe FROM conceptos cc JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id GROUP BY cc.documento_id,i.movimiento),
                gi AS (SELECT i.documento_id,i.movimiento,SUM(i.importe) importe FROM universo u JOIN cfdi_impuesto_global i ON i.documento_id=u.documento_id GROUP BY i.documento_id,i.movimiento),
                movimientos AS (SELECT 'TRASLADO' movimiento UNION ALL SELECT 'RETENCION')
                SELECT u.documento_id,u.uuid,u.moneda,u.estado_sat,m.movimiento,ci.importe conceptos,gi.importe globales,
                  CASE WHEN m.movimiento='TRASLADO' THEN t.total_trasladados ELSE t.total_retenidos END total_xml,
                  ci.importe-gi.importe diferencia_concepto_global,
                  gi.importe-CASE WHEN m.movimiento='TRASLADO' THEN t.total_trasladados ELSE t.total_retenidos END diferencia_global_total
                FROM universo u CROSS JOIN movimientos m LEFT JOIN ci ON ci.documento_id=u.documento_id AND ci.movimiento=m.movimiento
                LEFT JOIN gi ON gi.documento_id=u.documento_id AND gi.movimiento=m.movimiento LEFT JOIN cfdi_impuesto_totales t ON t.documento_id=u.documento_id
                WHERE u.tipo IN ('I','E','N')
                """+FiscalSql.filtros(f,"u")+" ORDER BY u.documento_id,m.movimiento";
            return tabla(em,sql,f,pagina,List.of("Documento","UUID","Moneda","SAT","Movimiento","Conceptos","Globales","Total XML","Diferencia concepto/global","Diferencia global/total"),"Ausencias y diferencias requieren revisión; no se corrige el XML ni se suman los niveles.");
        });
    }
    /** Ejecuta una proyección de 200 filas y una adicional para continuidad. */
    @SuppressWarnings("unchecked") static TablaFiscal tabla(EntityManager em,String sql,FiltroFiscal f,int pagina,List<String> columnas,String aviso){
        if(pagina<0||pagina>Integer.MAX_VALUE/200)throw new IllegalArgumentException("Página inválida");
        List<Object[]> r=FiscalSql.query(em,sql,f).setFirstResult(pagina*200).setMaxResults(201).getResultList();
        return new TablaFiscal(columnas,r.subList(0,Math.min(200,r.size())).stream().map(Arrays::asList).toList(),r.size()>200,aviso);
    }
}
