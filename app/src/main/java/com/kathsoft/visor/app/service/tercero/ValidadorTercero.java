package com.kathsoft.visor.app.service.tercero;

import java.util.Locale;

import com.kathsoft.visor.app.model.dto.SolicitudTercero;

/**
 * Normaliza datos de contrapartes sin atribuirles verificación ante SAT.
 * Los RFC genéricos se aceptan porque pueden aparecer en CFDI.
 */
public final class ValidadorTercero {
    private ValidadorTercero() { }

    /** @param datos solicitud sin normalizar
     * @return datos consistentes con las longitudes del esquema
     */
    public static SolicitudTercero normalizar(SolicitudTercero datos) {
        if (datos == null) throw new IllegalArgumentException("Faltan datos del tercero");
        String nombre = obligatorio(datos.nombreRazonSocial(), 300, "Nombre o razón social");
        String rfc = opcional(datos.rfc(), 13, "RFC");
        if (rfc != null) {
            rfc = rfc.toUpperCase(Locale.ROOT);
            if (!rfc.matches("(?:[A-ZÑ&]{3}|[A-ZÑ&]{4})[0-9]{6}[A-Z0-9]{3}")) {
                throw new IllegalArgumentException("RFC con estructura incorrecta");
            }
        }
        String pais = opcional(datos.paisFiscal(), 3, "País fiscal");
        if (pais != null) {
            pais = pais.toUpperCase(Locale.ROOT);
            if (!pais.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("País fiscal: clave de tres letras");
            }
        }
        String extranjero = opcional(datos.numeroRegistroFiscalExtranjero(), 100,
                "Número de registro fiscal extranjero");
        if (rfc == null && extranjero == null) {
            throw new IllegalArgumentException(
                    "Debe indicar RFC o número de registro fiscal extranjero");
        }
        if (extranjero != null && pais == null) {
            throw new IllegalArgumentException(
                    "Indique país fiscal al capturar registro extranjero");
        }
        return new SolicitudTercero(rfc, nombre, pais, extranjero);
    }

    private static String obligatorio(String valor, int maximo, String campo) {
        String limpio = opcional(valor, maximo, campo);
        if (limpio == null) throw new IllegalArgumentException(campo + " es obligatorio");
        return limpio;
    }

    private static String opcional(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.strip();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(campo + " supera " + maximo + " caracteres");
        }
        return limpio;
    }
}
