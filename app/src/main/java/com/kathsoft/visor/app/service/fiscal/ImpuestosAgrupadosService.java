package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import java.util.List;
import java.util.Objects;

/** Agrupa bases e impuestos originales por sus dimensiones efectivas sin multiplicar conceptos. */
public final class ImpuestosAgrupadosService {
    private final JpaContext jpa;

    /** Conserva el contexto de la sesión; ninguna consulta se ejecuta al construir el servicio. */
    public ImpuestosAgrupadosService(JpaContext jpa) { this.jpa=Objects.requireNonNull(jpa); }

    /**
     * Agrupa contribuyente/periodo, tercero, naturaleza, categoría y firma tributaria.
     * Una clasificación parcial genera una fila de tratamiento y otra pendiente. Se trunca
     * cada producto derivado a seis decimales y el último tratamiento recibe el residuo
     * de su parte, sin modificar la base o el impuesto importados. Un legado que exceda
     * el original queda sin monto derivado y requiere revisión.
     */
    public TablaFiscal consultar(Long actor,FiltroFiscal f,int pagina) {
        return jpa.enTransaccion(em->{
            AccesoFiscal.exigir(em,actor,f.titular(),Permisos.FISCAL_CONSULTAR);
            String sql=FiscalSql.BASE+FiscalSql.IMPUESTOS_DISTRIBUIDOS+"""
                ,tratados AS (
                  SELECT cl.impuesto_concepto_id,cl.tratamiento_id,t.clave,cl.importe_asignado
                  FROM clasificacion_impuesto_concepto cl JOIN tratamiento_impuesto t ON t.id=cl.tratamiento_id
                  WHERE cl.contribuyente_id=:t
                ),totales_tratados AS (
                  SELECT impuesto_concepto_id,SUM(importe_asignado) importe FROM tratados GROUP BY impuesto_concepto_id
                ),destinos AS (
                  SELECT i.id impuesto_id,tr.tratamiento_id,tr.clave,tr.importe_asignado asignado,COALESCE(tt.importe,0) total_tratado
                  FROM conceptos cc JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id
                  JOIN tratados tr ON tr.impuesto_concepto_id=i.id
                  LEFT JOIN totales_tratados tt ON tt.impuesto_concepto_id=i.id
                  WHERE i.importe>0
                  UNION ALL
                  SELECT i.id,NULL,'PENDIENTE',
                    CASE WHEN i.importe>0 THEN i.importe-COALESCE(tt.importe,0) ELSE i.importe END,
                    COALESCE(tt.importe,0)
                  FROM conceptos cc JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id
                  LEFT JOIN totales_tratados tt ON tt.impuesto_concepto_id=i.id
                  WHERE i.importe IS NULL OR i.importe<=0 OR COALESCE(tt.importe,0)<i.importe
                ),fracciones AS (
                  SELECT p.documento_id,p.concepto_id,p.categoria_id,p.categoria,p.naturaleza,
                    i.impuesto_id,i.impuesto,i.movimiento,i.tipo_factor,i.tasa_cuota,i.base_original,i.impuesto_original,
                    i.base_distribuida,i.impuesto_distribuido,d.clave tratamiento,d.total_tratado,
                    ROW_NUMBER() OVER(PARTITION BY p.concepto_id,i.impuesto_id,p.categoria_id ORDER BY COALESCE(d.tratamiento_id,999999)) posicion,
                    COUNT(*) OVER(PARTITION BY p.concepto_id,i.impuesto_id,p.categoria_id) cantidad,
                    CASE WHEN i.impuesto_original>0 AND d.total_tratado<=i.impuesto_original
                      THEN TRUNCATE(i.base_distribuida*d.asignado/i.impuesto_original,6)
                      WHEN i.impuesto_original IS NULL OR i.impuesto_original=0 THEN i.base_distribuida END base_parte,
                    CASE WHEN i.impuesto_original>0 AND d.total_tratado<=i.impuesto_original
                      THEN TRUNCATE(i.impuesto_distribuido*d.asignado/i.impuesto_original,6)
                      WHEN i.impuesto_original IS NULL OR i.impuesto_original=0 THEN i.impuesto_distribuido END impuesto_parte
                  FROM partes p JOIN impuestos_distribuidos i ON i.concepto_id=p.concepto_id AND i.categoria_id<=>p.categoria_id
                  JOIN destinos d ON d.impuesto_id=i.impuesto_id
                ),clasificados AS (
                  SELECT f.*,
                    CASE WHEN base_parte IS NULL THEN NULL WHEN posicion=cantidad
                      THEN base_distribuida-SUM(base_parte) OVER(PARTITION BY concepto_id,impuesto_id,categoria_id)+base_parte
                      ELSE base_parte END base_derivada,
                    CASE WHEN impuesto_parte IS NULL THEN NULL WHEN posicion=cantidad
                      THEN impuesto_distribuido-SUM(impuesto_parte) OVER(PARTITION BY concepto_id,impuesto_id,categoria_id)+impuesto_parte
                      ELSE impuesto_parte END impuesto_derivado
                  FROM fracciones f
                )
                SELECT u.rfc_tercero,u.nombre_tercero,u.sentido,u.tipo,u.moneda,u.estado_sat,
                  c.naturaleza,c.categoria,c.categoria_id,c.impuesto,c.movimiento,c.tipo_factor,c.tasa_cuota,c.tratamiento,
                  COUNT(DISTINCT u.documento_id),SUM(c.base_derivada),SUM(c.impuesto_derivado),
                  CASE WHEN MAX(c.total_tratado)>MAX(c.impuesto_original) THEN 'REVISAR_EXCESO_TRATAMIENTO'
                    WHEN c.tratamiento='PENDIENTE' THEN 'PENDIENTE_TRATAMIENTO'
                    ELSE 'DISTRIBUCION_DERIVADA' END estado
                FROM clasificados c JOIN universo u ON u.documento_id=c.documento_id
                WHERE 1=1
                """+FiscalSql.filtros(f,"u")+FiscalSql.impuestos(f,"c")
                    +(!f.naturaleza().isEmpty()?" AND c.naturaleza=:naturaleza":"")
                    +(f.categoria()>0?" AND c.categoria_id=:categoria":"")+"""
                GROUP BY u.rfc_tercero,u.nombre_tercero,u.sentido,u.tipo,u.moneda,u.estado_sat,
                  c.naturaleza,c.categoria,c.categoria_id,c.impuesto,c.movimiento,c.tipo_factor,c.tasa_cuota,c.tratamiento
                ORDER BY u.rfc_tercero,c.naturaleza,c.categoria,c.impuesto,c.movimiento,c.tipo_factor,c.tasa_cuota,c.tratamiento
                """;
            return ImpuestosService.tabla(em,sql,f,pagina,List.of("RFC","Tercero","Sentido","Tipo","Moneda","SAT",
                    "Naturaleza","Categoría","Categoría ID","Impuesto","Movimiento","Factor","Tasa/cuota",
                    "Tratamiento fiscal","Documentos","Base distribuida DERIVADA","Impuesto distribuido DERIVADO","Estado clasificación"),
                    "Las sumas por categoría y tratamiento son distribuciones derivadas de cada impuesto original. Exento conserva base y null de importe. Un legado excedido queda sin monto; consulte Orígenes y el impuesto original.");
        });
    }
}
