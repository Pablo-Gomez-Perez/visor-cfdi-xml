-- RF-020: ejecutar en la base visor_cfdi existente después de RF-013.
-- DDL con commit implícito: respaldo previo. No modifica clasificaciones por concepto.
USE visor_cfdi;

CREATE TABLE IF NOT EXISTS clasificacion_operacion_evento (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  documento_id BIGINT UNSIGNED NOT NULL,
  categoria_id BIGINT UNSIGNED NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  motivo VARCHAR(600) NOT NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_clasop_doc (contribuyente_id,documento_id,id),
  KEY idx_clasop_cat (categoria_id,contribuyente_id),
  KEY idx_clasop_usuario (usuario_id),
  CONSTRAINT fk_clasop_doc FOREIGN KEY (contribuyente_id,documento_id)
    REFERENCES contribuyente_documento(contribuyente_id,documento_id),
  CONSTRAINT fk_clasop_cat FOREIGN KEY (categoria_id,contribuyente_id)
    REFERENCES categoria_operacion(id,contribuyente_id),
  CONSTRAINT fk_clasop_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
  CONSTRAINT chk_clasop_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
