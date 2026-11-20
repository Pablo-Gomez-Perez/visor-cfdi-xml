package com.kathsoft.visor.app.service.fiscal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kathsoft.visor.app.model.entity.ClasificacionConceptoEntity;
import com.kathsoft.visor.app.model.entity.ClasificacionImpuestoConceptoEntity;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Distribuciones RF-021/022 independientes del XML y del periodo consultado.
 * El vínculo documental bloqueado protege la revisión conjunta de conceptos y tratamientos.
 */
public final class ClasificacionDetalleService {
    private final JpaContext jpa;
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Construye el servicio con el contexto compartido de la aplicación. */
    public ClasificacionDetalleService(JpaContext jpa) { this.jpa = Objects.requireNonNull(jpa); }
    /** Destino cerrado; ningún identificador de SQL proviene de texto del usuario. */
    public enum Tipo { CONCEPTO, IMPUESTO }
    /** Una distribución administrativa en moneda original; destino es categoría o tratamiento. */
    public record Asignacion(long destino, BigDecimal importe, String nota) {
        /** Valida identidad y longitud; los límites monetarios se comprueban contra el original. */
        public Asignacion {
            if (destino <= 0) throw new IllegalArgumentException("Seleccione categoría o tratamiento");
            nota = nota == null ? "" : nota.strip();
            if (nota.length() > 600) throw new IllegalArgumentException("Observación de máximo 600 caracteres");
            Objects.requireNonNull(importe);
            importe = importe.stripTrailingZeros();
        }
    }
    /** Concepto original y categoría general, disponibles para edición paginada. */
    public record Concepto(long id, int ordinal, String producto, String descripcion, BigDecimal importe,
            BigDecimal descuento, BigDecimal neto, String objeto, String categoriaGeneral) { }
    /** Impuesto original; importe null conserva exención o información ausente. */
    public record Impuesto(long id, String codigo, String movimiento, String factor, BigDecimal base,
            BigDecimal tasa, BigDecimal importe) { }
    /** Tratamientos de catálogo independientes de naturaleza contable. */
    public record Tratamiento(long id, String clave, String descripcion) {
        @Override public String toString() { return clave + " — " + descripcion; }
    }
    /** Instantánea consistente de revisión y distribuciones de un único origen. */
    public record Detalle(long revision, BigDecimal original, List<Asignacion> asignaciones) {
        public Detalle { asignaciones = List.copyOf(asignaciones); }
        /** Remanente administrativo, sin heredar la categoría general si existe detalle. */
        public BigDecimal pendiente() { return original == null ? null : original.subtract(asignaciones.stream().map(Asignacion::importe).reduce(BigDecimal.ZERO, BigDecimal::add)); }
    }

    /** Consulta hasta cien conceptos por página, sin impuestos multiplicando sus importes. */
    public List<Concepto> conceptos(Long actor, long titular, long documento, int pagina) {
        if (pagina < 0 || pagina > Integer.MAX_VALUE / 100) throw new IllegalArgumentException("Página inválida");
        return jpa.consultar(em -> {
            acceso(em, actor, titular, documento, false);
            return filas(em.createNativeQuery("""
                SELECT cc.id,cc.ordinal,cc.clave_prod_serv,cc.descripcion,cc.importe,COALESCE(cc.descuento,0),
                    cc.importe-COALESCE(cc.descuento,0),cc.objeto_impuesto,cat.nombre
                FROM cfdi_concepto cc
                LEFT JOIN clasificacion_operacion_evento h ON h.id=(SELECT MAX(hh.id) FROM clasificacion_operacion_evento hh WHERE hh.contribuyente_id=:t AND hh.documento_id=:d)
                LEFT JOIN categoria_operacion cat ON cat.id=h.categoria_id
                WHERE cc.documento_id=:d ORDER BY cc.ordinal,cc.id
                """).setParameter("t", titular).setParameter("d", documento).setFirstResult(pagina * 100).setMaxResults(100))
                .stream().map(r -> new Concepto(n(r[0]), ((Number)r[1]).intValue(), s(r[2]), s(r[3]),
                    (BigDecimal)r[4], (BigDecimal)r[5], (BigDecimal)r[6], s(r[7]), s(r[8]))).toList();
        });
    }

    /** Impuestos originales del concepto seleccionado, sólo tras comprobar su pertenencia. */
    public List<Impuesto> impuestos(Long actor, long titular, long documento, long concepto) {
        return jpa.consultar(em -> {
            acceso(em, actor, titular, documento, false);
            original(em, documento, Tipo.CONCEPTO, concepto);
            return filas(em.createNativeQuery("SELECT id,impuesto,movimiento,tipo_factor,base,tasa_cuota,importe FROM cfdi_concepto_impuesto WHERE concepto_id=:c ORDER BY movimiento,ordinal")
                .setParameter("c", concepto)).stream().map(r -> new Impuesto(n(r[0]),s(r[1]),s(r[2]),s(r[3]),(BigDecimal)r[4],(BigDecimal)r[5],(BigDecimal)r[6])).toList();
        });
    }

