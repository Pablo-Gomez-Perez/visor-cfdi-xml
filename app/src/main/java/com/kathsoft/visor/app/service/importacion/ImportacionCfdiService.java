package com.kathsoft.visor.app.service.importacion;

import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DateTimeException;
import javax.xml.stream.XMLStreamException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.hibernate.Session;
import jakarta.persistence.EntityManager;

/**
 * RF-007: importación con lectura concurrente y escritura JDBC transaccional.
 *
 * <p>Máximo dos parsers, cuatro tareas pendientes y ocho documentos por lote.
 * No se construye una lista global de archivos ni se comparte EntityManager,
 * XMLStreamReader, Connection ni CallableStatement entre hilos. Los rechazos
 * documentales se registran individualmente; un fallo crítico revierte el lote
 * actual y los lotes previamente confirmados permanecen.</p>
 */
public final class ImportacionCfdiService {
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int HILOS = 2;
    private static final int EN_VUELO = 4;
    private static final int TAMANO_LOTE = 8;
    private static final String NOMBRE_REPOSITORIO = "CFDI_LOCAL_RF007";
    private final JpaContext jpa;
    private final Path repositorio;

    /** Destino único y administrado de los XML originales. */
    public ImportacionCfdiService(JpaContext jpa, Path repositorio) {
        this.jpa = Objects.requireNonNull(jpa);
        this.repositorio = Objects.requireNonNull(repositorio).toAbsolutePath().normalize();
    }

    /** Contribuyentes habilitados explícitamente para el operador. */
    public record ContribuyenteImportacion(long id, String rfc, String nombre) {
        @Override public String toString() { return rfc + " — " + nombre; }
    }

    /** Parámetros inmutables del lote, con archivos individuales y carpetas. */
    public record Solicitud(long contribuyenteId, List<Path> origenes, boolean incluirSubcarpetas) {
        public Solicitud {
            if (contribuyenteId <= 0 || origenes == null || origenes.isEmpty()) {
                throw new IllegalArgumentException("Seleccione contribuyente y archivos o carpetas");
            }
            origenes = List.copyOf(origenes);
        }
    }

    /** Datos compactos para notificar avance, sin retener todas las filas. */
    public record Avance(long encontrados, long procesados, long nuevos,
            long duplicados, long rechazados, String ultimo) { }

    /** Resumen de una importación completada. */
    public record Resultado(long loteId, long encontrados, long nuevos, long duplicados,
            long rechazados, String ultimoRechazo) { }

    private record Preparado(Path origen, Path temporal, String clave, String sha,
            long bytes, LectorCfdiXml.Documento documento, int ordinal) { }

    private record Tarea(Path origen, int ordinal, Future<Preparado> futuro) { }

