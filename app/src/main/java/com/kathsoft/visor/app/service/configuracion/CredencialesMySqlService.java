package com.kathsoft.visor.app.service.configuracion;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;

/**
 * Almacena la contraseña de la conexión MySQL de forma recuperable, siguiendo
 * la estrategia de Kath ERP: archivo AES-256-GCM y clave aleatoria separada.
 *
 * <p>Los dos archivos residen bajo el perfil del usuario del sistema operativo.
 * En sistemas POSIX se aplican permisos 0700 al directorio y 0600 a ambos
 * archivos. Esta solución evita exposición accidental y manipulación sin
 * la clave, pero NO constituye una bóveda: quien lea ambos archivos puede
 * descifrar la contraseña. No respaldar ni sincronizar ambos archivos juntos.</p>
 *
 * <p>La identidad del servidor y el usuario están autenticados mediante AAD:
 * si cambian host, puerto, base o usuario, la contraseña no se puede reutilizar
 * sin introducirla y guardarla de nuevo.</p>
 */
public final class CredencialesMySqlService {

    private static final byte[] CABECERA = "VISDB001".getBytes(StandardCharsets.US_ASCII);
    private static final String NOMBRE_ARCHIVO = "database.credentials";
    private static final String NOMBRE_CLAVE = "connection.key";
    private static final int LONGITUD_CLAVE = 32;
    private static final int LONGITUD_NONCE = 12;
    private static final int LONGITUD_TAG = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Set<PosixFilePermission> PERMISOS_CARPETA = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> PERMISOS_ARCHIVO = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Path directorio;

    /** Almacenamiento aislado del directorio de trabajo y del repositorio Git. */
    public CredencialesMySqlService() {
        this(Path.of(System.getProperty("user.home"), ".visor-cfdi-xml"));
    }

    /**
     * Constructor para pruebas automatizadas con directorio temporal.
     *
     * @param directorio carpeta de configuración del perfil
     */
    public CredencialesMySqlService(Path directorio) {
        this.directorio = Objects.requireNonNull(directorio, "El directorio es obligatorio")
                .toAbsolutePath().normalize();
    }

    /** @return ruta fija del archivo de credenciales cifradas */
    public Path rutaArchivo() {
        return directorio.resolve(NOMBRE_ARCHIVO);
    }

    /** @return ruta del archivo de clave privada de instalación */
    public Path rutaClave() {
        return directorio.resolve(NOMBRE_CLAVE);
    }

