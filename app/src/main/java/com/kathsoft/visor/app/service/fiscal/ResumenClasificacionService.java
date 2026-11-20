package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.GrupoResumen;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Resumen;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Proyección RF-020 que reutiliza la precedencia y los residuos de RF-021.
 * Los filtros siguen perteneciendo al listado de operaciones: el helper no
 * sustituye su semántica por los filtros de los auxiliares fiscales.
 */
public final class ResumenClasificacionService {
    private ResumenClasificacionService() { }

    /**
     * Resume el universo ya autorizado dentro de la misma transacción JPA.
     *
     * <p>El llamador debe exigir OPERACIONES.CONSULTAR y alcance explícito antes
     * de invocar este contrato interno. No se añade permiso FISCAL.CONSULTAR.
     * El SELECT y la función de parámetros proceden exclusivamente del servicio,
     * nunca de texto de la interfaz. La selección devuelve documento_id y
     * categoria_id general y conserva exactamente el universo del listado.</p>
     *
     * <p>Los conteos globales son documentales. Un mismo documento puede aparecer
     * en varias naturalezas sin incrementar esos conteos. Para netos no positivos
     * no se inventa proporción: el IVA original queda una sola vez en un grupo
     * SIN_CLASIFICAR. Las diferencias de cabecera se conservan separadamente.</p>
     *
     * @param em administrador de entidades de la transacción del llamador
     * @param seleccionSql SELECT interno que identifica el universo RF-020
     * @param preparar crea cada consulta sobre em y aplica los parámetros RF-020
     * @return conteos globales y grupos documentales por naturaleza y divisa
     */
    public static Resumen resumir(EntityManager em, String seleccionSql, Function<String, Query> preparar) {
        Objects.requireNonNull(em);
        Objects.requireNonNull(seleccionSql);
        Objects.requireNonNull(preparar);
        Object[] conteos = (Object[]) preparar.apply("SELECT COUNT(*),COUNT(categoria_id) FROM (" + seleccionSql + ") seleccion_rf020")
                .getSingleResult();
        String sql = FiscalSql.BASE + FiscalSql.IMPUESTOS_DISTRIBUIDOS + """
            ,seleccion_rf020 AS (
            """ + seleccionSql + """
            ),documentos_resumen AS (
              SELECT u.* FROM universo u JOIN seleccion_rf020 s ON s.documento_id=u.documento_id
            ),controles_iva AS (
              SELECT i.concepto_id,COUNT(i.importe) con_importe
              FROM conceptos cc JOIN cfdi_concepto_impuesto i ON i.concepto_id=cc.id
              WHERE i.impuesto='002' AND i.movimiento='TRASLADO' GROUP BY i.concepto_id
            ),iva_partes AS (
              SELECT concepto_id,categoria_id,SUM(base_distribuida) base_iva,
                SUM(impuesto_distribuido) iva
              FROM impuestos_distribuidos WHERE impuesto='002' AND movimiento='TRASLADO'
              GROUP BY concepto_id,categoria_id
            ),conceptos_resumen AS (
              SELECT p.documento_id,p.naturaleza,u.moneda,u.sentido,u.tipo,u.estado_sat,p.neto,
                ip.base_iva,CASE WHEN p.original_neto>0 THEN COALESCE(ip.iva,0) ELSE 0 END iva,
                CASE
                  WHEN p.origen='SIN_CONCEPTOS' THEN 'SIN_CONCEPTOS'
                  WHEN p.original_neto<=0 OR p.origen='REVISAR_EXCESO' THEN 'REVISAR'
                  WHEN ic.lineas IS NULL THEN CASE WHEN cc.objeto_impuesto='01' THEN 'NO_OBJETO' ELSE 'SIN_DESGLOSE' END
                  WHEN ic.lineas<>1 OR cc.objeto_impuesto='01' THEN 'REVISAR'
                  WHEN ic.factor='Exento' AND ci.con_importe=0 AND ic.tasa IS NULL THEN 'EXENTO'
                  WHEN ic.factor='Tasa' AND ic.tasa>=0 AND ci.con_importe=1 THEN
                    CASE WHEN ic.tasa=0 THEN 'TASA0' WHEN ic.tasa=0.16 THEN 'TASA16' ELSE 'OTRAS' END
                  ELSE 'REVISAR'
                END grupo
              FROM partes p JOIN documentos_resumen u ON u.documento_id=p.documento_id
              LEFT JOIN conceptos cc ON cc.id=p.concepto_id
              LEFT JOIN iva_concepto ic ON ic.concepto_id=p.concepto_id
              LEFT JOIN controles_iva ci ON ci.concepto_id=p.concepto_id
              LEFT JOIN iva_partes ip ON ip.concepto_id=p.concepto_id AND ip.categoria_id<=>p.categoria_id
            ),control_neto AS (
              SELECT p.documento_id,SUM(p.neto) neto FROM partes p
              JOIN documentos_resumen u ON u.documento_id=p.documento_id GROUP BY p.documento_id
            ),filas_resumen AS (
              SELECT * FROM conceptos_resumen
              UNION ALL
              SELECT u.documento_id,'SIN_CLASIFICAR',u.moneda,u.sentido,u.tipo,u.estado_sat,
                0,0,ic.iva,'IVA_SIN_PROPORCION'
              FROM documentos_resumen u JOIN conceptos cc ON cc.documento_id=u.documento_id
              JOIN iva_concepto ic ON ic.concepto_id=cc.id WHERE cc.neto<=0 AND COALESCE(ic.iva,0)<>0
              UNION ALL
              SELECT u.documento_id,'SIN_CLASIFICAR',u.moneda,u.sentido,u.tipo,u.estado_sat,
                0,u.subtotal-u.descuento-cn.neto,0,'DIFERENCIA_CABECERA'
              FROM documentos_resumen u JOIN control_neto cn ON cn.documento_id=u.documento_id
              WHERE u.subtotal-u.descuento<>cn.neto
            ),por_documento AS (
              SELECT documento_id,naturaleza,moneda,sentido,tipo,estado_sat,SUM(neto) neto,
                SUM(CASE WHEN grupo='EXENTO' THEN base_iva ELSE 0 END) exento,
                SUM(CASE WHEN grupo='TASA0' THEN base_iva ELSE 0 END) tasa0,
                SUM(CASE WHEN grupo='TASA16' THEN base_iva ELSE 0 END) tasa16,
                SUM(CASE WHEN grupo='OTRAS' THEN base_iva ELSE 0 END) otras,
                SUM(CASE WHEN grupo='NO_OBJETO' THEN neto ELSE 0 END) no_objeto,
                SUM(CASE WHEN grupo='SIN_DESGLOSE' THEN neto ELSE 0 END) sin_desglose,
                SUM(CASE WHEN grupo='REVISAR' THEN neto ELSE 0 END) revisar,
                SUM(CASE WHEN grupo='SIN_CONCEPTOS' THEN neto ELSE 0 END) sin_conceptos,SUM(iva) iva,
                SUM(CASE WHEN grupo IN ('EXENTO','TASA0','TASA16','OTRAS') THEN neto-base_iva
                    WHEN grupo='DIFERENCIA_CABECERA' THEN base_iva ELSE 0 END) diferencia
              FROM filas_resumen GROUP BY documento_id,naturaleza,moneda,sentido,tipo,estado_sat
            )
            SELECT naturaleza,moneda,sentido,tipo,estado_sat,COUNT(DISTINCT documento_id),SUM(neto),
              SUM(exento),SUM(tasa0),SUM(tasa16),SUM(otras),SUM(no_objeto),SUM(sin_desglose),
              SUM(revisar),SUM(sin_conceptos),SUM(iva),SUM(diferencia)
            FROM por_documento GROUP BY naturaleza,moneda,sentido,tipo,estado_sat
            ORDER BY naturaleza,moneda,sentido,tipo,estado_sat
            """;
        return new Resumen(numero(conteos[0]), numero(conteos[1]), filas(preparar.apply(sql)).stream()
                .map(r -> new GrupoResumen(texto(r[0]), texto(r[1]), texto(r[2]), texto(r[3]), texto(r[4]), numero(r[5]),
                        decimal(r[6]), decimal(r[7]), decimal(r[8]), decimal(r[9]), decimal(r[10]), decimal(r[11]),
                        decimal(r[12]), decimal(r[13]), decimal(r[14]), decimal(r[15]), decimal(r[16])))
                .toList());
    }

    private static long numero(Object valor) { return ((Number) valor).longValue(); }
    private static String texto(Object valor) { return valor == null ? null : valor.toString(); }
    private static BigDecimal decimal(Object valor) { return (BigDecimal) valor; }
    @SuppressWarnings("unchecked") private static List<Object[]> filas(Query query) { return query.getResultList(); }
}
