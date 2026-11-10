package com.kathsoft.visor.app.service.contribuyente;

import java.time.LocalDate;
import java.util.Locale;

import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;

/**
 * Normalización técnica de campos capturados. La sintaxis RFC no certifica
 * existencia, inscripción, vigencia ni situación fiscal frente al SAT.
 */
public final class ValidadorContribuyente {

    private ValidadorContribuyente() {
    }

    /**
     * Normaliza y valida los campos de alta/edición, con RFC obligatorio.
     *
     * @param datos datos capturados
     * @return valores normalizados aptos para persistencia
     */
    public static SolicitudContribuyente normalizar(SolicitudContribuyente datos) {
        if (datos == null) {
            throw new IllegalArgumentException("Debe proporcionar los datos del contribuyente");
        }
        String rfc = normalizarRfc(datos.rfc());
        String nombre = datos.nombreRazonSocial() == null ? ""
                : datos.nombreRazonSocial().strip();
        if (nombre.isEmpty() || nombre.length() > 300) {
            throw new IllegalArgumentException("El nombre o razón social debe tener entre 1 y 300 caracteres");
        }
        String cp = nuloSiBlanco(datos.codigoPostalFiscal());
        if (cp != null && !cp.matches("[0-9]{5}")) {
            throw new IllegalArgumentException("El código postal fiscal debe contener 5 dígitos");
        }
        String correo = nuloSiBlanco(datos.correoContacto());
        if (correo != null) {
            correo = correo.toLowerCase(Locale.ROOT);
            if (correo.length() > 254
                    || !correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("El correo de contacto es inválido");
            }
        }
        return new SolicitudContribuyente(rfc, nombre, cp, correo);
    }

    /**
     * Valida exclusivamente la estructura del RFC (12 o 13 posiciones).
     * No realiza consulta al SAT ni verifica homoclave asignada.
     *
     * @param valor RFC introducido
     * @return RFC sin espacios exteriores y en mayúsculas
     */
    public static String normalizarRfc(String valor) {
        String rfc = valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT);
        if (!rfc.matches("(?:[A-ZÑ&]{3}|[A-ZÑ&]{4})[0-9]{6}[A-Z0-9]{3}")) {
            throw new IllegalArgumentException("RFC inválido: debe tener 12 o 13 caracteres con estructura fiscal");
        }
        return rfc;
    }

    /**
     * Valida la clave alfanumérica del régimen del catálogo SAT.
     *
     * @param clave código de régimen
     * @return código canónico
     */
    public static String normalizarRegimen(String clave) {
        String codigo = clave == null ? "" : clave.strip().toUpperCase(Locale.ROOT);
        if (!codigo.matches("[0-9]{3}")) {
            throw new IllegalArgumentException("La clave del régimen fiscal no es válida");
        }
        return codigo;
    }

    /**
     * Comprueba un intervalo de vigencia inclusivo.
     *
     * @param desde inicio requerido
     * @param hasta fin opcional
     */
    public static void validarVigencia(LocalDate desde, LocalDate hasta) {
        if (desde == null || (hasta != null && hasta.isBefore(desde))) {
            throw new IllegalArgumentException("La fecha inicial es obligatoria y no puede superar la final");
        }
    }

    private static String nuloSiBlanco(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.strip();
    }
}
