package com.kathsoft.visor.app.service.periodo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.kathsoft.visor.app.model.dto.FiltroPeriodoFiscal;
import com.kathsoft.visor.app.model.dto.MovimientoPeriodoFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * RF-006: consultas de periodos sin alterar documentos ni crear cierres.
 * La autorización RBAC y el alcance por contribuyente se evalúan en BD
 * en cada consulta. Los resultados son páginas acotadas a 200 filas.
 */
public final class PeriodosFiscalesService {
    private static final int MAX_PAGINA = 200;
    private final JpaContext jpa;

    /** @param jpa contexto de persistencia compartido */
    public PeriodosFiscalesService(JpaContext jpa) { this.jpa = jpa; }

    /** Contribuyente accesible para elegir en el filtro. */
    public record ContribuyentePeriodo(long id, String rfc, String nombre) {
        @Override public String toString() { return rfc + " — " + nombre; }
    }

    /**
     * Lista únicamente contribuyentes asignados explícitamente.
     * No utiliza el permiso global de administración de asignaciones como bypass.
     */
    public List<ContribuyentePeriodo> contribuyentes(Long operadorId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.PERIODOS_FISCALES_CONSULTAR);
            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createNativeQuery(
                "SELECT c.id, c.rfc, c.nombre_razon_social FROM contribuyente c "
                + "JOIN usuario_contribuyente uc ON uc.contribuyente_id=c.id "
                + "WHERE uc.usuario_id=?1 ORDER BY c.nombre_razon_social, c.id")
                .setParameter(1, operadorId).getResultList();
            return filas.stream().map(f -> new ContribuyentePeriodo(
                ((Number) f[0]).longValue(), (String) f[1], (String) f[2])).toList();
        });
    }

    /**
     * Consulta documentos por emisión, certificación o registro; para PAGO
     * devuelve una fila por nodo Pago (sin multiplicar por documentos relacionados).
     * @param operadorId cuenta activa
     * @param contribuyenteId contribuyente asignado
     * @param filtro intervalo y criterio
     * @param pagina índice de página desde cero
     * @return filas ordenadas por fecha e identificador, máximo 200
     */
    public List<MovimientoPeriodoFiscal> consultar(Long operadorId, long contribuyenteId,
            FiltroPeriodoFiscal filtro, int pagina) {
        if (filtro == null || pagina < 0 || pagina > Integer.MAX_VALUE / MAX_PAGINA) {
            throw new IllegalArgumentException("Filtro o página inválidos");
        }
        if (contribuyenteId <= 0) throw new IllegalArgumentException("Contribuyente inválido");
        try {
            return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.PERIODOS_FISCALES_CONSULTAR);
            exigirAlcance(em, operadorId, contribuyenteId);
            boolean pago = filtro.tipo() == FiltroPeriodoFiscal.TipoFechaFiscal.PAGO;
            String fecha = switch (filtro.tipo()) {
                case EMISION -> "d.fecha_emision";
                case CERTIFICACION -> "t.fecha_timbrado";
                case REGISTRO -> "d.registrado_en";
                case PAGO -> "p.fecha_pago";
            };
            String sql = "SELECT d.id, d.uuid, cd.sentido, c.tipo, " + fecha
                + (pago ? ", p.id, p.monto, p.moneda " : ", NULL, NULL, NULL ")
                + "FROM contribuyente_documento cd "
                + "JOIN documento_fiscal d ON d.id=cd.documento_id "
                + "LEFT JOIN cfdi c ON c.documento_id=d.id "
                + (pago ? "JOIN pago_fiscal p ON p.documento_id=d.id "
                        : "LEFT JOIN documento_timbre t ON t.documento_id=d.id ")
                + "WHERE cd.contribuyente_id=?1 AND " + fecha + ">=?2 AND " + fecha + "<?3 "
                + "ORDER BY " + fecha + ", d.id" + (pago ? ", p.id" : "");
            Query consulta = em.createNativeQuery(sql)
                .setParameter(1, contribuyenteId)
                .setParameter(2, Timestamp.valueOf(filtro.inicioInclusivo()))
                .setParameter(3, Timestamp.valueOf(filtro.finExclusivo()))
                .setFirstResult(pagina * MAX_PAGINA).setMaxResults(MAX_PAGINA);
            @SuppressWarnings("unchecked")
            List<Object[]> filas = consulta.getResultList();
            return filas.stream().map(PeriodosFiscalesService::mapearFila).toList();
            });
        } catch (RuntimeException er) {
            System.err.println("RF-006: falló la consulta de periodos del contribuyente "
                    + contribuyenteId + ", criterio " + filtro.tipo() + ", página " + pagina);
            er.printStackTrace(System.err);
            throw er;
        }
    }

    /**
     * Convierte una fila de consulta nativa a su DTO, conservando los tipos
     * monetarios y temporales sin transformaciones imprecisas.
     *
     * <p>Hibernate puede materializar un CHAR(1) como Character o String
     * según el dialecto y el driver JDBC. No debe realizarse un cast
     * directo a String para la columna cfdi.tipo.</p>
     *
     * @param f fila SQL con ocho posiciones del SELECT de periodos
     * @return movimiento tipado
     */
    static MovimientoPeriodoFiscal mapearFila(Object[] f) {
        if (f == null || f.length != 8) {
            throw new IllegalArgumentException("La fila SQL no contiene ocho columnas");
        }
        try {
            return new MovimientoPeriodoFiscal(
                    ((Number) f[0]).longValue(),
                    textoSql(f[1], "documento_fiscal.uuid"),
                    textoSql(f[2], "contribuyente_documento.sentido"),
                    textoSql(f[3], "cfdi.tipo"),
                    fecha(f[4]),
                    f[5] == null ? null : ((Number) f[5]).longValue(),
                    f[6] == null ? null : (BigDecimal) f[6],
                    textoSql(f[7], "pago_fiscal.moneda"));
        } catch (RuntimeException er) {
            throw new IllegalStateException(
                    "No fue posible convertir la fila de consulta para documento "
                    + (f[0] == null ? "desconocido" : f[0]), er);
        }
    }

    /**
     * Normaliza las representaciones JDBC de caracteres y textos.
     * Se rechazan tipos no esperados para no ocultar anomalías de mapeo.
     */
    private static String textoSql(Object valor, String columna) {
        if (valor == null) return null;
        if (valor instanceof String texto) return texto;
        if (valor instanceof Character caracter) return caracter.toString();
        throw new IllegalArgumentException("Tipo JDBC inesperado para " + columna
                + ": " + valor.getClass().getName());
    }

    private static LocalDateTime fecha(Object valor) {
        if (valor instanceof Timestamp t) return t.toLocalDateTime();
        if (valor instanceof LocalDateTime t) return t;
        throw new IllegalStateException("Fecha SQL inesperada: " + valor);
    }

    private static void exigirAlcance(EntityManager em, Long usuarioId, long contribuyenteId) {
        Number cantidad = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM usuario_contribuyente "
            + "WHERE usuario_id=?1 AND contribuyente_id=?2")
            .setParameter(1, usuarioId).setParameter(2, contribuyenteId).getSingleResult();
        if (cantidad.longValue() == 0) {
            throw new SecurityException("No existe asignación al contribuyente solicitado");
        }
    }
}
