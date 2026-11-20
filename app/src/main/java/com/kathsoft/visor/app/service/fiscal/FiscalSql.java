package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;

/** Proyecciones acotadas por titular; originales, distribuciones e impuestos se agregan en etapas separadas. */
final class FiscalSql {
    private FiscalSql() { }
    static final String BASE="""
        WITH activos AS (
          SELECT d.id documento_id,d.uuid,d.fecha_emision,c.tipo,c.moneda,c.tipo_cambio,c.subtotal,
            COALESCE(c.descuento,0) descuento,c.total,c.metodo_pago,cd.sentido,
            COALESCE(s.estado,'SIN_CONSULTA') estado_sat,p.tercero_id,p.rfc rfc_tercero,p.nombre_razon_social nombre_tercero,
            h.categoria_id,cat.nombre categoria,COALESCE(cat.naturaleza,'SIN_CLASIFICAR') naturaleza
          FROM contribuyente_documento cd JOIN documento_fiscal d ON d.id=cd.documento_id JOIN cfdi c ON c.documento_id=d.id
          LEFT JOIN documento_participante p ON p.documento_id=d.id AND p.rol=CASE WHEN cd.sentido='RECIBIDO' THEN 'E' ELSE 'R' END
          LEFT JOIN consulta_estado_sat s ON s.id=(SELECT ss.id FROM consulta_estado_sat ss WHERE ss.documento_id=d.id ORDER BY ss.fecha_consulta DESC,ss.id DESC LIMIT 1)
          LEFT JOIN clasificacion_operacion_evento h ON h.id=(SELECT MAX(hh.id) FROM clasificacion_operacion_evento hh WHERE hh.contribuyente_id=:t AND hh.documento_id=d.id)
          LEFT JOIN categoria_operacion cat ON cat.id=h.categoria_id
          WHERE cd.contribuyente_id=:t AND cd.archivado_en IS NULL
        ), universo AS (
          SELECT * FROM activos u WHERE u.fecha_emision>=:desde AND u.fecha_emision<:hasta
        ), conceptos AS (
          SELECT cc.*,cc.importe-COALESCE(cc.descuento,0) neto FROM universo u JOIN cfdi_concepto cc ON cc.documento_id=u.documento_id
        ), distribuidos AS (
          SELECT c.concepto_id,COUNT(*) cantidad,SUM(c.importe_asignado) asignado
          FROM clasificacion_concepto c JOIN conceptos cc ON cc.id=c.concepto_id WHERE c.contribuyente_id=:t GROUP BY c.concepto_id
        ), partes AS (
          SELECT cc.id concepto_id,cc.documento_id,cat.id categoria_id,cat.nombre categoria,cat.naturaleza,SUM(c.importe_asignado) neto,
            cc.neto original_neto,'DETALLE' origen
          FROM conceptos cc JOIN distribuidos ds ON ds.concepto_id=cc.id
          JOIN clasificacion_concepto c ON c.concepto_id=cc.id AND c.contribuyente_id=:t
          JOIN categoria_operacion cat ON cat.id=c.categoria_id AND cat.contribuyente_id=:t
          WHERE ds.asignado<=cc.neto GROUP BY cc.id,cc.documento_id,cat.id,cat.nombre,cat.naturaleza,cc.neto
          UNION ALL
          SELECT cc.id,cc.documento_id,NULL,NULL,'SIN_CLASIFICAR',cc.neto-ds.asignado,cc.neto,'REMANENTE'
          FROM conceptos cc JOIN distribuidos ds ON ds.concepto_id=cc.id WHERE ds.asignado<cc.neto
          UNION ALL
          SELECT cc.id,cc.documento_id,u.categoria_id,u.categoria,u.naturaleza,cc.neto,cc.neto,'GENERAL'
          FROM conceptos cc JOIN universo u ON u.documento_id=cc.documento_id LEFT JOIN distribuidos ds ON ds.concepto_id=cc.id WHERE ds.concepto_id IS NULL
          UNION ALL
          SELECT cc.id,cc.documento_id,NULL,NULL,'SIN_CLASIFICAR',cc.neto,cc.neto,'REVISAR_EXCESO'
          FROM conceptos cc JOIN distribuidos ds ON ds.concepto_id=cc.id WHERE ds.asignado>cc.neto
          UNION ALL
          SELECT NULL,u.documento_id,u.categoria_id,u.categoria,u.naturaleza,u.subtotal-u.descuento,u.subtotal-u.descuento,'SIN_CONCEPTOS'
          FROM universo u WHERE NOT EXISTS(SELECT 1 FROM conceptos cc WHERE cc.documento_id=u.documento_id)
        ), iva_concepto AS (
          SELECT i.concepto_id,COUNT(*) lineas,MAX(i.tipo_factor) factor,MAX(i.tasa_cuota) tasa,SUM(i.base) base,SUM(i.importe) iva
          FROM conceptos cc JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id WHERE i.impuesto='002' AND i.movimiento='TRASLADO' GROUP BY i.concepto_id
        )
        """;
    /**
     * Reparte únicamente valores derivados. Cada impuesto mantiene su base e importe originales.
     * Redondea las partes a seis decimales HALF_UP y asigna el residuo a la última categoría
     * (orden por ID; remanente primero). Así las partes completas reconstruyen el original.
     * Si el neto no es positivo, no inventa una proporción y deja derivados en null.
     */
    static final String IMPUESTOS_DISTRIBUIDOS="""
        ,impuestos_partidos AS (
          SELECT p.concepto_id,p.categoria_id,i.id impuesto_id,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota,
            i.base base_original,i.importe impuesto_original,
            CASE WHEN p.original_neto>0 THEN ROUND(i.base*p.neto/p.original_neto,6) END base_parte,
            CASE WHEN p.original_neto>0 THEN ROUND(i.importe*p.neto/p.original_neto,6) END impuesto_parte,
            ROW_NUMBER() OVER(PARTITION BY p.concepto_id,i.id ORDER BY COALESCE(p.categoria_id,0)) posicion,
            COUNT(*) OVER(PARTITION BY p.concepto_id,i.id) cantidad
          FROM partes p JOIN cfdi_concepto_impuesto i ON i.concepto_id=p.concepto_id
        ),impuestos_distribuidos AS (
          SELECT ip.*,
            CASE WHEN base_parte IS NULL THEN NULL WHEN posicion=cantidad THEN base_original-SUM(base_parte) OVER(PARTITION BY concepto_id,impuesto_id)+base_parte ELSE base_parte END base_distribuida,
            CASE WHEN impuesto_parte IS NULL THEN NULL WHEN posicion=cantidad THEN impuesto_original-SUM(impuesto_parte) OVER(PARTITION BY concepto_id,impuesto_id)+impuesto_parte ELSE impuesto_parte END impuesto_distribuido
          FROM impuestos_partidos ip
        )
        """;
    static Query query(EntityManager em,String sql,FiltroFiscal f){
        Query q=em.createNativeQuery(sql);
        if(sql.contains(":t"))q.setParameter("t",f.titular());
        if(sql.contains(":desde"))q.setParameter("desde",Timestamp.valueOf(f.desde().atStartOfDay()));
        if(sql.contains(":hasta"))q.setParameter("hasta",Timestamp.valueOf(f.hasta().plusDays(1).atStartOfDay()));
        if(sql.contains(":sentido"))q.setParameter("sentido",f.sentido());
        if(sql.contains(":rfc"))q.setParameter("rfc",f.rfc()+"%");
        if(sql.contains(":naturaleza"))q.setParameter("naturaleza",f.naturaleza());
        if(sql.contains(":categoria"))q.setParameter("categoria",f.categoria());
        if(sql.contains(":moneda"))q.setParameter("moneda",f.moneda());
        if(sql.contains(":tipo"))q.setParameter("tipo",f.tipo());
        if(sql.contains(":estado"))q.setParameter("estado",f.estadoSat());
        return q;
    }
    /** Predicados documentales reutilizables, sin limitar la fecha de emisión en el auxiliar de pagos. */
    static String filtros(FiltroFiscal f,String alias){
        String a=alias+".";String r="";
        if(!f.sentido().isEmpty())r+=" AND "+a+"sentido=:sentido";
        if(!f.rfc().isEmpty())r+=" AND "+a+"rfc_tercero LIKE :rfc";
        if(!f.moneda().isEmpty())r+=" AND "+a+"moneda=:moneda";
        if(!f.tipo().isEmpty())r+=" AND "+a+"tipo=:tipo";
        if(!f.estadoSat().isEmpty())r+=" AND "+a+"estado_sat=:estado";
        if(f.categoria()>0)r+=" AND ("+a+"categoria_id=:categoria OR EXISTS (SELECT 1 FROM clasificacion_concepto cl JOIN cfdi_concepto cc ON cc.id=cl.concepto_id WHERE cl.contribuyente_id=:t AND cc.documento_id="+a+"documento_id AND cl.categoria_id=:categoria))";
        if(!f.naturaleza().isEmpty())r+=" AND ("+a+"naturaleza=:naturaleza OR EXISTS (SELECT 1 FROM clasificacion_concepto cl JOIN cfdi_concepto cc ON cc.id=cl.concepto_id JOIN categoria_operacion ct ON ct.id=cl.categoria_id WHERE cl.contribuyente_id=:t AND cc.documento_id="+a+"documento_id AND ct.naturaleza=:naturaleza))";
        return r;
    }
}
