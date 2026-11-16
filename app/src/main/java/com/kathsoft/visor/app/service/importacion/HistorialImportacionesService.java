package com.kathsoft.visor.app.service.importacion;

import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * RF-013: historia inalterada de importaciones y archivado reversible por titular.
 * Nunca elimina XML, documentos fiscales, relaciones ni resultados de importación.
 */
public final class HistorialImportacionesService {
    private static final int TAMANO = 100;
    private final JpaContext jpa;

    /** Contexto compartido de persistencia. */
    public HistorialImportacionesService(JpaContext jpa) {
        this.jpa = Objects.requireNonNull(jpa);
    }

    /** Contadores históricos, independientes del estado operativo documental. */
    public record Lote(long id, long contribuyenteId, String rfc, String contribuyente,
            String usuario, LocalDateTime inicio, LocalDateTime fin, String estado,
            long aceptados, long duplicados, long rechazados, long errores, long archivados) { }

    /** Fila histórica; archivado representa el vínculo fiscal del titular, no el archivo físico. */
    public record Archivo(long id, int ordinal, String nombre, String ruta, String sha256,
            Long documentoId, String uuid, String resultado, String detalle, String estadoOperativo) { }

    /** Página de resultados con indicador de continuidad. */
    public record Pagina<T>(List<T> filas, boolean siguiente) {
        public Pagina { filas = List.copyOf(filas); }
    }

    /** Fechas civiles inclusivas. */
    public record Rango(LocalDate desde, LocalDate hasta) {
        public Rango {
            Objects.requireNonNull(desde, "Fecha inicial requerida");
            Objects.requireNonNull(hasta, "Fecha final requerida");
            if (desde.isAfter(hasta) || desde.getYear()<1000 || hasta.getYear()>9998)
                throw new IllegalArgumentException("Intervalo de fechas inválido");
        }
    }

    /** Estados originales del lote; no se confunden con archivado. */
    public enum EstadoLote { TODOS, EN_PROCESO, FINALIZADO, PARCIAL, ERROR }

    /** Cero significa todos los titulares/importadores autorizados. */
    public record Filtro(Rango rango, long contribuyente, long importador, EstadoLote estado) {
        public Filtro {
            Objects.requireNonNull(rango);
            Objects.requireNonNull(estado);
            if (contribuyente<0 || importador<0) throw new IllegalArgumentException("Filtro inválido");
        }
    }

    /** Opción de filtro identificada por clave, no por texto visible. */
    public record Opcion(long id, String etiqueta) {
        @Override public String toString() { return etiqueta; }
    }

    /** Catálogos restringidos a contribuyentes explícitamente asignados. */
    public record Opciones(List<Opcion> contribuyentes, List<Opcion> importadores) {
        public Opciones {
            contribuyentes=List.copyOf(contribuyentes);
            importadores=List.copyOf(importadores);
        }
    }

