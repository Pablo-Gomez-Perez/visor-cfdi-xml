package com.kathsoft.visor.app.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro de auditoría en la tabla bitacora_evento existente. */
@Entity
@Table(name = "bitacora_evento")
public class BitacoraEventoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "entidad", nullable = false, length = 85)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(name = "accion", nullable = false, length = 50)
    private String accion;

    @Column(name = "motivo", length = 600)
    private String motivo;

    /** Constructor requerido por JPA. */
    protected BitacoraEventoEntity() {
    }

    /**
     * Construye un evento sin registrar contraseñas ni hashes.
     *
     * @param usuarioId usuario responsable de la operación
     * @param entidad nombre de la entidad modificada
     * @param entidadId identificador del registro
     * @param accion tipo de acción
     * @param motivo descripción sin datos sensibles
     */
    public BitacoraEventoEntity(Long usuarioId, String entidad, Long entidadId, String accion, String motivo) {
        this.usuarioId = usuarioId;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.motivo = motivo;
    }
}