    /**
     * Indica si existe una credencial cifrada. La validez se verifica al cargar.
     *
     * @return true si existe un archivo o enlace en la ruta reservada
     */
    public boolean existen() {
        return Files.exists(rutaArchivo(), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Descifra y autentica la contraseña vinculada al servidor configurado.
     *
     * @param configuracion parámetros no secretos de conexión
     * @return contraseña temporal, que el llamador debe borrar con Arrays.fill
     * @throws IOException si falta la clave, hay corrupción o no coincide el servidor
     */
    public char[] cargar(ConfiguracionSistema configuracion) throws IOException {
        Objects.requireNonNull(configuracion, "La configuración es obligatoria");
        revisarRutas();
        if (!Files.isRegularFile(rutaArchivo(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("No existe una contraseña MySQL guardada");
        }
        byte[] contenido = Files.readAllBytes(rutaArchivo());
        byte[] clave = null;
        byte[] plano = null;
        try {
            if (contenido.length < CABECERA.length + LONGITUD_NONCE + LONGITUD_TAG) {
                throw new IOException("Archivo de credenciales incompleto");
            }
            for (int i = 0; i < CABECERA.length; i++) {
                if (contenido[i] != CABECERA[i]) {
                    throw new IOException("Formato de credenciales no reconocido");
                }
            }
            clave = leerClave();
            byte[] nonce = Arrays.copyOfRange(contenido, CABECERA.length,
                    CABECERA.length + LONGITUD_NONCE);
            try {
                Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
                cifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave, "AES"),
                        new GCMParameterSpec(128, nonce));
                cifrador.updateAAD(datosAsociados(configuracion));
                plano = cifrador.doFinal(contenido, CABECERA.length + LONGITUD_NONCE,
                        contenido.length - CABECERA.length - LONGITUD_NONCE);
                return descodificar(plano);
            } finally {
                Arrays.fill(nonce, (byte) 0);
            }
        } catch (GeneralSecurityException | CharacterCodingException ex) {
            throw new IOException(
                    "No se pudo descifrar la contraseña: archivo alterado, clave inválida "
                            + "o configuración de MySQL diferente", ex);
        } finally {
            Arrays.fill(contenido, (byte) 0);
            if (clave != null) {
                Arrays.fill(clave, (byte) 0);
            }
            if (plano != null) {
                Arrays.fill(plano, (byte) 0);
            }
        }
    }

    /**
     * Sobrescribe el archivo cifrado con una contraseña probada previamente
     * contra MySQL. Nunca crea archivos temporales con contraseña legible.
     *
     * @param configuracion parámetros de conexión a los que vincular el secreto
     * @param contrasena clave temporal MySQL; el llamador conserva su limpieza
     * @throws IOException cuando no es posible proteger o escribir los archivos
     */
    public void guardar(ConfiguracionSistema configuracion, char[] contrasena) throws IOException {
        Objects.requireNonNull(configuracion, "La configuración es obligatoria");
        Objects.requireNonNull(contrasena, "La contraseña no puede ser nula");
        revisarRutas();
        Files.createDirectories(directorio);
        protegerCarpeta();
        revisarRutas();

        byte[] clave = obtenerOCrearClave();
        byte[] plano = null;
        byte[] cifrado = null;
        Path temporal = null;
        try {
            plano = codificar(contrasena);
            byte[] nonce = new byte[LONGITUD_NONCE];
            RANDOM.nextBytes(nonce);
            try {
                Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
                cifrador.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave, "AES"),
                        new GCMParameterSpec(128, nonce));
                cifrador.updateAAD(datosAsociados(configuracion));
                cifrado = cifrador.doFinal(plano);
                try (ByteArrayOutputStream destino = new ByteArrayOutputStream()) {
                    destino.write(CABECERA);
                    destino.write(nonce);
                    destino.write(cifrado);
                    temporal = crearTemporal();
                    Files.write(temporal, destino.toByteArray());
                    protegerArchivo(temporal);
                }
            } finally {
                Arrays.fill(nonce, (byte) 0);
            }
            if (Files.isSymbolicLink(rutaArchivo())) {
                throw new IOException("No se permiten enlaces simbólicos para credenciales");
            }
            try {
                Files.move(temporal, rutaArchivo(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporal, rutaArchivo(), StandardCopyOption.REPLACE_EXISTING);
            }
            temporal = null;
            protegerArchivo(rutaArchivo());
        } catch (GeneralSecurityException | CharacterCodingException ex) {
            throw new IOException("No fue posible cifrar la contraseña MySQL", ex);
        } finally {
            Arrays.fill(clave, (byte) 0);
            if (plano != null) {
                Arrays.fill(plano, (byte) 0);
            }
            if (cifrado != null) {
                Arrays.fill(cifrado, (byte) 0);
            }
            if (temporal != null) {
                Files.deleteIfExists(temporal);
            }
        }
    }

    private byte[] obtenerOCrearClave() throws IOException {
        if (Files.exists(rutaClave(), LinkOption.NOFOLLOW_LINKS)) {
            return leerClave();
        }
        if (existen()) {
            throw new IOException("Falta connection.key. No se reemplazará una clave perdida de forma automática");
        }
        byte[] nueva = new byte[LONGITUD_CLAVE];
        RANDOM.nextBytes(nueva);
        boolean creada = false;
        try {
            if (soportaPosix()) {
                Files.createFile(rutaClave(), PosixFilePermissions.asFileAttribute(PERMISOS_ARCHIVO));
            } else {
                Files.createFile(rutaClave());
            }
            creada = true;
            Files.write(rutaClave(), nueva);
            protegerArchivo(rutaClave());
            return nueva;
        } catch (FileAlreadyExistsException ex) {
            Arrays.fill(nueva, (byte) 0);
            return leerClave();
        } catch (IOException ex) {
            Arrays.fill(nueva, (byte) 0);
            if (creada) {
                try {
                    Files.deleteIfExists(rutaClave());
                } catch (IOException sup) {
                    ex.addSuppressed(sup);
                }
            }
            throw ex;
        }
    }

    private byte[] leerClave() throws IOException {
        revisarRutas();
        if (!Files.isRegularFile(rutaClave(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("No existe el archivo de clave connection.key");
        }
        byte[] clave = Files.readAllBytes(rutaClave());
        if (clave.length != LONGITUD_CLAVE) {
            Arrays.fill(clave, (byte) 0);
            throw new IOException("Archivo de clave de cifrado inválido");
        }
        return clave;
    }

    private void revisarRutas() throws IOException {
        if (Files.isSymbolicLink(directorio) || Files.isSymbolicLink(rutaArchivo())
                || Files.isSymbolicLink(rutaClave())) {
            throw new IOException("No se admiten enlaces simbólicos en las credenciales de MySQL");
        }
    }

    private Path crearTemporal() throws IOException {
        return soportaPosix()
                ? Files.createTempFile(directorio, ".database-", ".tmp",
                        PosixFilePermissions.asFileAttribute(PERMISOS_ARCHIVO))
                : Files.createTempFile(directorio, ".database-", ".tmp");
    }

    private boolean soportaPosix() throws IOException {
        return Files.getFileStore(directorio).supportsFileAttributeView("posix");
    }

    private void protegerCarpeta() throws IOException {
        if (soportaPosix()) {
            Files.setPosixFilePermissions(directorio, PERMISOS_CARPETA);
        }
    }

    private void protegerArchivo(Path ruta) throws IOException {
        if (soportaPosix()) {
            Files.setPosixFilePermissions(ruta, PERMISOS_ARCHIVO);
        }
    }

    private static byte[] datosAsociados(ConfiguracionSistema cfg) throws IOException {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
                DataOutputStream datos = new DataOutputStream(salida)) {
            datos.writeUTF("visor-cfdi-xml:mysql:v1");
            datos.writeUTF(cfg.host().toLowerCase(Locale.ROOT));
            datos.writeInt(cfg.puerto());
            datos.writeUTF(cfg.baseDatos());
            datos.writeUTF(cfg.usuario());
            datos.flush();
            return salida.toByteArray();
        }
    }

    private static byte[] codificar(char[] texto) throws CharacterCodingException {
        ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(texto));
        try {
            byte[] resultado = new byte[buffer.remaining()];
            buffer.get(resultado);
            return resultado;
        } finally {
            if (buffer.hasArray()) {
                Arrays.fill(buffer.array(), (byte) 0);
            }
        }
    }

    private static char[] descodificar(byte[] texto) throws CharacterCodingException {
        CharBuffer buffer = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(texto));
        try {
            char[] resultado = new char[buffer.remaining()];
            buffer.get(resultado);
            return resultado;
        } finally {
            if (buffer.hasArray()) {
                Arrays.fill(buffer.array(), '\0');
            }
        }
    }
}