    /** Catálogo disponible para tratamientos parciales; no garantiza su procedencia tributaria. */
    public List<Tratamiento> tratamientos(Long actor, long titular) {
        return jpa.consultar(em -> {
            AccesoFiscal.exigir(em, actor, titular, Permisos.OPERACIONES_CONSULTAR);
            return filas(em.createNativeQuery("SELECT id,clave,descripcion FROM tratamiento_impuesto ORDER BY clave"))
                .stream().map(r -> new Tratamiento(n(r[0]),s(r[1]),s(r[2]))).toList();
        });
    }

    /** Lee revisión y asignaciones en una transacción consistente, sin bloquear el EDT. */
    public Detalle consultar(Long actor, long titular, long documento, Tipo tipo, long origen) {
        return jpa.enTransaccion(em -> {
            acceso(em, actor, titular, documento, false);
            return new Detalle(revision(em,titular,documento),original(em,documento,tipo,origen),asignaciones(em,titular,tipo,origen));
        });
    }

    /**
     * Sustituye atómicamente una distribución. Vacía significa retirar detalle y heredar RF-020.
     * No permite sobrescritura obsoleta, exceso, destinos ajenos/inactivos ni modificación del XML.
     * La auditoría conserva antes/después y comparte la misma transacción JPA.
     */
    public long guardar(Long actor, long titular, long documento, Tipo tipo, long origen, long revision,
            List<Asignacion> entradas, String motivo) {
        Objects.requireNonNull(tipo);
        List<Asignacion> nuevas = List.copyOf(entradas).stream().sorted(java.util.Comparator.comparingLong(Asignacion::destino)).toList();
        if (nuevas.size() > 100 || nuevas.stream().map(Asignacion::destino).distinct().count() != nuevas.size())
            throw new IllegalArgumentException("Máximo 100 destinos diferentes");
        return jpa.enTransaccion(em -> {
            acceso(em,actor,titular,documento,true);
            com.kathsoft.visor.app.security.AutorizacionService.exigir(em,actor,Permisos.OPERACIONES_CLASIFICAR_DETALLE);
            long vigente = revisionActual(em,titular,documento);
            if (vigente != revision) throw new IllegalStateException("La clasificación cambió. Actualice antes de guardar.");
            BigDecimal disponible = original(em,documento,tipo,origen);
            if (disponible == null) throw new IllegalArgumentException("Impuesto sin importe declarado; no se inventa un importe distribuible");
            DistribucionDecimal.validar(disponible,nuevas.stream().map(Asignacion::importe).toList());
            List<Asignacion> anteriores = asignaciones(em,titular,tipo,origen);
            if (anteriores.equals(nuevas)) return vigente;
            String razon = motivo == null ? "" : motivo.strip();
            boolean conHistoria=!em.createNativeQuery("SELECT b.id FROM bitacora_evento b JOIN bitacora_cambio c ON c.evento_id=b.id WHERE b.contribuyente_id=:t AND b.entidad_id=:d AND b.entidad='CLASIFICACION_DETALLE' AND c.atributo=:a LIMIT 1 FOR UPDATE")
                .setParameter("t",titular).setParameter("d",documento).setParameter("a",tipo.name()+":"+origen).getResultList().isEmpty();
            if (!conHistoria && anteriores.isEmpty() && !nuevas.isEmpty() && razon.isEmpty()) razon = "Clasificación inicial manual mediante RF-021.";
            if (razon.isEmpty() || razon.length() > 600) throw new IllegalArgumentException("Motivo obligatorio de hasta 600 caracteres");
            for (Asignacion a : nuevas) {
                validarDestino(em,titular,tipo,a.destino());
                if(tipo==Tipo.IMPUESTO && a.nota().isBlank())throw new IllegalArgumentException("Justificación obligatoria del tratamiento fiscal");
            }
            if (tipo == Tipo.CONCEPTO) {
                em.createQuery("delete from ClasificacionConceptoEntity x where x.contribuyenteId=:t and x.origenId=:o")
                    .setParameter("t",titular).setParameter("o",origen).executeUpdate();
                nuevas.forEach(a -> em.persist(new ClasificacionConceptoEntity(titular,origen,a.destino(),a.importe(),a.nota(),actor)));
            } else {
                em.createQuery("delete from ClasificacionImpuestoConceptoEntity x where x.contribuyenteId=:t and x.origenId=:o")
                    .setParameter("t",titular).setParameter("o",origen).executeUpdate();
                nuevas.forEach(a -> em.persist(new ClasificacionImpuestoConceptoEntity(titular,origen,a.destino(),a.importe(),a.nota(),actor)));
            }
            em.flush();
            em.createNativeQuery("INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo) VALUES (:u,:t,'CLASIFICACION_DETALLE',:d,:a,:m)")
                .setParameter("u",actor).setParameter("t",titular).setParameter("d",documento).setParameter("a",tipo.name()).setParameter("m",razon).executeUpdate();
            long evento = n(em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult());
            em.createNativeQuery("INSERT INTO bitacora_cambio(evento_id,atributo,valor_anterior,valor_nuevo) VALUES (:e,:a,:v,:n)")
                .setParameter("e",evento).setParameter("a",tipo.name()+":"+origen).setParameter("v",json(anteriores)).setParameter("n",json(nuevas)).executeUpdate();
            return evento;
        });
    }

