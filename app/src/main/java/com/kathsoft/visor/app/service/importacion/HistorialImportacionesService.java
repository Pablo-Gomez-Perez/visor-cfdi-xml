package com.kathsoft.visor.app.service.importacion;

import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * RF-013: consulta y eliminación controlada de importaciones por contribuyente.
 * Los XML del repositorio y sus metadatos nunca se eliminan aquí. Todas las
 * modificaciones, incluidos los eventos de auditoría, son una transacción.
 */
public final class HistorialImportacionesService {
    private static final int TAMANO = 100;
    private final JpaContext jpa;

    /** Contexto común con RF-007, sin conexiones independientes. */
    public HistorialImportacionesService(JpaContext jpa) {
        this.jpa = Objects.requireNonNull(jpa);
    }

    /** Identidad y contadores actuales de un lote, no inferidos del estado. */
    public record Lote(long id, long contribuyenteId, String rfc, String contribuyente,
            String usuario, LocalDateTime inicio, LocalDateTime fin, String estado,
            long aceptados, long duplicados, long rechazados, long errores) { }

    /** Fila del archivo (incluye archivos sin documento fiscal y diagnóstico). */
    public record Archivo(long id, int ordinal, String nombre, String ruta, String sha256,
            Long documentoId, String uuid, String resultado, String detalle) { }

    /** Página acotada de resultados con indicador de continuidad. */
    public record Pagina<T>(List<T> filas, boolean siguiente) {
        public Pagina { filas = List.copyOf(filas); }
    }

    /** Resultado confirmado; preservados incluye documentos compartidos o reimportados. */
    public record Eliminacion(long registros, long documentosEliminados, long documentosPreservados) { }

    /** Fechas civiles inclusive; el límite superior SQL es exclusivo. */
    public record Rango(LocalDate desde, LocalDate hasta) {
        public Rango {
            Objects.requireNonNull(desde, "Fecha inicial requerida");
            Objects.requireNonNull(hasta, "Fecha final requerida");
            if (desde.isAfter(hasta) || desde.getYear() < 1000 || hasta.getYear() > 9998)
                throw new IllegalArgumentException("Intervalo de fechas inválido");
        }
    }

