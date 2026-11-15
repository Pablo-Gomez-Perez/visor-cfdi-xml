package com.kathsoft.visor.app.service.clasificacion;

import com.kathsoft.visor.app.model.dto.DocumentoClasificado;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal;
import com.kathsoft.visor.app.model.dto.FiltroClasificacionFiscal.Sentido;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.clasificacion.ClasificadorDocumentoFiscal.Categoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RF-009: consulta paginada de clasificación de CFDI y retenciones existentes.
 * No reinterpreta XML ni escribe sobre tablas fiscales. Cada consulta comprueba
 * permiso efectivo y vínculo usuario_contribuyente contra MySQL.
 */
public final class ClasificacionCfdiService {
    /** Tamaño máximo de cada página en consultas de catálogo. */
    public static final int TAMANO_PAGINA = 100;
    private static final int ULTIMA_PAGINA = 100_000;

    private static final String CFDI_CONSISTENTE =
            "(d.clase='CFDI' AND c.documento_id IS NOT NULL "
            + "AND r.documento_id IS NULL AND c.tipo IN ('I','E','T','N','P'))";
    private static final String RETENCION_CONSISTENTE =
            "(d.clase='RETENCION' AND r.documento_id IS NOT NULL "
            + "AND c.documento_id IS NULL)";

    private final JpaContext jpa;

    /** @param jpa infraestructura JPA compartida, no administrada por Swing */
    public ClasificacionCfdiService(JpaContext jpa) {
        this.jpa = Objects.requireNonNull(jpa, "Contexto JPA requerido");
    }

    /** Contribuyente asignado al usuario autenticado. */
    public record ContribuyenteDisponible(long id, String rfc, String nombre) {
        @Override
        public String toString() {
            return rfc + " — " + nombre;
        }
    }

    /** Página acotada con bandera de continuidad sin consulta COUNT global. */
    public record Pagina(List<DocumentoClasificado> documentos, boolean tieneSiguiente,
            int indice) {
        public Pagina {
            documentos = List.copyOf(documentos);
        }
    }

