package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.security.AutorizacionService;
import jakarta.persistence.EntityManager;

/** Comprobaciones comunes del módulo fiscal, siempre contra permisos vigentes. */
public final class AccesoFiscal {
    private AccesoFiscal() { }

    /** Exige usuario activo, permiso y asignación explícita, también para ADMIN. */
    public static void exigir(EntityManager em, Long actor, long titular, String permiso) {
        AutorizacionService.exigir(em, actor, permiso);
        Number n = (Number) em.createNativeQuery("SELECT COUNT(*) FROM usuario_contribuyente WHERE usuario_id=:u AND contribuyente_id=:t")
                .setParameter("u", actor).setParameter("t", titular).getSingleResult();
        if (n.longValue() != 1) throw new SecurityException("Contribuyente no autorizado");
    }

    /** Comprueba disponibilidad del vínculo; el bloqueo serializa cambios de clasificación y evidencia. */
    public static void documento(EntityManager em, long titular, long documento, boolean bloquear) {
        var filas = em.createNativeQuery("SELECT documento_id FROM contribuyente_documento WHERE contribuyente_id=:t AND documento_id=:d AND archivado_en IS NULL" + (bloquear ? " FOR UPDATE" : ""))
                .setParameter("t", titular).setParameter("d", documento).getResultList();
        if (filas.size() != 1) throw new SecurityException("Documento no disponible para el contribuyente");
    }
}
