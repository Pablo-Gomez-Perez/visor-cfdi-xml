package com.kathsoft.visor.app.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Almacenamiento de contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria.
 * Sin dependencias de Spring. Nunca conserva contraseñas en texto plano.
 */
public final class PasswordHasher {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int ITERACIONES = 600_000;
    private static final int LONGITUD_BITS = 256;
    private static final int SAL_BYTES = 16;
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";

    private PasswordHasher() {
    }

    /**
     * Valida la contraseña antes de crear una cuenta.
     *
     * @param password caracteres entregados por el usuario
     */
    public static void validarNueva(char[] password) {
        if (password == null || password.length < 12 || password.length > 128) {
            throw new IllegalArgumentException("La contraseña debe tener entre 12 y 128 caracteres");
        }
    }

    /**
     * Produce un hash versionado con parámetros y sal individuales.
     *
     * @param password clave en memoria, el invocador debe borrarla al terminar
     * @return texto PBKDF2_SHA256$iteraciones$sal$hash
     */
    public static String crearHash(char[] password) {
        validarNueva(password);
        byte[] salt = new byte[SAL_BYTES];
        ALEATORIO.nextBytes(salt);
        byte[] derived = derivar(password, salt, ITERACIONES);
        try {
            return "PBKDF2_SHA256$" + ITERACIONES + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derived);
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    /**
     * Compara en tiempo constante el hash derivado con el hash registrado.
     *
     * @param password clave introducida
     * @param almacenado representación versionada obtenida de la BD
     * @return true si la contraseña es correcta
     */
    public static boolean verificar(char[] password, String almacenado) {
        if (password == null || almacenado == null) {
            return false;
        }
        try {
            String[] partes = almacenado.split("\\$", -1);
            if (partes.length != 4 || !"PBKDF2_SHA256".equals(partes[0])) {
                return false;
            }
            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones < 100_000 || iteraciones > 2_000_000) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (salt.length != SAL_BYTES || esperado.length != LONGITUD_BITS / 8) {
                return false;
            }
            byte[] real = derivar(password, salt, iteraciones);
            try {
                return MessageDigest.isEqual(real, esperado);
            } finally {
                Arrays.fill(real, (byte) 0);
                Arrays.fill(esperado, (byte) 0);
            }
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] derivar(char[] password, byte[] salt, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iteraciones, LONGITUD_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2 no está disponible en este JRE", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
