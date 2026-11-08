package com.kathsoft.visor.app.service.configuracion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;

/**
 * Persistencia local de parámetros no secretos mediante un archivo Properties.
 *
 * <p>Una futura integración de almacenamiento remoto deberá incorporarse en
 * una capa documental independiente. La contraseña de JDBC nunca se escribe.</p>
 */
public class ConfiguracionLocalService {

    private static final String ARCHIVO = "config.properties";
    private final Path archivo;

    /**
     * Utiliza una ubicación permanente independiente del directorio de trabajo.
     */
    public ConfiguracionLocalService() {
        this(Path.of(System.getProperty("user.home"), ".visor-cfdi-xml", ARCHIVO));
    }

    /**
     * Constructor inyectable para ejecutar pruebas sin alterar datos reales.
     *
     * @param archivo archivo donde se almacenará la configuración
     */
    public ConfiguracionLocalService(Path archivo) {
        this.archivo = Objects.requireNonNull(archivo, "La ruta es obligatoria")
                .toAbsolutePath().normalize();
        if (this.archivo.getParent() == null) {
            throw new IllegalArgumentException("La ruta de configuración requiere una carpeta");
        }
    }

    /**
     * Recupera los valores de conexión y almacenamiento ya guardados.
     *
     * @return configuración existente, o vacío durante la primera ejecución
     * @throws IOException si el archivo está dañado o no puede leerse
     */
    public Optional<ConfiguracionSistema> cargar() throws IOException {
        if (Files.notExists(archivo)) {
            return Optional.empty();
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
        }
        try {
            if (!"1".equals(propiedades.getProperty("version"))) {
                throw new IllegalArgumentException("Versión de configuración no soportada");
            }
            ConfiguracionSistema datos = new ConfiguracionSistema(
                    propiedades.getProperty("mysql.host"),
                    Integer.parseInt(propiedades.getProperty("mysql.port")),
                    propiedades.getProperty("mysql.database"),
                    propiedades.getProperty("mysql.user"),
                    Path.of(propiedades.getProperty("documentos.ruta")));
            return Optional.of(datos);
        } catch (RuntimeException ex) {
            throw new IOException("Los datos del archivo de configuración son inválidos", ex);
        }
    }

    /**
     * Guarda los parámetros de manera atómica cuando el sistema lo permite.
     *
     * @param datos configuración validada, sin contraseña
     * @throws IOException cuando el archivo no puede guardarse
     */
    public void guardar(ConfiguracionSistema datos) throws IOException {
        Objects.requireNonNull(datos, "La configuración es obligatoria");
        Properties propiedades = new Properties();
        propiedades.setProperty("version", "1");
        propiedades.setProperty("mysql.host", datos.host());
        propiedades.setProperty("mysql.port", Integer.toString(datos.puerto()));
        propiedades.setProperty("mysql.database", datos.baseDatos());
        propiedades.setProperty("mysql.user", datos.usuario());
        propiedades.setProperty("documentos.proveedor", "LOCAL");
        propiedades.setProperty("documentos.ruta", datos.directorioXml().toString());

        Files.createDirectories(archivo.getParent());
        Path temporal = Files.createTempFile(archivo.getParent(), ".config-", ".tmp");
        try {
            try (OutputStream salida = Files.newOutputStream(temporal)) {
                propiedades.store(salida, "Visor CFDI XML - RF-001");
            }
            try {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    /**
     * Crea y comprueba el directorio documental sin mover ni borrar XML.
     *
     * @param ruta directorio elegido para el repositorio documental
     * @throws IOException si el directorio no admite creación o escritura
     */
    public void prepararRepositorio(Path ruta) throws IOException {
        Files.createDirectories(ruta);
        if (!Files.isDirectory(ruta)) {
            throw new IOException("La ruta de documentos no es un directorio");
        }
        Path prueba = Files.createTempFile(ruta, ".verificar-", ".tmp");
        Files.deleteIfExists(prueba);
    }

    /**
     * @return ruta del archivo local de configuración
     */
    public Path getArchivo() {
        return archivo;
    }
}