    /** Carga catálogos de filtro sin revelar importadores de otros titulares. */
    public Opciones opciones(Long operador) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em,operador,Permisos.IMPORTACIONES_CONSULTAR);
            List<Opcion> c=filas(em.createNativeQuery("""
                SELECT c.id,CONCAT(c.rfc,' — ',c.nombre_razon_social)
                FROM contribuyente c JOIN usuario_contribuyente uc ON uc.contribuyente_id=c.id
                WHERE uc.usuario_id=?1 ORDER BY c.nombre_razon_social,c.id
                """).setParameter(1,operador)).stream()
                .map(f->new Opcion(numero(f[0]),texto(f[1]))).toList();
            List<Opcion> u=filas(em.createNativeQuery("""
                SELECT DISTINCT u.id,u.login FROM lote_importacion l JOIN usuario u ON u.id=l.usuario_id
                JOIN usuario_contribuyente uc ON uc.contribuyente_id=l.contribuyente_id
                WHERE uc.usuario_id=?1 ORDER BY u.login,u.id
                """).setParameter(1,operador)).stream()
                .map(f->new Opcion(numero(f[0]),texto(f[1]))).toList();
            return new Opciones(c,u);
        });
    }

    /** Consulta por fecha, titular, importador y estado; todos son filtros SQL. */
    public Pagina<Lote> listar(Long usuario,Filtro filtro,int pagina) {
        Objects.requireNonNull(filtro);
        int offset=desplazamiento(pagina);
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em,usuario,Permisos.IMPORTACIONES_CONSULTAR);
            List<Object[]> datos=filas(em.createNativeQuery("""
                SELECT l.id,l.contribuyente_id,c.rfc,c.nombre_razon_social,u.login,l.inicio,l.fin,l.estado,
                   (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='ACEPTADO'),
                   (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='DUPLICADO'),
                   (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='RECHAZADO'),
                   (SELECT COUNT(*) FROM importacion_archivo a WHERE a.lote_id=l.id AND a.resultado='ERROR'),
                   (SELECT COUNT(DISTINCT a.documento_id) FROM importacion_archivo a
                    JOIN contribuyente_documento cd ON cd.documento_id=a.documento_id
                      AND cd.contribuyente_id=l.contribuyente_id
                    WHERE a.lote_id=l.id AND cd.archivado_en IS NOT NULL)
                FROM lote_importacion l JOIN contribuyente c ON c.id=l.contribuyente_id
                JOIN usuario u ON u.id=l.usuario_id
                JOIN usuario_contribuyente uc ON uc.contribuyente_id=l.contribuyente_id
                WHERE uc.usuario_id=?1 AND l.inicio>=?2 AND l.inicio<?3
                AND (?4=0 OR l.contribuyente_id=?4) AND (?5=0 OR l.usuario_id=?5)
                AND (?6='TODOS' OR l.estado=?6)
                ORDER BY l.inicio DESC,l.id DESC
                """).setParameter(1,usuario)
                .setParameter(2,Timestamp.valueOf(filtro.rango().desde().atStartOfDay()))
                .setParameter(3,Timestamp.valueOf(filtro.rango().hasta().plusDays(1).atStartOfDay()))
                .setParameter(4,filtro.contribuyente()).setParameter(5,filtro.importador())
                .setParameter(6,filtro.estado().name()).setFirstResult(offset).setMaxResults(TAMANO+1));
            return pagina(datos.stream().map(f->new Lote(numero(f[0]),numero(f[1]),texto(f[2]),texto(f[3]),
                    texto(f[4]),fecha(f[5]),fecha(f[6]),texto(f[7]),numero(f[8]),numero(f[9]),
                    numero(f[10]),numero(f[11]),numero(f[12]))).toList());
        });
    }

    /** Consulta sin filtros adicionales, conservada para consumidores existentes. */
    public Pagina<Lote> listar(Long usuario,Rango rango,int pagina) {
        return listar(usuario,new Filtro(rango,0,0,EstadoLote.TODOS),pagina);
    }

    /** Consulta archivos de un lote incluido su estado operativo actual. */
    public Pagina<Archivo> archivos(Long usuario,long lote,int pagina) {
        int offset=desplazamiento(pagina);
        return jpa.consultar(em -> {
            exigirDetalle(em,usuario);
            loteAutorizado(em,usuario,lote,false);
            List<Object[]> datos=filas(em.createNativeQuery("""
                SELECT a.id,a.ordinal,a.nombre_archivo,a.ruta_origen,HEX(a.sha256),
                    a.documento_id,d.uuid,a.resultado,a.detalle,
                    CASE WHEN cd.documento_id IS NULL THEN 'SIN_DOCUMENTO'
                         WHEN cd.archivado_en IS NULL THEN 'ACTIVO' ELSE 'ARCHIVADO' END
                FROM importacion_archivo a JOIN lote_importacion l ON l.id=a.lote_id
                JOIN usuario_contribuyente uc ON uc.contribuyente_id=l.contribuyente_id
                LEFT JOIN documento_fiscal d ON d.id=a.documento_id
                LEFT JOIN contribuyente_documento cd ON cd.documento_id=a.documento_id
                   AND cd.contribuyente_id=l.contribuyente_id
                WHERE a.lote_id=?1 AND uc.usuario_id=?2 ORDER BY a.ordinal,a.id
                """).setParameter(1,lote).setParameter(2,usuario)
                .setFirstResult(offset).setMaxResults(TAMANO+1));
            return pagina(datos.stream().map(f->new Archivo(numero(f[0]),((Number)f[1]).intValue(),
                texto(f[2]),texto(f[3]),texto(f[4]),f[5]==null?null:numero(f[5]),
                texto(f[6]),texto(f[7]),texto(f[8]),texto(f[9]))).toList());
        });
    }

    /** Estado actualizado del lote para el diálogo. */
    public record EstadoActual(String estado,LocalDateTime fin) { }

    /** Consulta estado actual sin depender de una instantánea visual anterior. */
    public EstadoActual estadoActual(Long usuario,long lote) {
        return jpa.consultar(em->{
            exigirDetalle(em,usuario);
            Object[] f=loteAutorizado(em,usuario,lote,false);
            return new EstadoActual(texto(f[1]),fecha(f[2]));
        });
    }

    /** Archiva todos los documentos activos del lote únicamente para su titular. */
    public int archivarLote(Long usuario,long lote,String motivo) {
        String razon=motivo(motivo);
        return jpa.enTransaccion(em->{
            AutorizacionService.exigir(em,usuario,Permisos.IMPORTACIONES_CONSULTAR);
            AutorizacionService.exigir(em,usuario,Permisos.IMPORTACIONES_ARCHIVAR_LOTE);
            long titular=titularCerrado(em,usuario,lote);
            // Bloquear vínculos antes de auditar y modificar. Una fila por UUID/titular.
            em.createNativeQuery("""
                SELECT cd.documento_id FROM contribuyente_documento cd
                WHERE cd.contribuyente_id=?1 AND cd.archivado_en IS NULL
                  AND EXISTS (SELECT 1 FROM importacion_archivo a
                    WHERE a.lote_id=?2 AND a.documento_id=cd.documento_id)
                ORDER BY cd.documento_id FOR UPDATE
                """).setParameter(1,titular).setParameter(2,lote).getResultList();
            int eventos=em.createNativeQuery("""
                INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo)
                SELECT ?1,cd.contribuyente_id,'CONTRIBUYENTE_DOCUMENTO',cd.documento_id,'ARCHIVAR',
                    CONCAT('RF-013 lote=',?2,'; ',?3)
                FROM contribuyente_documento cd
                WHERE cd.contribuyente_id=?4 AND cd.archivado_en IS NULL
                  AND EXISTS (SELECT 1 FROM importacion_archivo a
                    WHERE a.lote_id=?2 AND a.documento_id=cd.documento_id)
                """).setParameter(1,usuario).setParameter(2,lote).setParameter(3,razon)
                .setParameter(4,titular).executeUpdate();
            int cambios=em.createNativeQuery("""
                UPDATE contribuyente_documento cd SET cd.archivado_en=CURRENT_TIMESTAMP(6)
                WHERE cd.contribuyente_id=?1 AND cd.archivado_en IS NULL
                  AND EXISTS (SELECT 1 FROM importacion_archivo a
                    WHERE a.lote_id=?2 AND a.documento_id=cd.documento_id)
                """).setParameter(1,titular).setParameter(2,lote).executeUpdate();
            if(cambios!=eventos) throw new IllegalStateException("Cambios concurrentes; vuelva a consultar");
            return cambios;
        });
    }

    /** Archiva el vínculo fiscal de un documento seleccionado, sin tocar otras entidades. */
    public boolean archivarArchivo(Long usuario,long lote,long archivo,String motivo) {
        return cambiarArchivo(usuario,lote,archivo,motivo,false);
    }

    /** Restaura un vínculo archivado solo tras verificar el XML original por SHA-256. */
    public boolean restaurarArchivo(Long usuario,long lote,long archivo,String motivo) {
        return cambiarArchivo(usuario,lote,archivo,motivo,true);
    }

    private boolean cambiarArchivo(Long usuario,long lote,long archivo,String motivo,boolean restaurar) {
        String razon=motivo(motivo);
        if(archivo<=0) throw new IllegalArgumentException("Archivo inválido");
        return jpa.enTransaccion(em->{
            exigirDetalle(em,usuario);
            AutorizacionService.exigir(em,usuario,restaurar?Permisos.IMPORTACIONES_RESTAURAR_CFDI:
                    Permisos.IMPORTACIONES_ARCHIVAR_CFDI);
            long titular=titularCerrado(em,usuario,lote);
            List<Object[]> datos=filas(em.createNativeQuery("""
                SELECT cd.documento_id,cd.archivado_en
                FROM importacion_archivo a JOIN contribuyente_documento cd
                    ON cd.documento_id=a.documento_id AND cd.contribuyente_id=?1
                WHERE a.lote_id=?2 AND a.id=?3 FOR UPDATE
                """).setParameter(1,titular).setParameter(2,lote).setParameter(3,archivo));
            if(datos.isEmpty()) throw new IllegalArgumentException("Archivo ajeno al lote o sin documento fiscal");
            long doc=numero(datos.get(0)[0]);
            boolean archivado=datos.get(0)[1]!=null;
            if(restaurar != archivado) return false;
            if(restaurar) verificarOriginal(em,doc);
            String sql=restaurar
                ?"UPDATE contribuyente_documento SET archivado_en=NULL WHERE contribuyente_id=?1 AND documento_id=?2 AND archivado_en IS NOT NULL"
                :"UPDATE contribuyente_documento SET archivado_en=CURRENT_TIMESTAMP(6) WHERE contribuyente_id=?1 AND documento_id=?2 AND archivado_en IS NULL";
            int cambios=em.createNativeQuery(sql).setParameter(1,titular).setParameter(2,doc).executeUpdate();
            if(cambios!=1) throw new IllegalStateException("Estado concurrente del documento");
            em.createNativeQuery("""
                INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo)
                VALUES (?1,?2,'CONTRIBUYENTE_DOCUMENTO',?3,?4,?5)
                """).setParameter(1,usuario).setParameter(2,titular).setParameter(3,doc)
                .setParameter(4,restaurar?"RESTAURAR":"ARCHIVAR")
                .setParameter(5,"RF-013 lote="+lote+"; archivo="+archivo+"; "+razon).executeUpdate();
            return true;
        });
    }

    private static void verificarOriginal(EntityManager em,long doc) {
        List<Object[]> originales=filas(em.createNativeQuery("""
            SELECT rl.ruta_base,ad.clave_objeto,HEX(ad.sha256),ad.tamano_bytes
            FROM documento_archivo da JOIN archivo_documental ad ON ad.id=da.archivo_id
            JOIN repositorio_documental rd ON rd.id=ad.repositorio_id
            JOIN repositorio_local rl ON rl.repositorio_id=rd.id
            WHERE da.documento_id=?1 AND da.tipo_uso='ORIGINAL' AND rd.proveedor='LOCAL'
            """).setParameter(1,doc));
        if(originales.size()!=1) throw new IllegalStateException("Se requiere un único XML original local");
        Object[] f=originales.get(0);
        String sha=texto(f[2]).toLowerCase(java.util.Locale.ROOT);
        if(!sha.matches("[0-9a-f]{64}") || !("cfdi/"+sha.substring(0,2)+"/"+sha+".xml").equals(texto(f[1])))
            throw new IllegalStateException("Metadatos del original inconsistentes");
        try {
            Path raiz=Path.of(texto(f[0])).toRealPath();
            Path esperado=raiz.resolve(texto(f[1])).normalize();
            if(!esperado.startsWith(raiz) || !esperado.toRealPath().equals(esperado)
                    || !Files.isRegularFile(esperado,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Ruta del original no segura");
            long tamano=numero(f[3]);
            if(tamano<=0 || tamano>8L*1024*1024 || Files.size(esperado)!=tamano)
                throw new IllegalStateException("Tamaño del original incorrecto");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long leidos=0;
            try(var in=Files.newInputStream(esperado,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer=new byte[32768];
                for(int n;(n=in.read(buffer))!=-1;) {
                    leidos+=n;
                    if(leidos>tamano) throw new IllegalStateException("XML cambió durante la lectura");
                    digest.update(buffer,0,n);
                }
            }
            if(leidos!=tamano || !sha.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IllegalStateException("SHA-256 del original no coincide; restauración bloqueada");
        } catch(IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No fue posible verificar el XML original",ex);
        }
    }

    private static String motivo(String motivo) {
        if(motivo==null || motivo.isBlank() || motivo.strip().length()>200)
            throw new IllegalArgumentException("Indique un motivo de 1 a 200 caracteres");
        return motivo.strip();
    }

    private static void exigirDetalle(EntityManager em,Long usuario) {
        AutorizacionService.exigir(em,usuario,Permisos.IMPORTACIONES_CONSULTAR);
        AutorizacionService.exigir(em,usuario,Permisos.IMPORTACIONES_CONSULTAR_DETALLE);
    }

    private static long titularCerrado(EntityManager em,Long usuario,long lote) {
        Object[] f=loteAutorizado(em,usuario,lote,true);
        if(f[2]==null || "EN_PROCESO".equals(texto(f[1])))
            throw new IllegalStateException("El lote está en proceso");
        return numero(f[0]);
    }

    private static Object[] loteAutorizado(EntityManager em,Long usuario,long lote,boolean bloquear) {
        if(lote<=0) throw new IllegalArgumentException("Lote inválido");
        List<Object[]> datos=filas(em.createNativeQuery("""
            SELECT l.contribuyente_id,l.estado,l.fin
            FROM lote_importacion l JOIN usuario_contribuyente uc ON uc.contribuyente_id=l.contribuyente_id
            WHERE l.id=?1 AND uc.usuario_id=?2
            """+(bloquear?" FOR UPDATE":"")).setParameter(1,lote).setParameter(2,usuario));
        if(datos.isEmpty()) throw new SecurityException("Lote inexistente o no autorizado");
        return datos.get(0);
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
