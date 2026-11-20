package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** RF-023: evidencia mensual REP con impuestosDR originales y resultados derivados separados. */
public final class AuxiliarIvaService {
    private final JpaContext jpa;
    /** Contexto compartido; no consulta en el constructor. */
    public AuxiliarIvaService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}

    /**
     * Consulta por FechaPago, incluidas facturas de periodos anteriores. SQL calcula controles históricos
     * antes de paginar. Una fila por impuestoDR; los niveles P y Totales no suman al reconocimiento.
     */
    @SuppressWarnings("unchecked") public TablaFiscal consultar(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=fuenteRep(f)+" ORDER BY fecha_pago,pago_id,dr_id,impuesto_dr_id";
            if(pagina<0||pagina>Integer.MAX_VALUE/200)throw new IllegalArgumentException("Página inválida");
            List<Object[]> datos=FiscalSql.query(em,sql,f).setFirstResult(pagina*200).setMaxResults(201).getResultList();
            List<List<Object>> filas=new ArrayList<>();
            for(Object[] r:datos.subList(0,Math.min(200,datos.size()))){
                boolean consistente=b(r[39]);
                MotorIva.Resultado resultado=MotorIva.evaluar(new MotorIva.Evidencia((BigDecimal)r[21],s(r[8]),s(r[9]),(BigDecimal)r[10],(BigDecimal)r[11],s(r[22]),s(r[23]),r[6]!=null,consistente,b(r[33]),!"2.0".equals(r[34]),(BigDecimal)r[29],"RECIBIDO".equals(r[7])&&"TRASLADO".equals(r[17])?(BigDecimal)r[30]:BigDecimal.ZERO,"RECIBIDO".equals(r[7])&&"TRASLADO".equals(r[17])?(BigDecimal)r[31]:BigDecimal.ZERO,b(r[32])),"RECIBIDO".equals(r[7])&&"TRASLADO".equals(r[17]));
                List<Object> fila=new ArrayList<>(Arrays.asList(r).subList(0,24));
                fila.add(resultado.realizadoDR());fila.add(resultado.convertidoMXN());fila.add(resultado.potencialDR());fila.add(resultado.acreditableDR());fila.add(resultado.noAcreditableDR());fila.add(resultado.pendienteTratamientoDR());fila.add(resultado.estado());fila.add(String.join("; ",resultado.incidencias())+(b(r[38])?"; AJUSTE_DOCUMENTADO_REVISAR_LIVA_7":""));filas.add(fila);
            }
            return new TablaFiscal(List.of("REP documento","UUID REP","Pago ID","FechaPago","DR ID","UUID factura","Factura documento","Sentido factura","MonedaDR","MonedaP","EquivalenciaDR","TipoCambioP","ImpPagadoDR","Parcialidad","Saldo anteriorDR","Saldo insolutoDR","ImpuestoDR ID","Movimiento","Factor","Tasa/cuota","BaseDR original","IVA DR original","SAT factura","SAT REP","IVA realizadoDR","IVA realizado MXN derivado","IVA potencialDR","IVA acreditable según registroDR","IVA no acreditableDR","IVA pendiente tratamientoDR","Estado auxiliar","Incidencias"),filas,datos.size()>200,
                "Auxiliar por FechaPago. DR es fuente; P/Totales son niveles independientes para revisión. No determina obligación definitiva. Retenciones, ajustes, históricos y requisitos no acreditados requieren revisión profesional.");
        });
    }

    /**
     * Resume en MySQL todas las evidencias filtradas, sin sumar páginas ni mezclar divisas.
     * La realización usa los mismos controles SQL que el detalle y conserva null si no hay
     * importes confirmables. El pendiente es documental, no una determinación fiscal.
     */
    public TablaFiscal resumir(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String documental=FiscalSql.BASE+" SELECT u.sentido,u.moneda,u.estado_sat,i.movimiento,COUNT(*) lineas,COUNT(DISTINCT u.documento_id) documentos,SUM(i.importe) importe FROM universo u JOIN conceptos cc ON cc.documento_id=u.documento_id JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id WHERE i.impuesto='002'"+FiscalSql.filtros(f,"u")+" GROUP BY u.sentido,u.moneda,u.estado_sat,i.movimiento";
            String sql="WITH evidencias_periodo AS ("+fuenteRep(f)+"""
                ),calculos AS (
                  SELECT e.*,
                    CASE WHEN realizable=1 AND sentido_factura='RECIBIDO' AND movimiento='TRASLADO'
                      AND acreditable>=0 AND no_acreditable>=0 AND acreditable+no_acreditable<=impuesto_original
                      THEN CASE WHEN impuesto_original=0 THEN 0 ELSE TRUNCATE(iva_dr*acreditable/impuesto_original,10) END END potencial,
                    CASE WHEN realizable=1 AND sentido_factura='RECIBIDO' AND movimiento='TRASLADO'
                      AND acreditable>=0 AND no_acreditable>=0 AND acreditable+no_acreditable<=impuesto_original
                      THEN CASE WHEN impuesto_original=0 THEN 0 ELSE TRUNCATE(iva_dr*no_acreditable/impuesto_original,10) END END no_acreditable_dr
                  FROM evidencias_periodo e
                ),documental AS (
                """+documental+"""
                ),caratula AS (
                SELECT sentido_factura,moneda_dr,movimiento,sat_factura,sat_rep,
                  COUNT(*) lineas,COUNT(DISTINCT dr_id) documentos_relacionados,
                  SUM(iva_dr) iva_documentado_dr,
                  SUM(CASE WHEN realizable=1 THEN iva_dr END) iva_realizado_dr,
                  SUM(CASE WHEN realizable=0 THEN iva_dr END) iva_pendiente_evidencia_dr,
                  SUM(CASE WHEN realizable=0 THEN 1 ELSE 0 END) lineas_pendientes,
                  SUM(CASE WHEN iva_dr IS NULL THEN 1 ELSE 0 END) lineas_sin_importe,
                  NULL iva_documental,SUM(potencial) potencial,
                  SUM(CASE WHEN iva_retenido=0 THEN potencial END) acreditable,
                  SUM(no_acreditable_dr) no_acreditable,
                  SUM(CASE WHEN potencial IS NOT NULL THEN iva_dr-no_acreditable_dr-CASE WHEN iva_retenido=0 THEN potencial ELSE 0 END END) pendiente_tratamiento,
                  'REP_FECHA_PAGO' fuente
                FROM calculos
                GROUP BY sentido_factura,moneda_dr,movimiento,sat_factura,sat_rep
                UNION ALL
                SELECT sentido,moneda,movimiento,estado_sat,NULL,lineas,documentos,NULL,NULL,NULL,NULL,NULL,importe,NULL,NULL,NULL,NULL,'CFDI_EMISION'
                FROM documental
                )
                SELECT * FROM caratula ORDER BY fuente,sentido_factura,moneda_dr,movimiento,sat_factura,sat_rep
                """;
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Sentido factura","MonedaDR","Movimiento","SAT factura","SAT REP","Líneas DR / conceptos","Documentos relacionados","IVA DR documentado original","IVA realizadoDR","IVA DR pendiente de evidencia","Líneas pendientes","Líneas sin importe declarado","IVA documental por emisión","IVA potencialmente acreditableDR","IVA acreditable según tratamientoDR","IVA no acreditableDR","IVA pendiente tratamientoDR","Fuente temporal"),
                "Carátula preliminar: originales por emisión y realización REP por FechaPago en filas independientes. Créditos sólo de traslados recibidos con tratamiento. Retenciones exigen revisión del entero y periodo. No sumar dimensiones ni monedas; manuales pendientes en su pestaña.");
        });
    }

    /**
     * Consulta evidencia manual aplicada del titular en su fecha de movimiento.
     * El IVA proporcional es una estimación derivada pendiente de revisión fiscal, nunca
     * una confirmación automática. Un REP activo no vinculado suspende la estimación para
     * evitar que la misma realización se agregue desde dos fuentes.
     */
    public TablaFiscal consultarManuales(Long actor,FiltroFiscal f,int pagina){
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+"""
                ,iva_original_manual AS (
                  SELECT cc.documento_id,SUM(i.importe) iva
                  FROM activos fac JOIN cfdi_concepto cc ON cc.documento_id=fac.documento_id
                  JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id
                  WHERE i.impuesto='002' AND i.movimiento='TRASLADO' GROUP BY cc.documento_id
                ),manuales AS (
                  SELECT fac.documento_id,fac.uuid,m.id movimiento_id,m.fecha_movimiento,fac.sentido,
                    fac.moneda moneda_factura,m.importe importe_pago,m.moneda moneda_pago,
                    a.importe_moneda_obligacion importe_aplicado,o.monto_original,io.iva iva_original,
                    fac.estado_sat,fac.total,fac.tipo,m.referencia,m.naturaleza naturaleza_movimiento,
                    o.moneda moneda_obligacion,a.importe_moneda_pago,
                    EXISTS(SELECT 1 FROM activos rep JOIN pago_fiscal p ON p.documento_id=rep.documento_id
                      JOIN pago_fiscal_documento pd ON pd.pago_fiscal_id=p.id
                      WHERE pd.uuid_documento=fac.uuid AND rep.estado_sat<>'CANCELADO'
                        AND NOT EXISTS(SELECT 1 FROM movimiento_pago_complemento mc
                          JOIN movimiento_pago vinculado ON vinculado.id=mc.movimiento_pago_id
                          WHERE mc.pago_fiscal_documento_id=pd.id AND vinculado.contribuyente_id=:t)) rep_sin_vincular
                  FROM activos fac JOIN obligacion o ON o.documento_id=fac.documento_id AND o.contribuyente_id=:t
                  JOIN aplicacion_pago a ON a.obligacion_id=o.id
                  JOIN movimiento_pago m ON m.id=a.movimiento_pago_id AND m.contribuyente_id=:t
                  LEFT JOIN iva_original_manual io ON io.documento_id=fac.documento_id
                  WHERE m.origen='MANUAL' AND m.fecha_movimiento>=:desde AND m.fecha_movimiento<:hasta
                    AND NOT EXISTS(SELECT 1 FROM movimiento_pago_complemento mc
                      JOIN movimiento_pago vinculado ON vinculado.id=mc.movimiento_pago_id
                      WHERE mc.movimiento_pago_id=m.id AND vinculado.contribuyente_id=:t)
                """+FiscalSql.filtros(f,"fac")+"""
                ),manuales_evaluados AS (
                  SELECT m.*,COALESCE(m.tipo='I' AND m.estado_sat='VIGENTE' AND m.rep_sin_vincular=0
                    AND m.iva_original>=0 AND m.monto_original>0 AND m.monto_original=m.total
                    AND m.moneda_obligacion=m.moneda_factura AND m.importe_aplicado>0
                    AND m.importe_aplicado<=m.monto_original AND m.importe_moneda_pago>0
                    AND m.importe_moneda_pago<=m.importe_pago
                    AND ((m.sentido='RECIBIDO' AND m.naturaleza_movimiento='PAGO')
                      OR (m.sentido='EMITIDO' AND m.naturaleza_movimiento='COBRO')),0) estimable
                  FROM manuales m
                )
                SELECT documento_id,uuid,movimiento_id,fecha_movimiento,sentido,moneda_factura,
                  importe_pago,moneda_pago,importe_aplicado,iva_original,
                  CASE WHEN estimable=1 THEN CAST(iva_original*importe_aplicado/monto_original AS DECIMAL(30,10)) END iva_proporcional_derivado,
                  'PENDIENTE_REVISION_FISCAL' estado,sat_factura,
                  CONCAT_WS('; ', 'EVIDENCIA_MANUAL_NO_CONFIRMA_REQUISITOS_FISCALES',
                    CASE WHEN rep_sin_vincular=1 THEN 'REP_ACTIVO_SIN_VINCULAR_EVITAR_DUPLICACION' END,
                    CASE WHEN estimable=0 THEN 'ESTIMACION_IVA_NO_DISPONIBLE_O_INCONSISTENTE' END),referencia
                FROM (SELECT me.*,me.estado_sat sat_factura FROM manuales_evaluados me) datos
                ORDER BY fecha_movimiento,movimiento_id,documento_id
                """;
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("Documento","UUID factura","Movimiento ID","Fecha movimiento","Sentido","Moneda factura","Importe movimiento","Moneda movimiento","Aplicación en moneda factura","IVA original factura","IVA proporcional DERIVADO pendiente","Estado auxiliar","SAT factura","Incidencias","Referencia de evidencia"),
                "Evidencia administrativa manual por fecha de movimiento. El IVA proporcional es DERIVADO y pendiente de revisión, no impuesto declarado ni acreditable confirmado. No sumar esta estimación al REP de la misma realización.");
        });
    }

    /** Fuente única del detalle y del agregado; los controles se evalúan antes de paginar. */
    private static String fuenteRep(FiltroFiscal f){
        return FiscalSql.BASE+"""
                ,firmas AS (
                  SELECT cc.documento_id,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota,SUM(i.importe) original,
                    SUM(COALESCE(tr.acreditable,0)) acreditable,SUM(COALESCE(tr.no_acreditable,0)) no_acreditable
                  FROM activos u JOIN cfdi_concepto cc ON cc.documento_id=u.documento_id JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id
                  LEFT JOIN (SELECT cl.impuesto_concepto_id,
                    SUM(CASE WHEN ti.clave='IVA_ACREDITABLE' THEN cl.importe_asignado ELSE 0 END) acreditable,
                    SUM(CASE WHEN ti.clave='IVA_NO_ACREDITABLE' THEN cl.importe_asignado ELSE 0 END) no_acreditable
                    FROM clasificacion_impuesto_concepto cl JOIN tratamiento_impuesto ti ON ti.id=cl.tratamiento_id WHERE cl.contribuyente_id=:t GROUP BY cl.impuesto_concepto_id) tr ON tr.impuesto_concepto_id=i.id
                  GROUP BY cc.documento_id,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota
                ), acumulados AS (
                  SELECT pd.uuid_documento,SUM(pd.importe_pagado) pagado,COUNT(*) cantidad,COUNT(DISTINCT pd.num_parcialidad) parcialidades
                  FROM activos rep JOIN pago_fiscal p ON p.documento_id=rep.documento_id JOIN pago_fiscal_documento pd ON pd.pago_fiscal_id=p.id
                  WHERE rep.estado_sat='VIGENTE' AND p.fecha_pago<:hasta GROUP BY pd.uuid_documento
                ), impuestos_acumulados AS (
                  SELECT pd.uuid_documento,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota,SUM(i.importe) importe
                  FROM activos rep JOIN pago_fiscal p ON p.documento_id=rep.documento_id JOIN pago_fiscal_documento pd ON pd.pago_fiscal_id=p.id
                  JOIN pago_fiscal_impuesto_documento i ON i.pago_documento_id=pd.id
                  WHERE rep.estado_sat='VIGENTE' AND p.fecha_pago<:hasta GROUP BY pd.uuid_documento,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota
                ),rep_filas AS (
                SELECT rep.documento_id rep_documento,rep.uuid uuid_rep,p.id pago_id,p.fecha_pago,pd.id dr_id,pd.uuid_documento uuid_factura,fac.documento_id factura_documento,
                  fac.sentido sentido_factura,pd.moneda moneda_dr,p.moneda moneda_pago,pd.equivalencia_dr,p.tipo_cambio tipo_cambio_pago,pd.importe_pagado,
                  pd.num_parcialidad,pd.saldo_anterior,pd.saldo_insoluto,i.id impuesto_dr_id,i.movimiento,i.tipo_factor,i.tasa_cuota,i.base base_dr,i.importe iva_dr,
                  fac.estado_sat sat_factura,rep.estado_sat sat_rep,fac.moneda moneda_factura,fac.total total_factura,ac.pagado pagado_acumulado,ac.cantidad cantidad_pagos,ac.parcialidades,
                  fm.original impuesto_original,fm.acreditable,fm.no_acreditable,
                  EXISTS(SELECT 1 FROM cfdi_concepto cc JOIN cfdi_concepto_impuesto ii ON ii.concepto_id=cc.id WHERE cc.documento_id=fac.documento_id AND ii.impuesto='002' AND ii.movimiento='RETENCION') iva_retenido,
                  (EXISTS(SELECT 1 FROM cfdi_relacion_grupo g WHERE g.documento_id IN (fac.documento_id,rep.documento_id) AND g.tipo_relacion='04')
                    OR EXISTS(SELECT 1 FROM cfdi_relacion_uuid ru JOIN cfdi_relacion_grupo g ON g.id=ru.grupo_id JOIN activos sustituto ON sustituto.documento_id=g.documento_id WHERE ru.uuid_relacionado IN (fac.uuid,rep.uuid) AND g.tipo_relacion='04' AND sustituto.fecha_emision<:hasta)) sustitucion,
                  pc.version_complemento,ia.importe impuesto_acumulado,fac.tipo tipo_factura,rep.sentido sentido_rep,
                  (EXISTS(SELECT 1 FROM ajuste_obligacion a JOIN obligacion o ON o.id=a.obligacion_id WHERE o.contribuyente_id=:t AND o.documento_id=fac.documento_id AND a.fecha_ajuste<DATE(:hasta))
                    OR EXISTS(SELECT 1 FROM cfdi_relacion_uuid ru JOIN cfdi_relacion_grupo g ON g.id=ru.grupo_id JOIN activos nota ON nota.documento_id=g.documento_id WHERE ru.uuid_relacionado=fac.uuid AND nota.tipo='E' AND g.tipo_relacion IN ('01','03','07') AND nota.fecha_emision<:hasta)) ajuste_pendiente
                FROM activos rep JOIN pago_fiscal p ON p.documento_id=rep.documento_id JOIN pago_fiscal_documento pd ON pd.pago_fiscal_id=p.id
                LEFT JOIN activos fac ON fac.uuid=pd.uuid_documento
                LEFT JOIN pago_fiscal_complemento pc ON pc.documento_id=rep.documento_id
                LEFT JOIN pago_fiscal_impuesto_documento i ON i.pago_documento_id=pd.id AND i.impuesto='002'
                LEFT JOIN firmas fm ON fm.documento_id=fac.documento_id AND fm.impuesto='002' AND fm.movimiento=i.movimiento AND fm.tipo_factor<=>i.tipo_factor AND fm.tasa_cuota<=>i.tasa_cuota
                LEFT JOIN acumulados ac ON ac.uuid_documento=pd.uuid_documento
                LEFT JOIN impuestos_acumulados ia ON ia.uuid_documento=pd.uuid_documento AND ia.impuesto='002' AND ia.movimiento=i.movimiento AND ia.tipo_factor<=>i.tipo_factor AND ia.tasa_cuota<=>i.tasa_cuota
                WHERE p.fecha_pago>=:desde AND p.fecha_pago<:hasta
                """+FiscalSql.filtros(f,"fac")+"""
                ),rep_controles AS (
                  SELECT rf.*,COALESCE(importe_pagado+saldo_insoluto=saldo_anterior
                    AND moneda_factura=moneda_dr AND pagado_acumulado<=total_factura
                    AND ((moneda_dr=moneda_pago AND COALESCE(equivalencia_dr,1)=1) OR (moneda_dr<>moneda_pago AND equivalencia_dr>0))
                    AND (moneda_pago='MXN' OR tipo_cambio_pago>0)
                    AND NOT EXISTS(SELECT 1 FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE p.id=rf.pago_id AND (pd.importe_pagado IS NULL OR (pd.moneda<>p.moneda AND (pd.equivalencia_dr IS NULL OR pd.equivalencia_dr<=0))))
                    AND (SELECT SUM(pd.importe_pagado/CASE WHEN pd.moneda=p.moneda THEN 1 ELSE pd.equivalencia_dr END) FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE p.id=rf.pago_id)<=(SELECT p.monto FROM pago_fiscal p WHERE p.id=rf.pago_id)
                    AND cantidad_pagos=parcialidades AND impuesto_acumulado<=impuesto_original
                    AND tipo_factura='I' AND EXISTS(SELECT 1 FROM activos fac JOIN activos rep ON rep.documento_id=rf.rep_documento WHERE fac.documento_id=rf.factura_documento AND fac.rfc_tercero=rep.rfc_tercero) AND sentido_factura=sentido_rep AND sentido_factura<>'AMBOS' AND ajuste_pendiente=0 AND fecha_pago>='2022-01-01' AND fecha_pago<'2027-01-01',0) consistente
                  FROM rep_filas rf
                )
                SELECT rc.*,COALESCE(factura_documento IS NOT NULL AND sat_factura='VIGENTE'
                  AND sat_rep='VIGENTE' AND consistente=1 AND sustitucion=0 AND version_complemento='2.0'
                  AND iva_dr>=0 AND impuesto_original>=0 AND iva_dr<=impuesto_original,0) realizable
                FROM rep_controles rc
                """;
    }
    private static String s(Object o){return o==null?"SIN_CONSULTA":o.toString();}
    private static boolean b(Object o){return o instanceof Boolean v?v:o instanceof Number n&&n.intValue()!=0;}
}