    /** Consulta lotes por fecha de inicio y asignación efectiva del operador. */
    public Pagina<Lote> listar(Long usuario, Rango rango, int pagina) {
        int offset = desplazamiento(pagina);
        Objects.requireNonNull(rango);
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, usuario, Permisos.IMPORTACIONES_CONSULTAR);
            // Resultados acotados, orden estable y contadores sobre el índice lote/ordinal.
            List<Object[]> filas = filas(em.createNativeQuery("""
                SELECT l.id,l.contribuyente_id,c.rfc,c.nombre_razon_social,u.login,
                  l.inicio,l.fin,l.estado,
                  (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='ACEPTADO'),
                  (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='DUPLICADO'),
                  (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='RECHAZADO'),
                  (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='ERROR')
                FROM lote_importacion l
                JOIN contribuyente c ON c.id=l.contribuyente_id
                JOIN usuario u ON u.id=l.usuario_id
                JOIN usuario_contribuyente uc ON uc.contribuyente_id=l.contribuyente_id
                WHERE uc.usuario_id=?1 AND l.inicio>=?2 AND l.inicio<?3
                ORDER BY l.inicio DESC,l.id DESC
                """).setParameter(1,usuario)
                .setParameter(2,Timestamp.valueOf(rango.desde().atStartOfDay()))
                .setParameter(3,Timestamp.valueOf(rango.hasta().plusDays(1).atStartOfDay()))
                .setFirstResult(offset).setMaxResults(TAMANO+1));
            return pagina(filas.stream().map(f -> new Lote(numero(f[0]),numero(f[1]),
                    texto(f[2]),texto(f[3]),texto(f[4]),fecha(f[5]),fecha(f[6]),texto(f[7]),
                    numero(f[8]),numero(f[9]),numero(f[10]),numero(f[11]))).toList());
        });
    }

    /** Consulta los archivos de un lote autorizado, incluyendo errores y duplicados. */
    public Pagina<Archivo> archivos(Long usuario, long lote, int pagina) {
        int offset = desplazamiento(pagina);
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, usuario, Permisos.IMPORTACIONES_CONSULTAR);
            AutorizacionService.exigir(em, usuario, Permisos.IMPORTACIONES_CONSULTAR_DETALLE);
            loteAutorizado(em, usuario, lote, false);
            List<Object[]> filas = filas(em.createNativeQuery("""
                    SELECT a.id,a.ordinal,a.nombre_archivo,a.ruta_origen,HEX(a.sha256),
                           a.documento_id,d.uuid,a.resultado,a.detalle
                    FROM importacion_archivo a
                    JOIN lote_importacion l ON l.id=a.lote_id
                    JOIN usuario_contribuyente uc ON uc.contribuyente_id=l.contribuyente_id
                    LEFT JOIN documento_fiscal d ON d.id=a.documento_id
                    WHERE a.lote_id=?1 AND uc.usuario_id=?2
                    ORDER BY a.ordinal,a.id
                    """).setParameter(1,lote).setParameter(2,usuario)
                    .setFirstResult(offset).setMaxResults(TAMANO+1));
            return pagina(filas.stream().map(f -> new Archivo(numero(f[0]),((Number)f[1]).intValue(),
                    texto(f[2]),texto(f[3]),texto(f[4]),f[5]==null?null:numero(f[5]),
                    texto(f[6]),texto(f[7]),texto(f[8]))).toList());
        });
    }

    /** Elimina un lote cerrado completo, con sus registros y documentos exclusivos. */
    public Eliminacion eliminarLote(Long usuario, long lote, String motivo) {
        return eliminar(usuario,lote,null,motivo,Permisos.IMPORTACIONES_ELIMINAR_LOTE);
    }

    /** Elimina una fila concreta de un lote cerrado, nunca por UUID arbitrario. */
    public Eliminacion eliminarArchivo(Long usuario, long lote, long archivo, String motivo) {
        if (archivo<=0) throw new IllegalArgumentException("Archivo inválido");
        return eliminar(usuario,lote,archivo,motivo,Permisos.IMPORTACIONES_ELIMINAR_ARCHIVO);
    }

    private Eliminacion eliminar(Long usuario,long lote,Long archivo,String motivo,String permiso) {
        if (motivo==null || motivo.isBlank() || motivo.strip().length()>200)
            throw new IllegalArgumentException("Indique un motivo de 1 a 200 caracteres");
        String razon=motivo.strip();
        return jpa.enTransaccion(em -> {
            AutorizacionService.exigir(em,usuario,Permisos.IMPORTACIONES_CONSULTAR);
            AutorizacionService.exigir(em,usuario,permiso);
            if (archivo!=null) AutorizacionService.exigir(em,usuario,Permisos.IMPORTACIONES_CONSULTAR_DETALLE);
            Object[] titular=loteAutorizado(em,usuario,lote,true);
            long contribuyente=numero(titular[0]);
            if ("EN_PROCESO".equals(texto(titular[1])) || titular[2]==null)
                throw new IllegalStateException("No se puede eliminar un lote en proceso");
            String alcance="a.lote_id=:lote AND (:archivo=0 OR a.id=:archivo)";
            if (archivo!=null && seleccion(em,"SELECT a.id FROM importacion_archivo a WHERE "+alcance
                    +" FOR UPDATE",lote,archivo).getResultList().isEmpty())
                throw new IllegalArgumentException("El archivo no pertenece al lote");
            List<?> docs=seleccion(em,"SELECT a.documento_id FROM importacion_archivo a WHERE "
                    +alcance+" AND a.documento_id IS NOT NULL ORDER BY a.documento_id FOR UPDATE",
                    lote,archivo).getResultList();
            // RF-007 bloquea primero el lote y después documento_fiscal. Mantener ese orden.
            // Los bloqueos de documento se conservan hasta commit; cualquier fallo revierte todo.
            java.util.ArrayList<Long> eliminables=new java.util.ArrayList<>();
            long preservados=0;
            for (Object value:docs.stream().distinct().toList()) {
                long doc=numero(value);
                List<?> identidad=em.createNativeQuery("SELECT uuid FROM documento_fiscal WHERE id=?1 FOR UPDATE")
                        .setParameter(1,doc).getResultList();
                if (identidad.isEmpty()) throw new IllegalStateException("Documento desaparecido: "+doc);
                String uuid=texto(identidad.get(0));
                if (!existe(em,"SELECT documento_id FROM contribuyente_documento "
                        +"WHERE documento_id=?1 AND contribuyente_id=?2",doc,contribuyente))
                    throw new IllegalStateException("Documento sin vínculo con el titular del lote");
                // Cualquier otra importación o titular mantiene íntegra la identidad global.
                boolean compartido=existe(em,"SELECT documento_id FROM contribuyente_documento "
                        +"WHERE documento_id=?1 AND contribuyente_id<>?2",doc,contribuyente);
                String otras=archivo==null?"lote_id<>?2":"id<>?2";
                boolean otraImportacion=existe(em,"SELECT id FROM importacion_archivo WHERE documento_id=?1 AND "
                        +otras,doc,archivo==null?lote:archivo);
                if (compartido || otraImportacion) { preservados++; continue; }
                verificarDependencias(em,doc,uuid);
                eliminables.add(doc);
            }
            // Auditoría por fila, incluso si no hay UUID. El registro original completo
            // permanece como evidencia en bitacora_cambio.
            seleccion(em,"""
                    INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo)
                    SELECT :operador,l.contribuyente_id,'IMPORTACION_ARCHIVO',a.id,
                           'ELIMINAR_REGISTRO',LEFT(CONCAT('RF-013 operador=',:operador,' lote=',l.id,' motivo=',:motivo),600)
                    FROM importacion_archivo a JOIN lote_importacion l ON l.id=a.lote_id
                    WHERE """+" "+alcance,lote,archivo).setParameter("operador",usuario).setParameter("motivo",razon).executeUpdate();
            seleccion(em,"""
                    INSERT INTO bitacora_cambio(evento_id,atributo,valor_anterior,valor_nuevo)
                    SELECT e.id,'registro_importacion',
                       JSON_OBJECT('lote',a.lote_id,'ordinal',a.ordinal,'archivo',a.nombre_archivo,
                       'ruta',a.ruta_origen,'sha256',HEX(a.sha256),'documento_id',a.documento_id,
                       'uuid',d.uuid,'archivo_documental_id',a.archivo_documental_id,
                       'resultado',a.resultado,'detalle',a.detalle),NULL
                    FROM importacion_archivo a
                    JOIN bitacora_evento e ON e.entidad='IMPORTACION_ARCHIVO'
                      AND e.entidad_id=a.id AND e.accion='ELIMINAR_REGISTRO'
                    LEFT JOIN documento_fiscal d ON d.id=a.documento_id
                    WHERE NOT EXISTS (SELECT 1 FROM bitacora_cambio bc WHERE bc.evento_id=e.id AND bc.atributo='registro_importacion')
                    AND """+" "+alcance,lote,archivo).executeUpdate();
            int registros=seleccion(em,"DELETE a FROM importacion_archivo a WHERE "+alcance,lote,archivo).executeUpdate();
            for (int i=0;i<eliminables.size();i+=250) {
                borrarDocumentos(em,eliminables.subList(i,Math.min(i+250,eliminables.size())));
            }
            em.createNativeQuery("""
                    INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo)
                    VALUES (?1,?2,'LOTE_IMPORTACION',?3,?4,?5)
                    """).setParameter(1,usuario).setParameter(2,contribuyente).setParameter(3,lote)
                    .setParameter(4,archivo==null?"ELIMINAR_LOTE":"ELIMINAR_ARCHIVO")
                    .setParameter(5,"RF-013 "+razon+"; registros="+registros+"; eliminados="+eliminables.size()
                            +"; preservados="+preservados+"; originales conservados").executeUpdate();
            if (archivo==null) {
                em.createNativeQuery("""
                        INSERT INTO bitacora_cambio(evento_id,atributo,valor_anterior,valor_nuevo)
                        SELECT LAST_INSERT_ID(),'lote_importacion',
                           JSON_OBJECT('id',id,'contribuyente_id',contribuyente_id,'usuario_id',usuario_id,
                           'directorio_origen',directorio_origen,'inicio',inicio,'fin',fin,'estado',estado),NULL
                        FROM lote_importacion WHERE id=?1
                        """).setParameter(1,lote).executeUpdate();
                em.createNativeQuery("DELETE FROM lote_importacion WHERE id=?1").setParameter(1,lote).executeUpdate();
            }
            return new Eliminacion(registros,eliminables.size(),preservados);
        });
    }

    private static void verificarDependencias(EntityManager em,long doc,String uuid) {
        // No eliminar información administrativa ni evidencias de consultas SAT
        // y no romper referencias UUID de comprobantes previamente importados.
        String[] bloqueos={
            "SELECT id FROM obligacion WHERE documento_id=?1",
            "SELECT id FROM efecto_fiscal_periodo WHERE documento_id=?1",
            "SELECT id FROM ajuste_obligacion WHERE documento_soporte_id=?1",
            "SELECT cc.id FROM clasificacion_concepto cc JOIN cfdi_concepto c ON c.id=cc.concepto_id WHERE c.documento_id=?1",
            "SELECT ci.id FROM clasificacion_impuesto_concepto ci JOIN cfdi_concepto_impuesto i ON i.id=ci.impuesto_concepto_id JOIN cfdi_concepto c ON c.id=i.concepto_id WHERE c.documento_id=?1",
            "SELECT m.movimiento_pago_id FROM movimiento_pago_complemento m JOIN pago_fiscal_documento pd ON pd.id=m.pago_fiscal_documento_id JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE p.documento_id=?1",
            "SELECT id FROM consulta_estado_sat WHERE documento_id=?1",
            "SELECT archivo_id FROM documento_archivo WHERE documento_id=?1 AND tipo_uso<>'ORIGINAL'"
        };
        for (String sql:bloqueos) if (existe(em,sql,doc))
            throw new IllegalStateException("CFDI "+uuid+" tiene dependencias administrativas o evidencias protegidas; no se eliminó el lote");
        if (existe(em,"SELECT pd.id FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id "
                +"WHERE pd.uuid_documento=?1 AND p.documento_id<>?2",uuid,doc)
            || existe(em,"SELECT r.grupo_id FROM cfdi_relacion_uuid r JOIN cfdi_relacion_grupo g ON g.id=r.grupo_id "
                +"WHERE r.uuid_relacionado=?1 AND g.documento_id<>?2",uuid,doc)
            || existe(em,"SELECT documento_id FROM retencion_relacion_uuid WHERE uuid_relacionado=?1 AND documento_id<>?2",uuid,doc))
            throw new IllegalStateException("CFDI "+uuid+" está referenciado por otro documento; elimine primero la relación de forma controlada");
    }

    private static void borrarDocumentos(EntityManager em,List<Long> docs) {
        // Lista positiva de tablas derivadas del XML; nunca desactivar FK.
        // Una FK nueva/desconocida provoca rollback de toda la operación.
        String[] porHijo={
            "DELETE pp FROM cfdi_concepto_parte_pedimento pp JOIN cfdi_concepto_parte p ON p.id=pp.parte_id JOIN cfdi_concepto c ON c.id=p.concepto_id WHERE c.documento_id=?1",
            "DELETE t FROM cfdi_concepto_cuenta_tercero t JOIN cfdi_concepto c ON c.id=t.concepto_id WHERE c.documento_id=?1",
            "DELETE i FROM cfdi_concepto_impuesto i JOIN cfdi_concepto c ON c.id=i.concepto_id WHERE c.documento_id=?1",
            "DELETE p FROM cfdi_concepto_parte p JOIN cfdi_concepto c ON c.id=p.concepto_id WHERE c.documento_id=?1",
            "DELETE p FROM cfdi_concepto_pedimento p JOIN cfdi_concepto c ON c.id=p.concepto_id WHERE c.documento_id=?1",
            "DELETE u FROM cfdi_relacion_uuid u JOIN cfdi_relacion_grupo g ON g.id=u.grupo_id WHERE g.documento_id=?1",
            "DELETE s FROM nomina_otro_pago_subsidio s JOIN nomina_otro_pago o ON o.id=s.otro_pago_id WHERE o.documento_id=?1",
            "DELETE i FROM pago_fiscal_impuesto_documento i JOIN pago_fiscal_documento pd ON pd.id=i.pago_documento_id JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE p.documento_id=?1",
            "DELETE i FROM pago_fiscal_impuesto_pago i JOIN pago_fiscal p ON p.id=i.pago_fiscal_id WHERE p.documento_id=?1",
            "DELETE g FROM pago_fiscal_grupo_impuesto10 g JOIN pago_fiscal p ON p.id=g.pago_fiscal_id WHERE p.documento_id=?1",
            "DELETE pd FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE p.documento_id=?1"
        };
        for (String sql:porHijo) borrarGrupo(em,sql,docs);
        String[] porDocumento={"cfdi_concepto","cfdi_impuesto_global","cfdi_impuesto_totales",
            "cfdi_informacion_global","cfdi_relacion_grupo","nomina_deduccion","nomina_emisor",
            "nomina_incapacidad","nomina_otro_pago","nomina_percepcion","nomina_receptor","nomina",
            "pago_fiscal_total_impuesto","pago_fiscal_totales","pago_fiscal","pago_fiscal_complemento",
            "retencion_impuesto","retencion_periodo","retencion_receptor_extranjero",
            "retencion_relacion_uuid","retencion_totales","retencion",
            "documento_verificacion","documento_complemento","documento_participante","documento_timbre",
            "documento_archivo","contribuyente_documento","cfdi"};
        for (String tabla:porDocumento)
            borrarGrupo(em,"DELETE FROM "+tabla+" WHERE documento_id=?1",docs);
        borrarGrupo(em,"DELETE FROM documento_fiscal WHERE id=?1",docs);
    }

    private static void borrarGrupo(EntityManager em,String sql,List<Long> docs) {
        em.createNativeQuery(sql.replace("=?1"," IN (:documentos)"))
                .unwrap(org.hibernate.query.NativeQuery.class)
                .setParameterList("documentos",docs).executeUpdate();
    }

    private static Object[] loteAutorizado(EntityManager em,Long usuario,long lote,boolean bloquear) {
        if (lote<=0) throw new IllegalArgumentException("Lote inválido");
        List<Object[]> datos=filas(em.createNativeQuery("""
                SELECT l.contribuyente_id,l.estado,l.fin
                FROM lote_importacion l JOIN usuario_contribuyente uc
                  ON uc.contribuyente_id=l.contribuyente_id
                WHERE l.id=?1 AND uc.usuario_id=?2
                """+(bloquear?" FOR UPDATE":"")).setParameter(1,lote).setParameter(2,usuario));
        if (datos.isEmpty()) throw new SecurityException("Lote inexistente o no autorizado");
        return datos.get(0);
    }

    private static boolean existe(EntityManager em,String sql,Object... parametros) {
        Query q=em.createNativeQuery(sql+" LIMIT 1 FOR UPDATE");
        for(int i=0;i<parametros.length;i++) q.setParameter(i+1,parametros[i]);
        return !q.getResultList().isEmpty();
    }

    private static Query seleccion(EntityManager em,String sql,long lote,Long archivo) {
        Query q=em.createNativeQuery(sql).setParameter("lote",lote);
        q.setParameter("archivo",archivo==null?0L:archivo);
        return q;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> filas(Query q) { return q.getResultList(); }
    private static long numero(Object o) { return ((Number)o).longValue(); }
    private static String texto(Object o) { return o==null?null:o.toString(); }
    private static LocalDateTime fecha(Object o) {
        return o==null?null:o instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)o;
    }
    private static int desplazamiento(int pagina) {
        if(pagina<0 || pagina>Integer.MAX_VALUE/TAMANO) throw new IllegalArgumentException("Página inválida");
        return pagina*TAMANO;
    }
    private static <T> Pagina<T> pagina(List<T> filas) {
        return new Pagina<>(filas.subList(0,Math.min(TAMANO,filas.size())),filas.size()>TAMANO);
    }
}
