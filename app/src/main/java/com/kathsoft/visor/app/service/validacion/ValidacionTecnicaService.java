package com.kathsoft.visor.app.service.validacion;

import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
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

/**
 * RF-008: búsqueda progresiva y validación técnica concurrente de XML.
 * El usuario necesita permiso CFDI.VALIDAR_TECNICAMENTE; no se introducen
 * archivos no verificados en la BD. La verificación es de solo lectura.
 *
 * <p>Máximo dos workers y cuatro Future en memoria; los resultados se emiten
 * al consumidor sin acumular una colección global de XML.</p>
 */
public final class ValidacionTecnicaService {
    private static final int HILOS = 2;
    private static final int EN_VUELO = 4;
    private static final int MAX_ARCHIVOS = 50_000;
    private final JpaContext jpa;
    private final ValidadorXsdSat validador;

    /** Configura esquemas SAT locales e infraestructura de autorización. */
    public ValidacionTecnicaService(JpaContext jpa, Path raizXsd) {
        this.jpa = Objects.requireNonNull(jpa);
        this.validador = new ValidadorXsdSat(raizXsd);
    }

    /** Ubicación predeterminada, configurable sin modificación del código. */
    public static Path carpetaEsquemas() {
        String configurada = System.getProperty("visor.xsd.dir");
        return configurada == null || configurada.isBlank()
                ? Path.of(System.getProperty("user.home"), ".visor-cfdi", "esquemas-sat")
                : Path.of(configurada);
    }

    /** Parámetros de la ejecución. */
    public record Solicitud(List<Path> origenes, boolean subcarpetas) {
        public Solicitud {
            if (origenes == null || origenes.isEmpty()) {
                throw new IllegalArgumentException("Seleccione al menos un XML o carpeta");
            }
            origenes = List.copyOf(origenes);
        }
    }

    /** Estadísticas terminales de la ejecución. */
    public record Resumen(int total, int validos, int invalidos, int incompatibles,
            int sinEsquema, int errores) { }

    /**
     * Recorre un conjunto de orígenes y transmite los resultados individuales
     * a medida que los obtiene. El callback se llama desde el hilo de trabajo
     * y debe publicar al EDT por medio de SwingWorker.publish().
     *
     * @param operadorId identidad autenticada
     * @param solicitud fuentes no confiables
     * @param receptor consumidor de cada resultado
     * @param cancelar cancelación cooperativa
     * @return contadores de resultados
     */
    public Resumen validar(Long operadorId, Solicitud solicitud,
            Consumer<ValidadorXsdSat.Resultado> receptor,
            BooleanSupplier cancelar) {
        Objects.requireNonNull(solicitud);
        Objects.requireNonNull(receptor);
        Objects.requireNonNull(cancelar);
        jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CFDI_VALIDAR_TECNICAMENTE);
            return null;
        });
        ExecutorService pool = Executors.newFixedThreadPool(HILOS, r -> {
            Thread hilo = new Thread(r, "rf008-validador-xsd");
            hilo.setDaemon(true);
            return hilo;
        });
        Deque<Future<ValidadorXsdSat.Resultado>> cola = new ArrayDeque<>();
        int[] contadores = new int[6]; // total, valido, invalido, incompatible, sin_esquema, error
        try {
            Set<Path> origenes = new HashSet<>();
            for (Path origen : solicitud.origenes()) {
                verificar(cancelar);
                Path ruta = origen.toAbsolutePath().normalize();
                if (!origenes.add(ruta)) continue;
                if (Files.isSymbolicLink(ruta)) {
                    throw new IllegalArgumentException("No se admiten enlaces simbólicos: " + ruta);
                }
                if (Files.isRegularFile(ruta, LinkOption.NOFOLLOW_LINKS)) {
                    enviar(ruta, pool, cola, contadores, receptor, cancelar);
                } else if (Files.isDirectory(ruta, LinkOption.NOFOLLOW_LINKS)) {
                    Files.walkFileTree(ruta, Set.of(),
                            solicitud.subcarpetas() ? Integer.MAX_VALUE : 1,
                            new SimpleFileVisitor<>() {
                                @Override public FileVisitResult preVisitDirectory(
                                        Path dir, BasicFileAttributes attrs) {
                                    verificar(cancelar);
                                    return FileVisitResult.CONTINUE;
                                }
                                @Override public FileVisitResult visitFile(
                                        Path file, BasicFileAttributes attrs) {
                                    verificar(cancelar);
                                    if (attrs.isRegularFile()) {
                                        enviar(file, pool, cola, contadores, receptor, cancelar);
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                } else {
                    throw new IllegalArgumentException("Ruta no disponible: " + ruta);
                }
            }
            while (!cola.isEmpty()) {
                recibir(cola.removeFirst(), contadores, receptor, cancelar);
            }
            return new Resumen(contadores[0], contadores[1], contadores[2],
                    contadores[3], contadores[4], contadores[5]);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            throw new UncheckedIOException("Error recorriendo los XML", ex);
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            throw ex;
        } finally {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("RF-008: la cancelación de validadores excedió 30 segundos");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void enviar(Path ruta, ExecutorService pool,
            Deque<Future<ValidadorXsdSat.Resultado>> cola, int[] contadores,
            Consumer<ValidadorXsdSat.Resultado> receptor, BooleanSupplier cancelar) {
        if (!ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")) return;
        verificar(cancelar);
        if (contadores[0] == MAX_ARCHIVOS) {
            throw new IllegalArgumentException("Se excedió el máximo de 50,000 archivos");
        }
        contadores[0]++;
        cola.addLast(pool.submit(() -> validador.validar(ruta)));
        if (cola.size() >= EN_VUELO) {
            recibir(cola.removeFirst(), contadores, receptor, cancelar);
        }
    }

    private static void recibir(Future<ValidadorXsdSat.Resultado> futuro, int[] contadores,
            Consumer<ValidadorXsdSat.Resultado> receptor, BooleanSupplier cancelar) {
        verificar(cancelar);
        try {
            ValidadorXsdSat.Resultado resultado = futuro.get();
            switch (resultado.estado()) {
                case VALIDO -> contadores[1]++;
                case INVALIDO -> contadores[2]++;
                case INCOMPATIBLE -> contadores[3]++;
                case SIN_ESQUEMA -> contadores[4]++;
                case ERROR -> contadores[5]++;
            }
            receptor.accept(resultado);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Verificación técnica interrumpida");
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Error inesperado en validador XSD", ex.getCause());
        }
    }

    private static void verificar(BooleanSupplier cancelar) {
        if (Thread.currentThread().isInterrupted() || cancelar.getAsBoolean()) {
            throw new CancellationException("Verificación técnica cancelada");
        }
    }
}