    /**
     * Obtiene exclusivamente titulares vinculados al usuario para seleccionar
     * el alcance de clasificación; no confiere acceso global por ser ADMIN.
     *
     * @param operadorId usuario activo
     * @return titulares accesibles
     */
    public List<ContribuyenteDisponible> contribuyentes(Long operadorId) {
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CFDI_CONSULTAR_CLASIFICACION);
            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createNativeQuery(
                    "SELECT c.id,c.rfc,c.nombre_razon_social FROM contribuyente c "
                    + "JOIN usuario_contribuyente uc ON uc.contribuyente_id=c.id "
                    + "WHERE uc.usuario_id=:usuario "
                    + "ORDER BY c.nombre_razon_social,c.id")
                    .setParameter("usuario", operadorId).getResultList();
            return filas.stream().map(f -> new ContribuyenteDisponible(
                    ((Number) f[0]).longValue(), texto(f[1], "contribuyente.rfc"),
                    texto(f[2], "contribuyente.nombre_razon_social"))).toList();
        });
    }

    /**
     * Consulta documentos sin duplicarlos por conceptos, pagos o complementos.
     * Los filtros de tipo son condiciones SQL fijas; ningún texto ingresado
     * por la interfaz se concatena en el SQL.
     *
     * @param operadorId usuario de aplicación
     * @param contribuyenteId titular asignado explícitamente
     * @param filtro categoría, dirección y prefijo del UUID
     * @param pagina índice desde cero
     * @return máximo 100 documentos y si existe página siguiente
     */
    public Pagina consultar(Long operadorId, long contribuyenteId,
            FiltroClasificacionFiscal filtro, int pagina) {
        Objects.requireNonNull(filtro, "Filtro requerido");
        if (contribuyenteId <= 0 || pagina < 0 || pagina > ULTIMA_PAGINA) {
            throw new IllegalArgumentException("Contribuyente o página inválidos");
        }
        return jpa.consultar(em -> {
            AutorizacionService.exigir(em, operadorId, Permisos.CFDI_CONSULTAR_CLASIFICACION);
            exigirAlcance(em, operadorId, contribuyenteId);
            StringBuilder sql = new StringBuilder(
                    "SELECT d.id,d.uuid,d.clase,c.tipo,r.clave_retencion,"
                    + "cd.sentido,d.fecha_emision,emi.rfc,rec.rfc,"
                    + "c.documento_id,r.documento_id "
                    + "FROM contribuyente_documento cd "
                    + "JOIN documento_fiscal d ON d.id=cd.documento_id "
                    + "LEFT JOIN cfdi c ON c.documento_id=d.id "
                    + "LEFT JOIN retencion r ON r.documento_id=d.id "
                    + "LEFT JOIN documento_participante emi "
                    + "ON emi.documento_id=d.id AND emi.rol='E' "
                    + "LEFT JOIN documento_participante rec "
                    + "ON rec.documento_id=d.id AND rec.rol='R' "
                    + "WHERE cd.contribuyente_id=:contribuyente ");

            switch (filtro.categoria()) {
                case TODAS -> { }
                case RETENCION -> sql.append("AND ").append(RETENCION_CONSISTENTE).append(' ');
                case INCONSISTENTE -> sql.append("AND NOT (")
                        .append(CFDI_CONSISTENTE).append(" OR ")
                        .append(RETENCION_CONSISTENTE).append(") ");
                default -> sql.append("AND ").append(CFDI_CONSISTENTE)
                        .append(" AND c.tipo=:tipo ");
            }
            if (filtro.sentido() != Sentido.TODOS) {
                sql.append("AND cd.sentido=:sentido ");
            }
            if (!filtro.prefijoUuid().isEmpty()) {
                sql.append("AND d.uuid LIKE :uuid ");
            }
            sql.append("ORDER BY d.fecha_emision DESC,d.id DESC");
            Query consulta = em.createNativeQuery(sql.toString())
                    .setParameter("contribuyente", contribuyenteId);
            if (filtro.categoria() != Categoria.TODAS
                    && filtro.categoria() != Categoria.RETENCION
                    && filtro.categoria() != Categoria.INCONSISTENTE) {
                consulta.setParameter("tipo", tipoFiscal(filtro.categoria()));
            }
            if (filtro.sentido() != Sentido.TODOS) {
                consulta.setParameter("sentido", filtro.sentido().name());
            }
            if (!filtro.prefijoUuid().isEmpty()) {
                consulta.setParameter("uuid", filtro.prefijoUuid() + "%");
            }
            consulta.setFirstResult(pagina * TAMANO_PAGINA);
            consulta.setMaxResults(TAMANO_PAGINA + 1);
            @SuppressWarnings("unchecked")
            List<Object[]> resultados = consulta.getResultList();
            List<DocumentoClasificado> documentos = new ArrayList<>(
                    Math.min(resultados.size(), TAMANO_PAGINA));
            for (int i = 0; i < Math.min(TAMANO_PAGINA, resultados.size()); i++) {
                documentos.add(mapear(resultados.get(i)));
            }
            return new Pagina(documentos, resultados.size() > TAMANO_PAGINA, pagina);
        });
    }

    private static String tipoFiscal(Categoria categoria) {
        return switch (categoria) {
            case INGRESO -> "I";
            case EGRESO -> "E";
            case TRASLADO -> "T";
            case NOMINA -> "N";
            case PAGO -> "P";
            default -> throw new IllegalArgumentException("No es un tipo CFDI: " + categoria);
        };
    }

    /**
     * Convierte una fila nativa con tipos de JDBC comprobados.
     * Hibernate/MySQL pueden devolver CHAR(1) como Character o String.
     */
    static DocumentoClasificado mapear(Object[] fila) {
        if (fila == null || fila.length != 11) {
            throw new IllegalArgumentException("La consulta no devolvió once columnas");
        }
        String clase = texto(fila[2], "documento_fiscal.clase");
        String tipo = texto(fila[3], "cfdi.tipo");
        Categoria categoria = ClasificadorDocumentoFiscal.identificar(
                clase, tipo, fila[9] != null, fila[10] != null);
        if (!(fila[6] instanceof Timestamp) && !(fila[6] instanceof LocalDateTime)) {
            throw new IllegalStateException("Fecha de emisión con tipo JDBC inesperado");
        }
        LocalDateTime fecha = fila[6] instanceof Timestamp t
                ? t.toLocalDateTime() : (LocalDateTime) fila[6];
        return new DocumentoClasificado(
                ((Number) fila[0]).longValue(), texto(fila[1], "documento_fiscal.uuid"),
                categoria, clase, tipo, texto(fila[4], "retencion.clave_retencion"),
                texto(fila[5], "contribuyente_documento.sentido"),
                fecha, texto(fila[7], "emisor.rfc"),
                texto(fila[8], "receptor.rfc"));
    }

    private static String texto(Object valor, String columna) {
        if (valor == null) return null;
        if (valor instanceof String s) return s;
        if (valor instanceof Character c) return c.toString();
        throw new IllegalStateException("Tipo JDBC inesperado en " + columna
                + ": " + valor.getClass().getName());
    }

    private static void exigirAlcance(EntityManager em, Long operadorId, long contribuyenteId) {
        Number asignaciones = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM usuario_contribuyente "
                + "WHERE usuario_id=:usuario AND contribuyente_id=:contribuyente")
                .setParameter("usuario", operadorId)
                .setParameter("contribuyente", contribuyenteId).getSingleResult();
        if (asignaciones.longValue() == 0) {
            throw new SecurityException("No tiene acceso al contribuyente solicitado");
        }
    }
}
