-- RF-013. Ejecutar UNA sola vez en instalaciones existentes; respaldo y aplicación cerrada.
-- Los esquemas completos de la rama ya contienen la columna.
USE visor_cfdi;

ALTER TABLE contribuyente_documento
    ADD COLUMN archivado_en DATETIME(6) NULL DEFAULT NULL AFTER primer_registro;

-- Retirar permisos obsoletos únicamente si se probó la versión anterior de PR #15.
-- No modifica documentos ni historial. Los permisos nuevos se crean al iniciar Java.
START TRANSACTION;
DELETE rp FROM rol_permiso rp JOIN permiso p ON p.id=rp.permiso_id
WHERE p.recurso='IMPORTACIONES' AND p.accion IN ('ELIMINAR_LOTE','ELIMINAR_ARCHIVO');
DELETE FROM permiso
WHERE recurso='IMPORTACIONES' AND accion IN ('ELIMINAR_LOTE','ELIMINAR_ARCHIVO');
COMMIT;
