package com.kathsoft.visor.app.model.configuracion;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Parámetros públicos de configuración local.
 *
 * <p>No almacena contraseñas. La ubicación de documentos define el
 * repositorio local, no el periodo fiscal de cada CFDI.</p>
 *
 * @param host nombre o dirección del servidor MySQL
 * @param puerto puerto TCP del servidor MySQL
 * @param baseDatos esquema MySQL existente
 * @param usuario usuario de base de datos
 * @param directorioXml carpeta administrada para los documentos originales
 */
public record ConfiguracionSistema(
        String host, int puerto, String baseDatos, String usuario, Path directorioXml) {

    /**
     * Valida y normaliza los datos introducidos en el asistente.
     */
    public ConfiguracionSistema {
        host = Objects.requireNonNull(host, "El servidor es obligatorio").trim();
        baseDatos = Objects.requireNonNull(baseDatos, "La base de datos es obligatoria").trim();
        usuario = Objects.requireNonNull(usuario, "El usuario es obligatorio").trim();
        directorioXml = Objects.requireNonNull(directorioXml, "La carpeta de XML es obligatoria")
                .toAbsolutePath().normalize();

        if (host.isBlank() || !host.matches("[A-Za-z0-9.\\[\\]:_-]+")) {
            throw new IllegalArgumentException("Introduzca un servidor MySQL válido");
        }
        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535");
        }
        if (!baseDatos.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("El nombre de la base de datos solo admite letras, números y _");
        }
        if (usuario.isBlank()) {
            throw new IllegalArgumentException("El usuario de MySQL es obligatorio");
        }
    }
}
