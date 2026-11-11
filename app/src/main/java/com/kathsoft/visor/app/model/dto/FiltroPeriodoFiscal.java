package com.kathsoft.visor.app.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * RF-006: intervalo temporal de consulta, con fin exclusivo.
 * No representa un cierre fiscal ni determina causación de impuestos.
 */
public record FiltroPeriodoFiscal(LocalDate desde, LocalDate hasta, TipoFechaFiscal tipo) {
    /** Fechas documentales y movimientos de pago consultables. */
    public enum TipoFechaFiscal {
        EMISION("Emisión"), CERTIFICACION("Certificación"),
        PAGO("Pago"), REGISTRO("Registro");
        private final String etiqueta;
        TipoFechaFiscal(String etiqueta) { this.etiqueta = etiqueta; }
        @Override public String toString() { return etiqueta; }
    }

    /** Valida fechas y tipo, sin alterar la zona horaria del CFDI. */
    public FiltroPeriodoFiscal {
        Objects.requireNonNull(desde, "Fecha inicial requerida");
        Objects.requireNonNull(hasta, "Fecha final requerida");
        Objects.requireNonNull(tipo, "Tipo de fecha requerido");
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final precede a la inicial");
        }
        if (hasta.equals(LocalDate.MAX)) {
            throw new IllegalArgumentException("La fecha final excede el rango permitido");
        }
    }

    /** @return límite inferior inclusivo */
    public LocalDateTime inicioInclusivo() { return desde.atStartOfDay(); }
    /** @return límite superior exclusivo, conservando horas y microsegundos */
    public LocalDateTime finExclusivo() { return hasta.plusDays(1).atStartOfDay(); }

    /** Construye un ejercicio calendario. */
    public static FiltroPeriodoFiscal ejercicio(int anio, TipoFechaFiscal tipo) {
        if (anio < 1 || anio > 9998) throw new IllegalArgumentException("Ejercicio inválido");
        return new FiltroPeriodoFiscal(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31), tipo);
    }

    /** Construye un mes calendario. */
    public static FiltroPeriodoFiscal mes(int anio, int mes, TipoFechaFiscal tipo) {
        LocalDate inicio = LocalDate.of(anio, mes, 1);
        return new FiltroPeriodoFiscal(inicio, inicio.withDayOfMonth(inicio.lengthOfMonth()), tipo);
    }
}