    /** Historial paginado con instantáneas antes/después, sin reconstruir desde el XML. */
    public List<Object[]> historial(Long actor,long titular,long documento,int pagina) {
        if (pagina < 0 || pagina > Integer.MAX_VALUE/100) throw new IllegalArgumentException("Página inválida");
        return jpa.consultar(em -> {
            acceso(em,actor,titular,documento,false);
            return filas(em.createNativeQuery("""
                SELECT b.id,b.ocurrido_en,u.login,b.accion,b.motivo,c.atributo,c.valor_anterior,c.valor_nuevo
                FROM bitacora_evento b JOIN usuario u ON u.id=b.usuario_id JOIN bitacora_cambio c ON c.evento_id=b.id
                WHERE b.contribuyente_id=:t AND b.entidad_id=:d AND b.entidad='CLASIFICACION_DETALLE' ORDER BY b.id DESC
                """).setParameter("t",titular).setParameter("d",documento).setFirstResult(pagina*100).setMaxResults(100));
        });
    }
    private static void acceso(EntityManager em,Long actor,long t,long d,boolean lock) {
        AccesoFiscal.exigir(em,actor,t,Permisos.OPERACIONES_CONSULTAR);
        AccesoFiscal.documento(em,t,d,lock);
    }
    private static long revisionActual(EntityManager em,long t,long d) {
        List<?> r=em.createNativeQuery("SELECT id FROM bitacora_evento WHERE contribuyente_id=:t AND entidad_id=:d AND entidad='CLASIFICACION_DETALLE' ORDER BY id DESC LIMIT 1 FOR UPDATE")
            .setParameter("t",t).setParameter("d",d).getResultList();
        return r.isEmpty()?0:n(r.get(0));
    }
    private static long revision(EntityManager em,long t,long d) {
        return n(em.createNativeQuery("SELECT COALESCE(MAX(id),0) FROM bitacora_evento WHERE contribuyente_id=:t AND entidad_id=:d AND entidad='CLASIFICACION_DETALLE'")
                .setParameter("t",t).setParameter("d",d).getSingleResult());
    }
    private static BigDecimal original(EntityManager em,long doc,Tipo tipo,long origen) {
        String sql = tipo == Tipo.CONCEPTO ? "SELECT importe-COALESCE(descuento,0) FROM cfdi_concepto WHERE id=:o AND documento_id=:d"
            : "SELECT i.importe FROM cfdi_concepto_impuesto i JOIN cfdi_concepto c ON c.id=i.concepto_id WHERE i.id=:o AND c.documento_id=:d";
        List<?> r=em.createNativeQuery(sql).setParameter("o",origen).setParameter("d",doc).getResultList();
        if (r.size()!=1) throw new SecurityException("Origen no disponible en este documento");
        return (BigDecimal)r.get(0);
    }
    private static List<Asignacion> asignaciones(EntityManager em,long t,Tipo tipo,long origen) {
        String tabla = tipo == Tipo.CONCEPTO ? "clasificacion_concepto" : "clasificacion_impuesto_concepto";
        String columna = tipo == Tipo.CONCEPTO ? "concepto_id" : "impuesto_concepto_id";
        String destino = tipo == Tipo.CONCEPTO ? "categoria_id" : "tratamiento_id";
        return filas(em.createNativeQuery("SELECT "+destino+",importe_asignado,COALESCE("+(tipo==Tipo.CONCEPTO?"nota":"justificacion")+",'') FROM "+tabla+" WHERE contribuyente_id=:t AND "+columna+"=:o ORDER BY "+destino+",id")
            .setParameter("t",t).setParameter("o",origen)).stream().map(r->new Asignacion(n(r[0]),(BigDecimal)r[1],s(r[2]))).toList();
    }
    private static void validarDestino(EntityManager em,long t,Tipo tipo,long destino) {
        String sql = tipo == Tipo.CONCEPTO ? "SELECT id FROM categoria_operacion WHERE id=:id AND contribuyente_id=:t AND activo=1 FOR UPDATE"
            : "SELECT id FROM tratamiento_impuesto WHERE id=:id";
        Query q=em.createNativeQuery(sql).setParameter("id",destino);
        if(tipo==Tipo.CONCEPTO) q.setParameter("t",t);
        if(q.getResultList().size()!=1) throw new IllegalArgumentException("Categoría o tratamiento no disponible");
    }
    private static String json(Object valor) {
        try { return JSON.writeValueAsString(valor); } catch(JsonProcessingException e) { throw new IllegalStateException("No se pudo generar auditoría",e); }
    }
    private static long n(Object o) { return ((Number)o).longValue(); }
    private static String s(Object o) { return o==null?null:o.toString(); }
    @SuppressWarnings("unchecked") private static List<Object[]> filas(Query q) { return q.getResultList(); }
}