    /** Fallo documental recuperable, identificado independientemente del worker. */
    private static final class RechazoXml extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String sha;
        RechazoXml(String detalle, String sha, Throwable causa) {
            super(detalle, causa);
            this.sha = sha;
        }
    }

    /** Lista contribuyentes asignados, sin dependencia del privilegio RF-006. */
    public List<ContribuyenteImportacion> contribuyentes(Long operadorId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CFDI_IMPORTAR);
            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createNativeQuery(
                    "SELECT c.id,c.rfc,c.nombre_razon_social FROM contribuyente c "
                    + "JOIN usuario_contribuyente uc ON uc.contribuyente_id=c.id "
                    + "WHERE uc.usuario_id=?1 AND c.activo=1 ORDER BY c.nombre_razon_social")
                    .setParameter(1, operadorId).getResultList();
            return filas.stream().map(r -> new ContribuyenteImportacion(
                    ((Number) r[0]).longValue(), (String) r[1], (String) r[2])).toList();
        });
    }

    /**
     * Recorre fuentes progresivamente, procesa XML en paralelo y confirma
     * grupos acotados usando sp_rf007_importar_cfdi sobre la conexión de JPA.
     * No bloquea el EDT: invocar únicamente desde SwingWorker.
     *
     * @throws RuntimeException error con ruta/causa, sin ocultar el rollback del lote
     */
    public Resultado importar(Long operadorId, Solicitud solicitud,
            Consumer<Avance> progreso, BooleanSupplier cancelar) {
        Objects.requireNonNull(progreso);
        Objects.requireNonNull(cancelar);
        String rfc = jpa.consultar(em -> {
            exigirAcceso(em, operadorId, solicitud.contribuyenteId());
            return (String) em.createNativeQuery(
                    "SELECT rfc FROM contribuyente WHERE id=?1 AND activo=1")
                    .setParameter(1, solicitud.contribuyenteId()).getSingleResult();
        });
        try {
            Files.createDirectories(repositorio);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo preparar el repositorio documental", ex);
        }
        long[] ids = crearLote(operadorId, solicitud.contribuyenteId());
        long lote = ids[0], repo = ids[1];
        long[] contadores = new long[5]; // encontrados, procesados, nuevos, duplicados, rechazados
        Path temporalDir = null;
        ExecutorService ejecutor = Executors.newFixedThreadPool(HILOS, r -> {
            Thread hilo = new Thread(r, "rf007-lector-xml");
            hilo.setDaemon(true);
            return hilo;
        });
        Deque<Tarea> cola = new ArrayDeque<>();
        List<Preparado> pendientes = new ArrayList<>(TAMANO_LOTE);
        final int[] ordinal = {0};
        try {
            temporalDir = Files.createTempDirectory(repositorio, ".rf007-staging-");
            final Path staging = temporalDir;
            Set<Path> recorridos = new HashSet<>();
            for (Path fuente : solicitud.origenes()) {
                verificarCancelacion(cancelar);
                Path normal = fuente.toAbsolutePath().normalize();
                if (!recorridos.add(normal)) continue;
                if (normal.startsWith(repositorio)) {
                    throw new IllegalArgumentException("La fuente no puede estar dentro del repositorio destino");
                }
                if (Files.isSymbolicLink(normal)) {
                    throw new IllegalArgumentException("No se permiten fuentes simbólicas: " + normal);
                }
                if (Files.isRegularFile(normal, LinkOption.NOFOLLOW_LINKS)) {
                    enviar(normal, staging, rfc, ejecutor, cola, pendientes, cancelar,
                            contadores, progreso, operadorId, solicitud.contribuyenteId(),
                            repo, lote, ordinal);
                } else if (Files.isDirectory(normal, LinkOption.NOFOLLOW_LINKS)) {
                    int profundidad = solicitud.incluirSubcarpetas()
                            ? Integer.MAX_VALUE : 1;
                    Files.walkFileTree(normal, Set.of(), profundidad,
                            new SimpleFileVisitor<>() {
                                @Override public FileVisitResult preVisitDirectory(
                                        Path dir, BasicFileAttributes attrs) {
                                    verificarCancelacion(cancelar);
                                    return dir.startsWith(repositorio)
                                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                                }
                                @Override public FileVisitResult visitFile(
                                        Path archivo, BasicFileAttributes attrs) {
                                    verificarCancelacion(cancelar);
                                    if (attrs.isRegularFile()) {
                                        enviar(archivo, staging, rfc, ejecutor, cola,
                                                pendientes, cancelar, contadores, progreso,
                                                operadorId, solicitud.contribuyenteId(),
                                                repo, lote, ordinal);
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                } else {
                    throw new IllegalArgumentException("No existe el archivo o carpeta: " + normal);
                }
            }
            while (!cola.isEmpty()) {
                retirar(cola, pendientes, cancelar, contadores, progreso,
                        operadorId, solicitud.contribuyenteId(), repo, lote);
            }
            confirmar(pendientes, cancelar, contadores, progreso,
                    operadorId, solicitud.contribuyenteId(), repo, lote);
            String ultimoRechazo = contadores[4] == 0 ? null : jpa.consultar(em -> {
                exigirAcceso(em, operadorId, solicitud.contribuyenteId());
                Object[] fila = (Object[]) em.createNativeQuery(
                    "SELECT nombre_archivo,detalle FROM importacion_archivo "
                    + "WHERE lote_id=?1 AND resultado='RECHAZADO' ORDER BY ordinal DESC")
                    .setParameter(1,lote).setMaxResults(1).getSingleResult();
                return fila[0] + ": " + fila[1];
            });
            finalizar(lote, contadores[4] == 0 ? "FINALIZADO" : "PARCIAL");
            return new Resultado(lote, contadores[0], contadores[2], contadores[3],
                    contadores[4], ultimoRechazo);
        } catch (IOException ex) {
            UncheckedIOException fallo = new UncheckedIOException(
                    "Error de archivos en importación " + lote, ex);
            registrarFallo(lote, contadores[1] > 0, fallo);
            throw fallo;
        } catch (RuntimeException ex) {
            registrarFallo(lote, contadores[1] > 0, ex);
            throw ex;
        } finally {
            ejecutor.shutdownNow();
            try {
                if (!ejecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Los lectores XML no terminaron al cancelar");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (temporalDir != null) limpiarTemporal(temporalDir);
            }
        }
    }

    private void enviar(Path archivo, Path staging, String rfc, ExecutorService ejecutor,
            Deque<Tarea> cola, List<Preparado> pendientes,
            BooleanSupplier cancelar, long[] contadores, Consumer<Avance> progreso,
            Long usuario, long contribuyente, long repo, long lote, int[] ordinal) {
        if (!archivo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")) return;
        if (ordinal[0] == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cantidad de archivos fuera de rango");
        }
        contadores[0]++;
        int numero = ++ordinal[0];
        cola.addLast(new Tarea(archivo, numero,
                ejecutor.submit(() -> preparar(archivo, staging, rfc, numero))));
        if (cola.size() >= EN_VUELO) {
            retirar(cola, pendientes, cancelar, contadores, progreso,
                    usuario, contribuyente, repo, lote);
        }
    }

    private void retirar(Deque<Tarea> cola, List<Preparado> pendientes,
            BooleanSupplier cancelar, long[] contadores, Consumer<Avance> progreso,
            Long usuario, long contribuyente, long repo, long lote) {
        verificarCancelacion(cancelar);
        Tarea tarea = cola.removeFirst();
        try {
            pendientes.add(tarea.futuro().get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Importación interrumpida");
        } catch (ExecutionException ex) {
            Throwable causa = ex.getCause();
            if (causa instanceof RechazoXml rechazo) {
                registrarRechazo(usuario, contribuyente, lote, tarea, rechazo);
                contadores[1]++;
                contadores[4]++;
                progreso.accept(new Avance(contadores[0],contadores[1],contadores[2],
                        contadores[3],contadores[4], "RECHAZADO: "
                        + tarea.origen().getFileName()));
            } else {
                if (causa instanceof Error error) throw error;
                throw new IllegalStateException("Error crítico procesando " + tarea.origen()
                        + ": " + causa.getMessage(), causa);
            }
        }
        if (pendientes.size() == TAMANO_LOTE) {
            confirmar(pendientes, cancelar, contadores, progreso,
                    usuario, contribuyente, repo, lote);
        }
    }

    private static Preparado preparar(Path archivo, Path staging, String rfc,
            int ordinal) throws Exception {
        if (archivo.getFileName().toString().length() > 260
                || archivo.toAbsolutePath().toString().length() > 1024) {
            throw new RechazoXml("Nombre o ruta origen fuera del límite MySQL", null, null);
        }
        long tamano = Files.size(archivo);
        if (tamano <= 0 || tamano > MAX_BYTES) {
            throw new RechazoXml("Tamaño XML fuera de rango (1 a 8 MiB)", null, null);
        }
        Path temporal = Files.createTempFile(staging, "cfdi-", ".tmp");
        boolean exito = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try (InputStream in = new DigestInputStream(Files.newInputStream(archivo), digest);
                    OutputStream out = Files.newOutputStream(temporal)) {
                byte[] buffer = new byte[32 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    bytes += n;
                    if (bytes > MAX_BYTES) {
                        throw new RechazoXml("XML excedió 8 MiB", null, null);
                    }
                    out.write(buffer, 0, n);
                }
            }
            String sha = HexFormat.of().formatHex(digest.digest());
            LectorCfdiXml.Documento doc;
            try {
                doc = new LectorCfdiXml().leer(temporal);
            } catch (IllegalArgumentException | DateTimeException | XMLStreamException ex) {
                throw new RechazoXml(DiagnosticoXml.describir(temporal) + " | Causa: "
                        + DiagnosticoXml.acotar(ex.getMessage(),400), sha, ex);
            }
            if (!rfc.equalsIgnoreCase(doc.emisorRfc())
                    && !rfc.equalsIgnoreCase(doc.receptorRfc())) {
                throw new RechazoXml(DiagnosticoXml.describir(temporal)
                        + " | RFC emisor/receptor ajeno al contribuyente", sha, null);
            }
            exito = true;
            return new Preparado(archivo, temporal,
                    "cfdi/" + sha.substring(0, 2) + "/" + sha + ".xml",
                    sha, bytes, doc, ordinal);
        } finally {
            if (!exito) Files.deleteIfExists(temporal);
        }
    }

    /** Persiste el rechazo sin tocar documentos fiscales ni originales. */
    private void registrarRechazo(Long usuario, long contribuyente, long lote,
            Tarea tarea, RechazoXml rechazo) {
        jpa.enTransaccion(em -> {
            exigirAcceso(em, usuario, contribuyente);
            em.createNativeQuery("INSERT INTO importacion_archivo "
                    + "(lote_id,ordinal,nombre_archivo,ruta_origen,sha256,resultado,detalle) "
                    + "VALUES (?1,?2,?3,?4,UNHEX(?5),'RECHAZADO',?6)")
                .setParameter(1,lote).setParameter(2,tarea.ordinal())
                .setParameter(3,DiagnosticoXml.acotar(tarea.origen().getFileName().toString(),260))
                .setParameter(4,DiagnosticoXml.acotar(tarea.origen().toAbsolutePath().toString(),1024))
                .setParameter(5,rechazo.sha)
                .setParameter(6,DiagnosticoXml.acotar(rechazo.getMessage(),1000))
                .executeUpdate();
            return null;
        });
    }

    private void confirmar(List<Preparado> pendientes, BooleanSupplier cancelar,
            long[] contadores, Consumer<Avance> progreso,
            Long usuario, long contribuyente, long repo, long lote) {
        if (pendientes.isEmpty()) return;
        verificarCancelacion(cancelar);
        // Preparar archivos antes de ocupar una conexión JDBC; si la BD
        // revierte, los objetos huérfanos podrán recolectarse después.
        for (Preparado p : pendientes) {
            verificarCancelacion(cancelar);
            archivar(p);
        }
        int[] duplicados = jpa.enTransaccion(em -> {
            exigirAcceso(em, usuario, contribuyente);
            int[] resultado = {0};
            em.unwrap(Session.class).doWork(connection -> {
                CallableStatement cfdi = null;
                CallableStatement retencion = null;
                try {
                    for (Preparado p : pendientes) {
                        verificarCancelacion(cancelar);
                        CallableStatement call;
                        if ("RETENCION".equals(p.documento().clase())) {
                            if (retencion == null) {
                                retencion = connection.prepareCall(
                                        "{call sp_rf007_importar_retencion(?,?,?,?,?,?,?,?,?,?)}");
                                retencion.registerOutParameter(10, Types.TINYINT);
                            }
                            call = retencion;
                        } else {
                            if (cfdi == null) {
                                cfdi = connection.prepareCall(
                                        "{call sp_rf007_importar_cfdi(?,?,?,?,?,?,?,?,?,?)}");
                                cfdi.registerOutParameter(10, Types.TINYINT);
                            }
                            call = cfdi;
                        }
                        call.setLong(1, lote);
                        call.setInt(2, p.ordinal());
                        call.setLong(3, repo);
                        call.setString(4, p.clave());
                        call.setString(5, p.sha());
                        call.setLong(6, p.bytes());
                        call.setString(7, p.origen().getFileName().toString());
                        call.setString(8, p.origen().toAbsolutePath().toString());
                        call.setString(9, p.documento().json());
                        try {
                            call.execute();
                            resultado[0] += call.getInt(10);
                        } catch (SQLException er) {
                            throw new SQLException("RF-007: fallo importando "
                                    + p.origen().getFileName() + " (ordinal "
                                    + p.ordinal() + ", clase " + p.documento().clase()
                                    + ", tipo " + p.documento().tipo() + ", UUID " + p.documento().uuid()
                                    + ", ruta " + p.origen() + ", "
                                    + DiagnosticoXml.describir(repositorio.resolve(p.clave())) + "): "
                                    + diagnosticarErrorProcedimiento(
                                            p.documento().tipo(), er.getMessage()),
                                    er.getSQLState(), er.getErrorCode(), er);
                        }
                    }
                } finally {
                    // Cada CallableStatement pertenece a este único hilo y
                    // a la misma conexión/transacción Hibernate.
                    if (retencion != null) retencion.close();
                    if (cfdi != null) cfdi.close();
                }
            });
            return resultado;
        });
        contadores[1] += pendientes.size();
        contadores[3] += duplicados[0];
        contadores[2] += pendientes.size() - duplicados[0];
        Preparado ultimo = pendientes.get(pendientes.size() - 1);
        pendientes.clear();
        progreso.accept(new Avance(contadores[0], contadores[1],
                contadores[2], contadores[3], contadores[4], ultimo.origen().getFileName().toString()));
    }

    /**
     * Añade una orientación operativa cuando una rutina antigua de RF-007
     * (que solo admitía I/E/T) rechaza un CFDI que ya superó el parser Java.
     *
     * <p>La pista no sustituye el error original: el mismo SIGNAL puede
     * tener otras causas y solo SHOW CREATE PROCEDURE permite confirmarlo.</p>
     *
     * @param tipo tipo fiscal previamente leído del XML
     * @param mensaje mensaje original enviado por MySQL
     * @return error conservado, con pasos de diagnóstico cuando corresponda
     */
    static String diagnosticarErrorProcedimiento(String tipo, String mensaje) {
        String sql = mensaje == null ? "Error SQL sin descripción" : mensaje;
        if (("P".equals(tipo) || "N".equals(tipo))
                && sql.contains("CFDI incompleto o version/tipo no admitido")) {
            return sql + ". Posible procedimiento MySQL RF-007 desactualizado: "
                    + "la versión inicial de sp_rf007_importar_cfdi solo admite I/E/T. "
                    + "Compruebe SHOW CREATE PROCEDURE visor_cfdi.sp_rf007_importar_cfdi "
                    + "y confirme que incluya I/E/T/P/N. Si no, instale la versión "
                    + "de RF-007 etapa 2 y los procedimientos auxiliares.";
        }
        return sql;
    }

    private void archivar(Preparado preparado) {
        Path destino = repositorio.resolve(preparado.clave()).normalize();
        if (!destino.startsWith(repositorio)) {
            throw new SecurityException("Ruta documental fuera del repositorio");
        }
        try {
            Files.createDirectories(destino.getParent());
            if (Files.exists(destino)) {
                // Un archivo previo puede estar corrupto; nunca sobrescribirlo.
                if (Files.size(destino) != preparado.bytes()
                        || !sha256(destino).equals(preparado.sha())) {
                    throw new IllegalStateException("Archivo del repositorio alterado: " + destino);
                }
                Files.deleteIfExists(preparado.temporal());
            } else {
                try {
                    Files.move(preparado.temporal(), destino);
                } catch (java.nio.file.FileAlreadyExistsException ex) {
                    if (Files.size(destino) != preparado.bytes()
                            || !sha256(destino).equals(preparado.sha())) throw ex;
                    Files.deleteIfExists(preparado.temporal());
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo guardar el XML original: " + destino, ex);
        }
    }

    private static String sha256(Path archivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(Files.newInputStream(archivo), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private long[] crearLote(Long operador, long contribuyente) {
        return jpa.enTransaccion(em -> {
            exigirAcceso(em, operador, contribuyente);
            em.createNativeQuery(
                    "INSERT IGNORE INTO repositorio_documental(nombre,proveedor) "
                    + "VALUES (?1,'LOCAL')")
                    .setParameter(1, NOMBRE_REPOSITORIO).executeUpdate();
            long repo = ((Number) em.createNativeQuery(
                    "SELECT id FROM repositorio_documental WHERE nombre=?1")
                    .setParameter(1, NOMBRE_REPOSITORIO).getSingleResult()).longValue();
            em.createNativeQuery(
                    "INSERT IGNORE INTO repositorio_local(repositorio_id,ruta_base) VALUES (?1,?2)")
                    .setParameter(1, repo).setParameter(2, repositorio.toString()).executeUpdate();
            String rutaRegistrada = (String) em.createNativeQuery(
                    "SELECT ruta_base FROM repositorio_local WHERE repositorio_id=?1")
                    .setParameter(1, repo).getSingleResult();
            if (!repositorio.toString().equals(rutaRegistrada)) {
                throw new IllegalStateException("La ruta del repositorio difiere de la base de datos");
            }
            em.createNativeQuery(
                    "INSERT INTO lote_importacion(contribuyente_id,usuario_id,estado) "
                    + "VALUES (?1,?2,'EN_PROCESO')")
                    .setParameter(1, contribuyente).setParameter(2, operador).executeUpdate();
            long lote = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()")
                    .getSingleResult()).longValue();
            return new long[] {lote, repo};
        });
    }

    private void registrarFallo(long lote, boolean parcial, RuntimeException original) {
        try {
            finalizar(lote, parcial ? "PARCIAL" : "ERROR");
        } catch (RuntimeException falloAuditoria) {
            original.addSuppressed(falloAuditoria);
        }
    }

    private void finalizar(long lote, String estado) {
        jpa.enTransaccion(em -> {
            em.createNativeQuery(
                    "UPDATE lote_importacion SET estado=?1, fin=CURRENT_TIMESTAMP(6) WHERE id=?2")
                    .setParameter(1, estado).setParameter(2, lote).executeUpdate();
            return null;
        });
    }

    private static void exigirAcceso(EntityManager em, Long operador, long contribuyente) {
        AutorizacionService.exigir(em, operador, Permisos.CFDI_IMPORTAR);
        Number n = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM usuario_contribuyente "
                + "WHERE usuario_id=?1 AND contribuyente_id=?2")
                .setParameter(1, operador).setParameter(2, contribuyente).getSingleResult();
        if (n.longValue() == 0) {
            throw new SecurityException("Sin asignación explícita al contribuyente");
        }
    }

    private static void verificarCancelacion(BooleanSupplier cancelar) {
        if (cancelar.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Importación cancelada por el usuario");
        }
    }

    private static void limpiarTemporal(Path carpeta) {
        try {
            Files.walkFileTree(carpeta, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path archivo, BasicFileAttributes attrs)
                        throws IOException {
                    Files.deleteIfExists(archivo);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException ex)
                        throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Archivos de staging huérfanos deben revisarse en mantenimiento.
        }
    }
}
