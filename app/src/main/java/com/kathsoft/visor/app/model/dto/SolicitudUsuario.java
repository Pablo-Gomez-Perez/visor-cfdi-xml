package com.kathsoft.visor.app.model.dto;

/**
 * Información para registrar una cuenta; la contraseña es temporal.
 *
 * @param nombre nombre completo
 * @param login nombre de usuario
 * @param correo correo opcional
 * @param rol clave ADMIN o CONSULTA
 */
public record SolicitudUsuario(String nombre, String login, String correo, String rol) {
}
