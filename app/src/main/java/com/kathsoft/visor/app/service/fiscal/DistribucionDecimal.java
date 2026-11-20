package com.kathsoft.visor.app.service.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reglas administrativas de distribución; nunca recalcula un impuesto original. */
public final class DistribucionDecimal {
    private DistribucionDecimal() { }

    /** Valida capacidad DECIMAL(20,6), no negatividad y suma menor o igual al disponible. */
    public static BigDecimal validar(BigDecimal disponible, List<BigDecimal> importes) {
        Objects.requireNonNull(disponible, "Importe original requerido");
        if (disponible.signum() < 0) throw new IllegalArgumentException("Importe distribuible negativo: requiere revisión");
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal importe : importes) {
            if (importe == null || importe.signum() < 0 || importe.stripTrailingZeros().scale() > 6
                    || importe.precision() - importe.scale() > 14)
                throw new IllegalArgumentException("Importe no negativo con máximo 14 enteros y 6 decimales requerido");
            total = total.add(importe);
        }
        if (total.compareTo(disponible) > 0) throw new IllegalArgumentException("La asignación supera el importe original");
        return disponible.subtract(total);
    }

    /**
     * Convierte proporciones [0,1] a importes a seis decimales por truncamiento.
     * Si suman uno, asigna el residuo al último renglón; si son parciales conserva el remanente.
     * Esta precisión administrativa no representa redondeo fiscal de una declaración.
     */
    public static List<BigDecimal> proporciones(BigDecimal original, List<BigDecimal> proporciones) {
        validar(original, List.of());
        BigDecimal suma = BigDecimal.ZERO;
        List<BigDecimal> resultado = new ArrayList<>();
        for (BigDecimal p : proporciones) {
            if (p == null || p.signum() < 0 || p.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("Proporción fuera de [0,1]");
            suma = suma.add(p);
            resultado.add(original.multiply(p).setScale(6, RoundingMode.DOWN));
        }
        if (suma.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("Las proporciones superan uno");
        if (suma.compareTo(BigDecimal.ONE) == 0 && !resultado.isEmpty()) {
            BigDecimal usado = resultado.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            int ultimo = resultado.size() - 1;
            resultado.set(ultimo, resultado.get(ultimo).add(original.subtract(usado)));
        }
        return List.copyOf(resultado);
    }
}
