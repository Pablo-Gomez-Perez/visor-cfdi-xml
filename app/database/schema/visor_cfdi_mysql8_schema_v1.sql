-- ============================================================================
-- VISOR CFDI XML - ESQUEMA INICIAL INTEGRAL (v1.0)
-- Motor: MySQL 8.0.16+ (recomendado MySQL 8.0.36+/8.4 LTS)
-- Objetivo: almacenar DATOS EXTRAIDOS de CFDI y retenciones, no archivos XML.
-- Crear sobre una instalacion nueva. No destruye datos ni deshabilita FK.
-- La base de datos NO es un validador XSD / juridico / de IVA acreditable.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS visor_cfdi
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE visor_cfdi;

-- 01. CATALOGOS LOCALES Y VERSIONABLES (las claves SAT evolucionan).
CREATE TABLE cat_tipo_cfdi (
  clave CHAR(1) PRIMARY KEY,
  descripcion VARCHAR(80) NOT NULL
) ENGINE=InnoDB;

INSERT INTO cat_tipo_cfdi (clave, descripcion) VALUES
  ('I','Ingreso'), ('E','Egreso'), ('T','Traslado'),
  ('N','Nomina'), ('P','Pago')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

CREATE TABLE sat_catalogo (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(90) NOT NULL,
  descripcion VARCHAR(250) NULL,
  UNIQUE KEY uk_sat_catalogo_nombre (nombre)
) ENGINE=InnoDB;

CREATE TABLE sat_catalogo_clave (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  catalogo_id BIGINT UNSIGNED NOT NULL,
  clave VARCHAR(45) NOT NULL,
  descripcion VARCHAR(450) NOT NULL,
  vigencia_inicio DATE NOT NULL,
  vigencia_fin DATE NULL,
  UNIQUE KEY uk_catalogo_clave_vigencia (catalogo_id, clave, vigencia_inicio),
  CONSTRAINT fk_scclave_catalogo FOREIGN KEY (catalogo_id)
    REFERENCES sat_catalogo(id),
  CONSTRAINT chk_scclave_vigencia CHECK
    (vigencia_fin IS NULL OR vigencia_fin >= vigencia_inicio)
) ENGINE=InnoDB;

-- Importar las versiones oficiales completas de catalogos SAT posteriormente.
-- NO se aplican FK a claves SAT dinamicas dentro de documentos historicos.

-- 02. CONTRIBUYENTES, TERCEROS Y ACCESOS.
CREATE TABLE contribuyente (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rfc VARCHAR(13) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  nombre_razon_social VARCHAR(300) NOT NULL,
  codigo_postal_fiscal CHAR(5) NULL,
  correo_contacto VARCHAR(254) NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_contribuyente_rfc (rfc),
  CONSTRAINT chk_contribuyente_rfc CHECK (CHAR_LENGTH(rfc) BETWEEN 12 AND 13)
) ENGINE=InnoDB;

CREATE TABLE contribuyente_regimen (
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  clave_regimen VARCHAR(5) NOT NULL,
  vigente_desde DATE NOT NULL,
  vigente_hasta DATE NULL,
  PRIMARY KEY (contribuyente_id, clave_regimen, vigente_desde),
  CONSTRAINT fk_contrib_regimen_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT chk_contrib_regimen_fechas CHECK
    (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
) ENGINE=InnoDB;

CREATE TABLE tercero (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rfc VARCHAR(13) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
  nombre_razon_social VARCHAR(300) NOT NULL,
  pais_fiscal CHAR(3) NULL,
  num_registro_fiscal_extranjero VARCHAR(100) NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_tercero_rfc (rfc),
  KEY idx_tercero_nombre (nombre_razon_social)
) ENGINE=InnoDB;

-- Una contraparte puede ser proveedor Y cliente para el mismo contribuyente.
-- RFC genericos no se unifican automaticamente en una contraparte unica.
CREATE TABLE contribuyente_tercero_rol (
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  tercero_id BIGINT UNSIGNED NOT NULL,
  rol VARCHAR(30) NOT NULL,
  PRIMARY KEY (contribuyente_id, tercero_id, rol),
  CONSTRAINT fk_ctr_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_ctr_tercero FOREIGN KEY (tercero_id)
    REFERENCES tercero(id),
  CONSTRAINT chk_ctr_rol CHECK
    (rol IN ('PROVEEDOR','CLIENTE','PRESTADOR','OTRO'))
) ENGINE=InnoDB;

CREATE TABLE usuario (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre_completo VARCHAR(200) NOT NULL,
  login VARCHAR(80) NOT NULL,
  correo VARCHAR(254) NULL,
  hash_contrasena VARCHAR(255) NOT NULL,
  estado VARCHAR(15) NOT NULL DEFAULT 'ACTIVO',
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ultimo_acceso DATETIME(6) NULL,
  UNIQUE KEY uk_usuario_login (login),
  UNIQUE KEY uk_usuario_correo (correo),
  CONSTRAINT chk_usuario_estado CHECK
    (estado IN ('ACTIVO','INACTIVO','BLOQUEADO'))
) ENGINE=InnoDB;

CREATE TABLE rol (
  id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  clave VARCHAR(40) NOT NULL,
  descripcion VARCHAR(200) NOT NULL,
  UNIQUE KEY uk_rol_clave (clave)
) ENGINE=InnoDB;

INSERT INTO rol (clave, descripcion) VALUES
  ('ADMIN','Administracion del sistema'),
  ('CONTADOR','Administracion fiscal y financiera'),
  ('AUXILIAR','Operaciones permitidas por sus permisos'),
  ('CONSULTA','Acceso de solo lectura segun permisos')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion);

CREATE TABLE permiso (
  id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  recurso VARCHAR(70) NOT NULL,
  accion VARCHAR(40) NOT NULL,
  descripcion VARCHAR(200) NOT NULL,
  UNIQUE KEY uk_permiso_recurso_accion (recurso, accion)
) ENGINE=InnoDB;

CREATE TABLE rol_permiso (
  rol_id SMALLINT UNSIGNED NOT NULL,
  permiso_id SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (rol_id, permiso_id),
  CONSTRAINT fk_rolperm_rol FOREIGN KEY (rol_id) REFERENCES rol(id),
  CONSTRAINT fk_rolperm_permiso FOREIGN KEY (permiso_id) REFERENCES permiso(id)
) ENGINE=InnoDB;

CREATE TABLE usuario_rol (
  usuario_id BIGINT UNSIGNED NOT NULL,
  rol_id SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (usuario_id, rol_id),
  CONSTRAINT fk_usrol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
  CONSTRAINT fk_usrol_rol FOREIGN KEY (rol_id) REFERENCES rol(id)
) ENGINE=InnoDB;

CREATE TABLE usuario_contribuyente (
  usuario_id BIGINT UNSIGNED NOT NULL,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (usuario_id, contribuyente_id),
  CONSTRAINT fk_uc_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
  CONSTRAINT fk_uc_contrib FOREIGN KEY (contribuyente_id) REFERENCES contribuyente(id)
) ENGINE=InnoDB;

-- 03. REPOSITORIOS DOCUMENTALES (almacenamiento desacoplado de MySQL).
CREATE TABLE repositorio_documental (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  proveedor VARCHAR(30) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_repositorio_nombre (nombre),
  CONSTRAINT chk_repo_proveedor CHECK
    (proveedor IN ('LOCAL','S3','DROPBOX','MEGA','OTRO'))
) ENGINE=InnoDB;

CREATE TABLE repositorio_local (
  repositorio_id BIGINT UNSIGNED PRIMARY KEY,
  ruta_base VARCHAR(1024) NOT NULL,
  CONSTRAINT fk_repo_local FOREIGN KEY (repositorio_id)
    REFERENCES repositorio_documental(id)
) ENGINE=InnoDB;

-- Los proveedores remotos tendran tablas de configuracion propias.
-- Nunca almacenar tokens/llaves en esta base de datos sin gestion de secretos.
CREATE TABLE archivo_documental (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  repositorio_id BIGINT UNSIGNED NOT NULL,
  clave_objeto VARCHAR(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  sha256 BINARY(32) NOT NULL,
  tamano_bytes BIGINT UNSIGNED NOT NULL,
  tipo_mime VARCHAR(100) NOT NULL DEFAULT 'application/xml',
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_archivo_ubicacion (repositorio_id, clave_objeto),
  KEY idx_archivo_sha256 (sha256),
  CONSTRAINT fk_archivo_repositorio FOREIGN KEY (repositorio_id)
    REFERENCES repositorio_documental(id),
  CONSTRAINT chk_archivo_tamano CHECK (tamano_bytes > 0)
) ENGINE=InnoDB;

-- 04. DOCUMENTO FISCAL: CFDI Anexo 20 O retenciones (dos familias).
CREATE TABLE documento_fiscal (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  clase VARCHAR(15) NOT NULL,
  uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  version_estandar VARCHAR(8) NOT NULL,
  fecha_emision DATETIME NOT NULL,
  registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_doc_uuid (uuid),
  KEY idx_doc_fecha_emision (fecha_emision),
  CONSTRAINT chk_doc_clase CHECK (clase IN ('CFDI','RETENCION')),
  CONSTRAINT chk_doc_uuid CHECK
    (uuid REGEXP '^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$')
) ENGINE=InnoDB;

-- Instantanea documental: emisor y receptor del XML en el momento del timbrado.
-- No se sobreescriben con cambios de nombre, regimen o domicilio posteriores.
CREATE TABLE documento_participante (
  documento_id BIGINT UNSIGNED NOT NULL,
  rol CHAR(1) NOT NULL,
  tercero_id BIGINT UNSIGNED NULL,
  rfc VARCHAR(13) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
  nombre_razon_social VARCHAR(300) NULL,
  regimen_fiscal VARCHAR(5) NULL,
  domicilio_fiscal_cp CHAR(5) NULL,
  residencia_fiscal CHAR(3) NULL,
  num_registro_fiscal_extranjero VARCHAR(100) NULL,
  uso_cfdi VARCHAR(5) NULL,
  PRIMARY KEY (documento_id, rol),
  KEY idx_doc_part_rfc (rfc, rol),
  CONSTRAINT fk_docpart_documento FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT fk_docpart_tercero FOREIGN KEY (tercero_id)
    REFERENCES tercero(id),
  CONSTRAINT chk_docpart_rol CHECK (rol IN ('E','R'))
) ENGINE=InnoDB;

-- Un UUID existe UNA sola vez globalmente. Cada contribuyente lo ve desde
-- su propia perspectiva: emitido, recibido o ambos (casos excepcionales).
CREATE TABLE contribuyente_documento (
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  documento_id BIGINT UNSIGNED NOT NULL,
  sentido VARCHAR(10) NOT NULL,
  primer_registro DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (contribuyente_id, documento_id),
  KEY idx_cd_doc (documento_id),
  CONSTRAINT fk_cd_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_cd_doc FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT chk_cd_sentido CHECK
    (sentido IN ('EMITIDO','RECIBIDO','AMBOS'))
) ENGINE=InnoDB;

CREATE TABLE documento_archivo (
  documento_id BIGINT UNSIGNED NOT NULL,
  archivo_id BIGINT UNSIGNED NOT NULL,
  tipo_uso VARCHAR(15) NOT NULL DEFAULT 'ORIGINAL',
  PRIMARY KEY (documento_id, archivo_id),
  CONSTRAINT fk_da_doc FOREIGN KEY (documento_id) REFERENCES documento_fiscal(id),
  CONSTRAINT fk_da_archivo FOREIGN KEY (archivo_id) REFERENCES archivo_documental(id),
  CONSTRAINT chk_da_uso CHECK (tipo_uso IN ('ORIGINAL','RESPALDO','ANEXO'))
) ENGINE=InnoDB;

CREATE TABLE documento_timbre (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  version_timbre VARCHAR(8) NOT NULL,
  fecha_timbrado DATETIME NOT NULL,
  rfc_proveedor_certificacion VARCHAR(13) NULL,
  no_certificado_sat VARCHAR(30) NOT NULL,
  sello_cfd TEXT NULL,
  sello_sat TEXT NULL,
  CONSTRAINT fk_timbre_documento FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id)
) ENGINE=InnoDB;

CREATE TABLE documento_verificacion (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  resultado VARCHAR(25) NOT NULL,
  detalle VARCHAR(1000) NULL,
  verificado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  fuente VARCHAR(120) NULL,
  KEY idx_dver_doc_fecha (documento_id, verificado_en),
  CONSTRAINT fk_dver_doc FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT chk_dver_tipo CHECK
    (tipo IN ('XSD','SELLO','TIMBRADO','SAT','NEGOCIO')),
  CONSTRAINT chk_dver_resultado CHECK
    (resultado IN ('VALIDO','INVALIDO','NO_VERIFICADO','ERROR','ADVERTENCIA'))
) ENGINE=InnoDB;

CREATE TABLE consulta_estado_sat (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  estado VARCHAR(25) NOT NULL,
  es_cancelable VARCHAR(50) NULL,
  estado_cancelacion VARCHAR(70) NULL,
  fecha_consulta DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  referencia_consulta VARCHAR(180) NULL,
  detalle_error VARCHAR(600) NULL,
  KEY idx_estado_doc_fecha (documento_id, fecha_consulta),
  CONSTRAINT fk_estado_documento FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT chk_estado_sat CHECK
    (estado IN ('VIGENTE','CANCELADO','NO_ENCONTRADO','DESCONOCIDO','ERROR'))
) ENGINE=InnoDB;

-- 05. NUCLEO CFDI (I, E, T, N, P): campos del comprobante, no de sus partes.
CREATE TABLE cfdi (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  tipo CHAR(1) NOT NULL,
  serie VARCHAR(40) NULL,
  folio VARCHAR(40) NULL,
  lugar_expedicion CHAR(5) NULL,
  moneda VARCHAR(3) NOT NULL,
  tipo_cambio DECIMAL(20,6) NULL,
  subtotal DECIMAL(20,6) NOT NULL,
  descuento DECIMAL(20,6) NULL,
  total DECIMAL(20,6) NOT NULL,
  forma_pago VARCHAR(5) NULL,
  metodo_pago VARCHAR(5) NULL,
  condiciones_pago VARCHAR(1000) NULL,
  exportacion VARCHAR(5) NULL,
  confirmacion VARCHAR(20) NULL,
  no_certificado VARCHAR(30) NULL,
  sello_emisor TEXT NULL,
  KEY idx_cfdi_tipo (tipo),
  KEY idx_cfdi_serie_folio (serie, folio),
  CONSTRAINT fk_cfdi_documento FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT fk_cfdi_tipo FOREIGN KEY (tipo) REFERENCES cat_tipo_cfdi(clave),
  CONSTRAINT chk_cfdi_importes CHECK
    (subtotal >= 0 AND total >= 0 AND (descuento IS NULL OR descuento >= 0))
) ENGINE=InnoDB;

CREATE TABLE cfdi_informacion_global (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  periodicidad VARCHAR(5) NOT NULL,
  meses VARCHAR(5) NOT NULL,
  ejercicio SMALLINT UNSIGNED NOT NULL,
  CONSTRAINT fk_cglobal_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id)
) ENGINE=InnoDB;

CREATE TABLE cfdi_relacion_grupo (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  tipo_relacion VARCHAR(5) NOT NULL,
  UNIQUE KEY uk_rel_grupo_ordinal (documento_id, ordinal),
  CONSTRAINT fk_relgrupo_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id),
  CONSTRAINT chk_relgrupo_ordinal CHECK (ordinal > 0)
) ENGINE=InnoDB;

-- No hacer FK al UUID destino: puede no haberse importado todavia.
CREATE TABLE cfdi_relacion_uuid (
  grupo_id BIGINT UNSIGNED NOT NULL,
  uuid_relacionado CHAR(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  PRIMARY KEY (grupo_id, uuid_relacionado),
  KEY idx_relacion_uuid (uuid_relacionado),
  CONSTRAINT fk_rel_uuid_grupo FOREIGN KEY (grupo_id)
    REFERENCES cfdi_relacion_grupo(id)
) ENGINE=InnoDB;

CREATE TABLE cfdi_concepto (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  clave_prod_serv VARCHAR(12) NOT NULL,
  no_identificacion VARCHAR(100) NULL,
  cantidad DECIMAL(20,6) NOT NULL,
  clave_unidad VARCHAR(10) NOT NULL,
  unidad VARCHAR(50) NULL,
  descripcion TEXT NOT NULL,
  valor_unitario DECIMAL(20,6) NOT NULL,
  importe DECIMAL(20,6) NOT NULL,
  descuento DECIMAL(20,6) NULL,
  objeto_impuesto VARCHAR(5) NULL,
  UNIQUE KEY uk_concepto_ordinal (documento_id, ordinal),
  CONSTRAINT fk_concepto_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id),
  CONSTRAINT chk_concepto_montos CHECK
    (ordinal > 0 AND cantidad >= 0 AND valor_unitario >= 0 AND importe >= 0
     AND (descuento IS NULL OR descuento >= 0))
) ENGINE=InnoDB;

CREATE TABLE cfdi_concepto_cuenta_tercero (
  concepto_id BIGINT UNSIGNED PRIMARY KEY,
  rfc VARCHAR(13) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  nombre VARCHAR(300) NOT NULL,
  regimen_fiscal VARCHAR(5) NOT NULL,
  domicilio_fiscal_cp CHAR(5) NOT NULL,
  CONSTRAINT fk_cuentatercero_concepto FOREIGN KEY (concepto_id)
    REFERENCES cfdi_concepto(id)
) ENGINE=InnoDB;

CREATE TABLE cfdi_concepto_pedimento (
  concepto_id BIGINT UNSIGNED NOT NULL,
  numero_pedimento VARCHAR(35) NOT NULL,
  PRIMARY KEY (concepto_id, numero_pedimento),
  CONSTRAINT fk_cped_concepto FOREIGN KEY (concepto_id)
    REFERENCES cfdi_concepto(id)
) ENGINE=InnoDB;

CREATE TABLE cfdi_concepto_parte (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  concepto_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  clave_prod_serv VARCHAR(12) NOT NULL,
  no_identificacion VARCHAR(100) NULL,
  cantidad DECIMAL(20,6) NOT NULL,
  unidad VARCHAR(50) NULL,
  descripcion TEXT NOT NULL,
  valor_unitario DECIMAL(20,6) NULL,
  importe DECIMAL(20,6) NULL,
  UNIQUE KEY uk_cparte_ordinal (concepto_id, ordinal),
  CONSTRAINT fk_cparte_concepto FOREIGN KEY (concepto_id)
    REFERENCES cfdi_concepto(id)
) ENGINE=InnoDB;

CREATE TABLE cfdi_concepto_parte_pedimento (
  parte_id BIGINT UNSIGNED NOT NULL,
  numero_pedimento VARCHAR(35) NOT NULL,
  PRIMARY KEY (parte_id, numero_pedimento),
  CONSTRAINT fk_cparte_ped FOREIGN KEY (parte_id)
    REFERENCES cfdi_concepto_parte(id)
) ENGINE=InnoDB;

CREATE TABLE cfdi_concepto_impuesto (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  concepto_id BIGINT UNSIGNED NOT NULL,
  movimiento VARCHAR(10) NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  impuesto VARCHAR(5) NOT NULL,
  tipo_factor VARCHAR(12) NULL,
  base DECIMAL(20,6) NOT NULL,
  tasa_cuota DECIMAL(18,6) NULL,
  importe DECIMAL(20,6) NULL,
  UNIQUE KEY uk_concepto_imp_ordinal (concepto_id, movimiento, ordinal),
  KEY idx_cimp_impuesto (impuesto, tipo_factor, tasa_cuota),
  CONSTRAINT fk_cimp_concepto FOREIGN KEY (concepto_id)
    REFERENCES cfdi_concepto(id),
  CONSTRAINT chk_cimp_mov CHECK (movimiento IN ('TRASLADO','RETENCION')),
  CONSTRAINT chk_cimp_montos CHECK
    (ordinal > 0 AND base >= 0 AND (importe IS NULL OR importe >= 0))
) ENGINE=InnoDB;

-- El subtotal oficial del nodo impuestos se preserva como dato documental;
-- no sustituye el calculo derivado de sus conceptos.
CREATE TABLE cfdi_impuesto_totales (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  total_trasladados DECIMAL(20,6) NULL,
  total_retenidos DECIMAL(20,6) NULL,
  CONSTRAINT fk_cimptot_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id)
) ENGINE=InnoDB;

CREATE TABLE cfdi_impuesto_global (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  movimiento VARCHAR(10) NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  impuesto VARCHAR(5) NOT NULL,
  tipo_factor VARCHAR(12) NULL,
  base DECIMAL(20,6) NULL,
  tasa_cuota DECIMAL(18,6) NULL,
  importe DECIMAL(20,6) NULL,
  UNIQUE KEY uk_gimp_ordinal (documento_id, movimiento, ordinal),
  CONSTRAINT fk_gimp_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id),
  CONSTRAINT chk_gimp_mov CHECK (movimiento IN ('TRASLADO','RETENCION')),
  CONSTRAINT chk_gimp_positivos CHECK
    (ordinal > 0 AND (importe IS NULL OR importe >= 0) AND (base IS NULL OR base >= 0))
) ENGINE=InnoDB;

-- 06. COMPLEMENTOS: registro extensible de presencia + modulos especializados.
CREATE TABLE tipo_complemento (
  id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  espacio_nombres VARCHAR(400) NOT NULL,
  version VARCHAR(20) NULL,
  UNIQUE KEY uk_comp_namespace (espacio_nombres)
) ENGINE=InnoDB;

CREATE TABLE documento_complemento (
  documento_id BIGINT UNSIGNED NOT NULL,
  tipo_complemento_id SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (documento_id, tipo_complemento_id),
  CONSTRAINT fk_dcomp_doc FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT fk_dcomp_tipo FOREIGN KEY (tipo_complemento_id)
    REFERENCES tipo_complemento(id)
) ENGINE=InnoDB;

-- 07. PAGOS 2.0: un CFDI tipo P puede contener multiples nodos Pago.
CREATE TABLE pago_fiscal (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  fecha_pago DATETIME NOT NULL,
  forma_pago VARCHAR(5) NOT NULL,
  moneda VARCHAR(3) NOT NULL,
  tipo_cambio DECIMAL(20,6) NULL,
  monto DECIMAL(20,6) NOT NULL,
  numero_operacion VARCHAR(100) NULL,
  rfc_emisor_cta_ordenante VARCHAR(13) NULL,
  nombre_banco_ordenante_ext VARCHAR(300) NULL,
  cuenta_ordenante VARCHAR(60) NULL,
  rfc_emisor_cta_beneficiaria VARCHAR(13) NULL,
  cuenta_beneficiaria VARCHAR(60) NULL,
  tipo_cadena_pago VARCHAR(5) NULL,
  certificado_pago TEXT NULL,
  cadena_pago TEXT NULL,
  sello_pago TEXT NULL,
  UNIQUE KEY uk_pago_fiscal_ordinal (documento_id, ordinal),
  KEY idx_pago_fiscal_fecha (fecha_pago),
  CONSTRAINT fk_pago_fiscal_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id),
  CONSTRAINT chk_pago_fiscal_monto CHECK (ordinal > 0 AND monto > 0)
) ENGINE=InnoDB;

CREATE TABLE pago_fiscal_documento (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  pago_fiscal_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  uuid_documento CHAR(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  serie VARCHAR(40) NULL,
  folio VARCHAR(40) NULL,
  moneda VARCHAR(3) NOT NULL,
  equivalencia_dr DECIMAL(20,10) NULL,
  num_parcialidad INT UNSIGNED NULL,
  saldo_anterior DECIMAL(20,6) NULL,
  importe_pagado DECIMAL(20,6) NOT NULL,
  saldo_insoluto DECIMAL(20,6) NULL,
  objeto_impuesto VARCHAR(5) NULL,
  UNIQUE KEY uk_pagodoc_ordinal (pago_fiscal_id, ordinal),
  KEY idx_pagodoc_uuid (uuid_documento),
  CONSTRAINT fk_pagodoc_pago FOREIGN KEY (pago_fiscal_id)
    REFERENCES pago_fiscal(id),
  CONSTRAINT chk_pagodoc_montos CHECK
    (ordinal > 0 AND importe_pagado > 0 AND
     (saldo_anterior IS NULL OR saldo_anterior >= 0) AND
     (saldo_insoluto IS NULL OR saldo_insoluto >= 0))
) ENGINE=InnoDB;

CREATE TABLE pago_fiscal_impuesto_documento (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  pago_documento_id BIGINT UNSIGNED NOT NULL,
  movimiento VARCHAR(10) NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  impuesto VARCHAR(5) NOT NULL,
  tipo_factor VARCHAR(12) NULL,
  base DECIMAL(20,6) NULL,
  tasa_cuota DECIMAL(18,6) NULL,
  importe DECIMAL(20,6) NULL,
  UNIQUE KEY uk_pfid_ordinal (pago_documento_id, movimiento, ordinal),
  CONSTRAINT fk_pfid_pagodoc FOREIGN KEY (pago_documento_id)
    REFERENCES pago_fiscal_documento(id),
  CONSTRAINT chk_pfid_mov CHECK (movimiento IN ('TRASLADO','RETENCION'))
) ENGINE=InnoDB;

CREATE TABLE pago_fiscal_impuesto_pago (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  pago_fiscal_id BIGINT UNSIGNED NOT NULL,
  movimiento VARCHAR(10) NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  impuesto VARCHAR(5) NOT NULL,
  tipo_factor VARCHAR(12) NULL,
  base DECIMAL(20,6) NULL,
  tasa_cuota DECIMAL(18,6) NULL,
  importe DECIMAL(20,6) NULL,
  UNIQUE KEY uk_pfip_ordinal (pago_fiscal_id, movimiento, ordinal),
  CONSTRAINT fk_pfip_pago FOREIGN KEY (pago_fiscal_id)
    REFERENCES pago_fiscal(id),
  CONSTRAINT chk_pfip_mov CHECK (movimiento IN ('TRASLADO','RETENCION'))
) ENGINE=InnoDB;

CREATE TABLE pago_fiscal_totales (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  monto_total_pagos DECIMAL(20,6) NOT NULL,
  CONSTRAINT fk_pft_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id)
) ENGINE=InnoDB;

-- Cada clave corresponde a un atributo numerico del nodo Pagos20:Totales,
-- por ejemplo TotalTrasladosBaseIVA16 o TotalRetencionesISR.
CREATE TABLE pago_fiscal_total_impuesto (
  documento_id BIGINT UNSIGNED NOT NULL,
  atributo VARCHAR(70) NOT NULL,
  importe_mxn DECIMAL(20,6) NOT NULL,
  PRIMARY KEY (documento_id, atributo),
  CONSTRAINT fk_pfti_tot FOREIGN KEY (documento_id)
    REFERENCES pago_fiscal_totales(documento_id),
  CONSTRAINT chk_pfti_importe CHECK (importe_mxn >= 0)
) ENGINE=InnoDB;

-- 08. NOMINA 1.2: datos estructurados generales; extensiones por revision.
CREATE TABLE nomina (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  version_complemento VARCHAR(10) NOT NULL,
  tipo_nomina CHAR(1) NOT NULL,
  fecha_pago DATE NOT NULL,
  fecha_inicial_pago DATE NOT NULL,
  fecha_final_pago DATE NOT NULL,
  num_dias_pagados DECIMAL(12,3) NOT NULL,
  total_percepciones DECIMAL(20,6) NULL,
  total_deducciones DECIMAL(20,6) NULL,
  total_otros_pagos DECIMAL(20,6) NULL,
  CONSTRAINT fk_nomina_cfdi FOREIGN KEY (documento_id)
    REFERENCES cfdi(documento_id),
  CONSTRAINT chk_nomina_tipo CHECK (tipo_nomina IN ('O','E')),
  CONSTRAINT chk_nomina_fecha CHECK (fecha_final_pago >= fecha_inicial_pago)
) ENGINE=InnoDB;

CREATE TABLE nomina_emisor (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  curp VARCHAR(18) NULL,
  registro_patronal VARCHAR(30) NULL,
  rfc_patron_origen VARCHAR(13) NULL,
  origen_recurso VARCHAR(5) NULL,
  monto_recurso_propio DECIMAL(20,6) NULL,
  CONSTRAINT fk_nomemi_nomina FOREIGN KEY (documento_id)
    REFERENCES nomina(documento_id)
) ENGINE=InnoDB;

CREATE TABLE nomina_receptor (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  curp VARCHAR(18) NOT NULL,
  num_seguridad_social VARCHAR(20) NULL,
  fecha_inicio_relacion_laboral DATE NULL,
  antiguedad VARCHAR(20) NULL,
  tipo_contrato VARCHAR(5) NULL,
  sindicalizado CHAR(2) NULL,
  tipo_jornada VARCHAR(5) NULL,
  tipo_regimen VARCHAR(5) NOT NULL,
  num_empleado VARCHAR(25) NOT NULL,
  departamento VARCHAR(120) NULL,
  puesto VARCHAR(120) NULL,
  riesgo_puesto VARCHAR(5) NULL,
  periodicidad_pago VARCHAR(5) NULL,
  banco VARCHAR(5) NULL,
  cuenta_bancaria VARCHAR(60) NULL,
  salario_base_cot_apor DECIMAL(20,6) NULL,
  salario_diario_integrado DECIMAL(20,6) NULL,
  clave_entidad_federativa VARCHAR(5) NOT NULL,
  CONSTRAINT fk_nomrec_nomina FOREIGN KEY (documento_id)
    REFERENCES nomina(documento_id)
) ENGINE=InnoDB;

CREATE TABLE nomina_percepcion (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  tipo_percepcion VARCHAR(5) NOT NULL,
  clave VARCHAR(20) NOT NULL,
  concepto VARCHAR(250) NOT NULL,
  importe_gravado DECIMAL(20,6) NOT NULL,
  importe_exento DECIMAL(20,6) NOT NULL,
  UNIQUE KEY uk_nomperc_ordinal (documento_id, ordinal),
  CONSTRAINT fk_nomperc_nomina FOREIGN KEY (documento_id)
    REFERENCES nomina(documento_id)
) ENGINE=InnoDB;

CREATE TABLE nomina_deduccion (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  tipo_deduccion VARCHAR(5) NOT NULL,
  clave VARCHAR(20) NOT NULL,
  concepto VARCHAR(250) NOT NULL,
  importe DECIMAL(20,6) NOT NULL,
  UNIQUE KEY uk_nomded_ordinal (documento_id, ordinal),
  CONSTRAINT fk_nomded_nomina FOREIGN KEY (documento_id)
    REFERENCES nomina(documento_id)
) ENGINE=InnoDB;

CREATE TABLE nomina_otro_pago (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  tipo_otro_pago VARCHAR(5) NOT NULL,
  clave VARCHAR(20) NOT NULL,
  concepto VARCHAR(250) NOT NULL,
  importe DECIMAL(20,6) NOT NULL,
  UNIQUE KEY uk_nomotro_ordinal (documento_id, ordinal),
  CONSTRAINT fk_nomotro_nomina FOREIGN KEY (documento_id)
    REFERENCES nomina(documento_id)
) ENGINE=InnoDB;

CREATE TABLE nomina_otro_pago_subsidio (
  otro_pago_id BIGINT UNSIGNED PRIMARY KEY,
  subsidio_causado DECIMAL(20,6) NOT NULL,
  CONSTRAINT fk_nomsub_otro FOREIGN KEY (otro_pago_id)
    REFERENCES nomina_otro_pago(id)
) ENGINE=InnoDB;

CREATE TABLE nomina_incapacidad (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  dias_incapacidad INT UNSIGNED NOT NULL,
  tipo_incapacidad VARCHAR(5) NOT NULL,
  importe_monetario DECIMAL(20,6) NULL,
  UNIQUE KEY uk_nominc_ordinal (documento_id, ordinal),
  CONSTRAINT fk_nominc_nomina FOREIGN KEY (documento_id)
    REFERENCES nomina(documento_id)
) ENGINE=InnoDB;

-- 09. RETENCIONES E INFORMACION DE PAGOS 2.0 (familia separada).
CREATE TABLE retencion (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  folio_interno VARCHAR(60) NULL,
  clave_retencion VARCHAR(10) NOT NULL,
  lugar_expedicion CHAR(5) NULL,
  certificado_numero VARCHAR(30) NULL,
  sello_emisor TEXT NULL,
  CONSTRAINT fk_ret_documento FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id)
) ENGINE=InnoDB;

CREATE TABLE retencion_periodo (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  mes_inicial TINYINT UNSIGNED NOT NULL,
  mes_final TINYINT UNSIGNED NOT NULL,
  ejercicio SMALLINT UNSIGNED NOT NULL,
  CONSTRAINT fk_retper_ret FOREIGN KEY (documento_id)
    REFERENCES retencion(documento_id),
  CONSTRAINT chk_retper_mes CHECK
    (mes_inicial BETWEEN 1 AND 12 AND mes_final BETWEEN 1 AND 12
     AND mes_final >= mes_inicial)
) ENGINE=InnoDB;

CREATE TABLE retencion_totales (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  monto_total_operacion DECIMAL(20,6) NOT NULL,
  monto_total_gravado DECIMAL(20,6) NOT NULL,
  monto_total_exento DECIMAL(20,6) NOT NULL,
  monto_total_retenido DECIMAL(20,6) NOT NULL,
  CONSTRAINT fk_rettot_ret FOREIGN KEY (documento_id)
    REFERENCES retencion(documento_id)
) ENGINE=InnoDB;

CREATE TABLE retencion_impuesto (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  documento_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  base_retencion DECIMAL(20,6) NULL,
  impuesto VARCHAR(10) NOT NULL,
  importe_retencion DECIMAL(20,6) NOT NULL,
  tipo_pago_retencion VARCHAR(5) NULL,
  UNIQUE KEY uk_retimp_ordinal (documento_id, ordinal),
  CONSTRAINT fk_retimp_ret FOREIGN KEY (documento_id)
    REFERENCES retencion(documento_id)
) ENGINE=InnoDB;

CREATE TABLE retencion_receptor_extranjero (
  documento_id BIGINT UNSIGNED PRIMARY KEY,
  nacionalidad VARCHAR(15) NOT NULL,
  num_registro_fiscal VARCHAR(100) NULL,
  CONSTRAINT fk_retrec_ret FOREIGN KEY (documento_id)
    REFERENCES retencion(documento_id)
) ENGINE=InnoDB;

CREATE TABLE retencion_relacion_uuid (
  documento_id BIGINT UNSIGNED NOT NULL,
  uuid_relacionado CHAR(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  PRIMARY KEY (documento_id, uuid_relacionado),
  CONSTRAINT fk_retrel_ret FOREIGN KEY (documento_id)
    REFERENCES retencion(documento_id)
) ENGINE=InnoDB;

-- 10. IMPORTACIONES Y TRAZABILIDAD (se registran intentos rechazados).
CREATE TABLE lote_importacion (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  directorio_origen VARCHAR(1024) NULL,
  inicio DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  fin DATETIME(6) NULL,
  estado VARCHAR(15) NOT NULL DEFAULT 'EN_PROCESO',
  CONSTRAINT fk_lote_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_lote_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id),
  CONSTRAINT chk_lote_estado CHECK
    (estado IN ('EN_PROCESO','FINALIZADO','PARCIAL','ERROR'))
) ENGINE=InnoDB;

CREATE TABLE importacion_archivo (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  lote_id BIGINT UNSIGNED NOT NULL,
  ordinal INT UNSIGNED NOT NULL,
  nombre_archivo VARCHAR(260) NOT NULL,
  ruta_origen VARCHAR(1024) NULL,
  sha256 BINARY(32) NULL,
  documento_id BIGINT UNSIGNED NULL,
  archivo_documental_id BIGINT UNSIGNED NULL,
  resultado VARCHAR(20) NOT NULL,
  detalle VARCHAR(1000) NULL,
  UNIQUE KEY uk_imp_archivo_ordinal (lote_id, ordinal),
  KEY idx_imp_archivo_doc (documento_id),
  CONSTRAINT fk_imparch_lote FOREIGN KEY (lote_id)
    REFERENCES lote_importacion(id),
  CONSTRAINT fk_imparch_doc FOREIGN KEY (documento_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT fk_imparch_repo FOREIGN KEY (archivo_documental_id)
    REFERENCES archivo_documental(id),
  CONSTRAINT chk_imparch_resultado CHECK
    (resultado IN ('ACEPTADO','DUPLICADO','RECHAZADO','ERROR'))
) ENGINE=InnoDB;

-- 11. CATEGORIZACION CONTABLE Y TRATAMIENTO TRIBUTARIO EDITABLE.
CREATE TABLE categoria_operacion (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  clave VARCHAR(35) NOT NULL,
  nombre VARCHAR(150) NOT NULL,
  naturaleza VARCHAR(25) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_catop_clave (contribuyente_id, clave),
  UNIQUE KEY uk_catop_id_contrib (id, contribuyente_id),
  CONSTRAINT fk_catop_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT chk_catop_nat CHECK
    (naturaleza IN ('GASTO','COSTO','GASTO_FINANCIERO','INVERSION','INGRESO','OTRA'))
) ENGINE=InnoDB;

CREATE TABLE clasificacion_concepto (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  concepto_id BIGINT UNSIGNED NOT NULL,
  categoria_id BIGINT UNSIGNED NOT NULL,
  importe_asignado DECIMAL(20,6) NOT NULL,
  nota VARCHAR(600) NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_clasconcepto_concepto (contribuyente_id, concepto_id),
  CONSTRAINT fk_clasconc_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_clasconc_concepto FOREIGN KEY (concepto_id)
    REFERENCES cfdi_concepto(id),
  CONSTRAINT fk_clasconc_categoria FOREIGN KEY (categoria_id, contribuyente_id)
    REFERENCES categoria_operacion(id, contribuyente_id),
  CONSTRAINT fk_clasconc_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id),
  CONSTRAINT chk_clasconc_importe CHECK (importe_asignado >= 0)
) ENGINE=InnoDB;

CREATE TABLE tratamiento_impuesto (
  id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  clave VARCHAR(35) NOT NULL,
  descripcion VARCHAR(250) NOT NULL,
  UNIQUE KEY uk_tratamiento_clave (clave)
) ENGINE=InnoDB;

INSERT INTO tratamiento_impuesto (clave, descripcion) VALUES
  ('PENDIENTE','Tratamiento fiscal pendiente de revisión'),
  ('IVA_ACREDITABLE','IVA clasificado preliminarmente como acreditable'),
  ('IVA_NO_ACREDITABLE','IVA clasificado como no acreditable'),
  ('IVA_PARCIAL','IVA sujeto a proporción o clasificación parcial'),
  ('OTRO','Otro tratamiento fiscal documentado')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion);

CREATE TABLE clasificacion_impuesto_concepto (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  impuesto_concepto_id BIGINT UNSIGNED NOT NULL,
  tratamiento_id SMALLINT UNSIGNED NOT NULL,
  importe_asignado DECIMAL(20,6) NOT NULL,
  justificacion VARCHAR(600) NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_clasimp_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_clasimp_impuesto FOREIGN KEY (impuesto_concepto_id)
    REFERENCES cfdi_concepto_impuesto(id),
  CONSTRAINT fk_clasimp_trat FOREIGN KEY (tratamiento_id)
    REFERENCES tratamiento_impuesto(id),
  CONSTRAINT fk_clasimp_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id),
  CONSTRAINT chk_clasimp_importe CHECK (importe_asignado >= 0)
) ENGINE=InnoDB;

CREATE TABLE periodo_fiscal (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  ejercicio SMALLINT UNSIGNED NOT NULL,
  mes TINYINT UNSIGNED NOT NULL,
  UNIQUE KEY uk_periodo_contrib_ej_mes (contribuyente_id, ejercicio, mes),
  UNIQUE KEY uk_periodo_id_contrib (id, contribuyente_id),
  CONSTRAINT fk_periodo_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT chk_periodo_mes CHECK (mes BETWEEN 1 AND 12)
) ENGINE=InnoDB;

-- La pertenencia al periodo de EMISION se obtiene de documento_fiscal.fecha_emision.
-- Aqui SOLO se registran efectos adicionales imputados a otro periodo.
CREATE TABLE efecto_fiscal_periodo (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  documento_id BIGINT UNSIGNED NOT NULL,
  periodo_id BIGINT UNSIGNED NOT NULL,
  concepto_efecto VARCHAR(45) NOT NULL,
  importe DECIMAL(20,6) NOT NULL,
  descripcion VARCHAR(600) NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_efecto_doccontrib FOREIGN KEY (contribuyente_id, documento_id)
    REFERENCES contribuyente_documento(contribuyente_id, documento_id),
  CONSTRAINT fk_efecto_periodo FOREIGN KEY (periodo_id, contribuyente_id)
    REFERENCES periodo_fiscal(id, contribuyente_id),
  CONSTRAINT fk_efecto_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id)
) ENGINE=InnoDB;

-- 12. CUENTAS POR PAGAR / COBRAR Y APLICACIONES FINANCIERAS.
CREATE TABLE obligacion (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  documento_id BIGINT UNSIGNED NULL,
  tercero_id BIGINT UNSIGNED NULL,
  naturaleza VARCHAR(10) NOT NULL,
  concepto VARCHAR(500) NULL,
  moneda VARCHAR(3) NOT NULL,
  monto_original DECIMAL(20,6) NOT NULL,
  fecha_origen DATE NOT NULL,
  fecha_vencimiento DATE NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_obligacion_documento (contribuyente_id, documento_id, naturaleza),
  KEY idx_obligacion_vencimiento (contribuyente_id, fecha_vencimiento),
  CONSTRAINT fk_obliga_contrib_doc FOREIGN KEY (contribuyente_id, documento_id)
    REFERENCES contribuyente_documento(contribuyente_id, documento_id),
  CONSTRAINT fk_obliga_tercero FOREIGN KEY (tercero_id)
    REFERENCES tercero(id),
  CONSTRAINT chk_obliga_naturaleza CHECK (naturaleza IN ('PAGAR','COBRAR')),
  CONSTRAINT chk_obliga_monto CHECK (monto_original >= 0)
) ENGINE=InnoDB;

-- Los movimientos operativos NO se consideran prueba fiscal sin evidencia.
CREATE TABLE movimiento_pago (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  tercero_id BIGINT UNSIGNED NULL,
  naturaleza VARCHAR(10) NOT NULL,
  fecha_movimiento DATETIME NOT NULL,
  moneda VARCHAR(3) NOT NULL,
  importe DECIMAL(20,6) NOT NULL,
  origen VARCHAR(15) NOT NULL,
  referencia VARCHAR(160) NULL,
  observaciones VARCHAR(1000) NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_movpago_contrib_fecha (contribuyente_id, fecha_movimiento),
  UNIQUE KEY uk_movpago_id_contrib (id, contribuyente_id),
  CONSTRAINT fk_movpago_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_movpago_tercero FOREIGN KEY (tercero_id)
    REFERENCES tercero(id),
  CONSTRAINT fk_movpago_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id),
  CONSTRAINT chk_movpago_nat CHECK (naturaleza IN ('PAGO','COBRO')),
  CONSTRAINT chk_movpago_origen CHECK
    (origen IN ('MANUAL','COMPLEMENTO','BANCO','OTRO')),
  CONSTRAINT chk_movpago_importe CHECK (importe > 0)
) ENGINE=InnoDB;

-- M:N: un pago liquida varias obligaciones o una obligacion recibe varios pagos.
CREATE TABLE aplicacion_pago (
  movimiento_pago_id BIGINT UNSIGNED NOT NULL,
  obligacion_id BIGINT UNSIGNED NOT NULL,
  importe_moneda_pago DECIMAL(20,6) NOT NULL,
  importe_moneda_obligacion DECIMAL(20,6) NOT NULL,
  PRIMARY KEY (movimiento_pago_id, obligacion_id),
  CONSTRAINT fk_apago_mov FOREIGN KEY (movimiento_pago_id)
    REFERENCES movimiento_pago(id),
  CONSTRAINT fk_apago_obligacion FOREIGN KEY (obligacion_id)
    REFERENCES obligacion(id),
  CONSTRAINT chk_apago_importes CHECK
    (importe_moneda_pago > 0 AND importe_moneda_obligacion > 0)
) ENGINE=InnoDB;

-- Evidencia: el registro del complemento y el movimiento operativo son distintos.
CREATE TABLE movimiento_pago_complemento (
  movimiento_pago_id BIGINT UNSIGNED NOT NULL,
  pago_fiscal_documento_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (movimiento_pago_id, pago_fiscal_documento_id),
  CONSTRAINT fk_mpc_mov FOREIGN KEY (movimiento_pago_id)
    REFERENCES movimiento_pago(id),
  CONSTRAINT fk_mpc_pagofiscal FOREIGN KEY (pago_fiscal_documento_id)
    REFERENCES pago_fiscal_documento(id)
) ENGINE=InnoDB;

CREATE TABLE ajuste_obligacion (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  obligacion_id BIGINT UNSIGNED NOT NULL,
  documento_soporte_id BIGINT UNSIGNED NULL,
  tipo VARCHAR(25) NOT NULL,
  fecha_ajuste DATE NOT NULL,
  importe_moneda_obligacion DECIMAL(20,6) NOT NULL,
  observacion VARCHAR(600) NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  CONSTRAINT fk_ajuste_obligacion FOREIGN KEY (obligacion_id)
    REFERENCES obligacion(id),
  CONSTRAINT fk_ajuste_doc FOREIGN KEY (documento_soporte_id)
    REFERENCES documento_fiscal(id),
  CONSTRAINT fk_ajuste_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id),
  CONSTRAINT chk_ajuste_tipo CHECK
    (tipo IN ('NOTA_CREDITO','DESCUENTO','DEVOLUCION','ANTICIPO','OTRO')),
  CONSTRAINT chk_ajuste_importe CHECK (importe_moneda_obligacion > 0)
) ENGINE=InnoDB;

-- 13. CUENTAS BANCARIAS, ESTADOS DE CUENTA Y CONCILIACION.
CREATE TABLE banco (
  id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  clave VARCHAR(12) NULL,
  nombre VARCHAR(150) NOT NULL,
  UNIQUE KEY uk_banco_clave (clave)
) ENGINE=InnoDB;

CREATE TABLE cuenta_bancaria (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  banco_id SMALLINT UNSIGNED NOT NULL,
  alias VARCHAR(100) NOT NULL,
  numero_enmascarado VARCHAR(32) NULL,
  clabe CHAR(18) NULL,
  moneda VARCHAR(3) NOT NULL,
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_cuenta_clabe (clabe),
  UNIQUE KEY uk_cuenta_id_contrib (id, contribuyente_id),
  CONSTRAINT fk_cuenta_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_cuenta_banco FOREIGN KEY (banco_id)
    REFERENCES banco(id)
) ENGINE=InnoDB;

CREATE TABLE importacion_estado_bancario (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  cuenta_bancaria_id BIGINT UNSIGNED NOT NULL,
  archivo_documental_id BIGINT UNSIGNED NULL,
  formato_origen VARCHAR(30) NOT NULL,
  periodo_desde DATE NULL,
  periodo_hasta DATE NULL,
  importado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  usuario_id BIGINT UNSIGNED NOT NULL,
  KEY idx_estado_bancario_cuenta (cuenta_bancaria_id, importado_en),
  CONSTRAINT fk_ieb_cuenta FOREIGN KEY (cuenta_bancaria_id)
    REFERENCES cuenta_bancaria(id),
  CONSTRAINT fk_ieb_archivo FOREIGN KEY (archivo_documental_id)
    REFERENCES archivo_documental(id),
  CONSTRAINT fk_ieb_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE movimiento_bancario (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  importacion_estado_id BIGINT UNSIGNED NOT NULL,
  ordinal INT UNSIGNED NOT NULL,
  fecha_operacion DATE NOT NULL,
  fecha_valor DATE NULL,
  naturaleza VARCHAR(5) NOT NULL,
  importe DECIMAL(20,6) NOT NULL,
  saldo_reportado DECIMAL(20,6) NULL,
  descripcion VARCHAR(1000) NULL,
  referencia VARCHAR(200) NULL,
  identificador_bancario VARCHAR(120) NULL,
  UNIQUE KEY uk_bancmov_ordinal (importacion_estado_id, ordinal),
  KEY idx_bancmov_fecha (fecha_operacion, naturaleza),
  CONSTRAINT fk_bancmov_import FOREIGN KEY (importacion_estado_id)
    REFERENCES importacion_estado_bancario(id),
  CONSTRAINT chk_bancmov_nat CHECK (naturaleza IN ('CARGO','ABONO')),
  CONSTRAINT chk_bancmov_importe CHECK (importe > 0)
) ENGINE=InnoDB;

-- Estado propuesto/confirmado sin aceptar automaticamente coincidencias.
CREATE TABLE conciliacion_bancaria (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  movimiento_bancario_id BIGINT UNSIGNED NOT NULL,
  movimiento_pago_id BIGINT UNSIGNED NOT NULL,
  importe_conciliado DECIMAL(20,6) NOT NULL,
  estado VARCHAR(15) NOT NULL DEFAULT 'PROPUESTO',
  puntuacion DECIMAL(5,4) NULL,
  explicacion VARCHAR(500) NULL,
  revisado_por BIGINT UNSIGNED NULL,
  revisado_en DATETIME(6) NULL,
  UNIQUE KEY uk_conciliacion_par (movimiento_bancario_id, movimiento_pago_id),
  CONSTRAINT fk_conc_banc FOREIGN KEY (movimiento_bancario_id)
    REFERENCES movimiento_bancario(id),
  CONSTRAINT fk_conc_pago FOREIGN KEY (movimiento_pago_id)
    REFERENCES movimiento_pago(id),
  CONSTRAINT fk_conc_revisor FOREIGN KEY (revisado_por)
    REFERENCES usuario(id),
  CONSTRAINT chk_conc_estado CHECK
    (estado IN ('PROPUESTO','CONFIRMADO','RECHAZADO')),
  CONSTRAINT chk_conc_importe CHECK (importe_conciliado > 0),
  CONSTRAINT chk_conc_score CHECK
    (puntuacion IS NULL OR puntuacion BETWEEN 0 AND 1)
) ENGINE=InnoDB;

-- 14. REPORTES, EXPORTACIONES Y AUDITORIA.
CREATE TABLE exportacion_reporte (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  contribuyente_id BIGINT UNSIGNED NOT NULL,
  usuario_id BIGINT UNSIGNED NOT NULL,
  tipo_reporte VARCHAR(80) NOT NULL,
  formato VARCHAR(15) NOT NULL,
  archivo_documental_id BIGINT UNSIGNED NULL,
  solicitado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_export_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id),
  CONSTRAINT fk_export_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id),
  CONSTRAINT fk_export_archivo FOREIGN KEY (archivo_documental_id)
    REFERENCES archivo_documental(id),
  CONSTRAINT chk_export_formato CHECK
    (formato IN ('PDF','XLSX','CSV','TXT','MD'))
) ENGINE=InnoDB;

CREATE TABLE exportacion_filtro (
  exportacion_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  campo VARCHAR(80) NOT NULL,
  operador VARCHAR(18) NOT NULL,
  valor VARCHAR(500) NOT NULL,
  PRIMARY KEY (exportacion_id, ordinal),
  CONSTRAINT fk_expfiltro_export FOREIGN KEY (exportacion_id)
    REFERENCES exportacion_reporte(id)
) ENGINE=InnoDB;

CREATE TABLE bitacora_evento (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  usuario_id BIGINT UNSIGNED NULL,
  contribuyente_id BIGINT UNSIGNED NULL,
  entidad VARCHAR(85) NOT NULL,
  entidad_id BIGINT UNSIGNED NULL,
  accion VARCHAR(50) NOT NULL,
  ocurrido_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  motivo VARCHAR(600) NULL,
  KEY idx_evento_entidad (entidad, entidad_id),
  KEY idx_evento_fecha (ocurrido_en),
  CONSTRAINT fk_evento_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id),
  CONSTRAINT fk_evento_contrib FOREIGN KEY (contribuyente_id)
    REFERENCES contribuyente(id)
) ENGINE=InnoDB;

CREATE TABLE bitacora_cambio (
  evento_id BIGINT UNSIGNED NOT NULL,
  atributo VARCHAR(85) NOT NULL,
  valor_anterior TEXT NULL,
  valor_nuevo TEXT NULL,
  PRIMARY KEY (evento_id, atributo),
  CONSTRAINT fk_cambio_evento FOREIGN KEY (evento_id)
    REFERENCES bitacora_evento(id)
) ENGINE=InnoDB;

-- 15. VISTAS: INFORMACION DERIVADA, NO CAMPOS REDUNDANTES.
CREATE OR REPLACE VIEW v_cfdi_periodo_emision AS
SELECT cd.contribuyente_id,
       cd.sentido,
       d.id AS documento_id,
       d.uuid,
       d.fecha_emision,
       YEAR(d.fecha_emision) AS ejercicio_emision,
       MONTH(d.fecha_emision) AS mes_emision,
       c.tipo,
       c.moneda,
       c.subtotal,
       c.total
  FROM contribuyente_documento cd
  JOIN documento_fiscal d ON d.id = cd.documento_id
  JOIN cfdi c ON c.documento_id = d.id;

CREATE OR REPLACE VIEW v_saldo_obligacion AS
SELECT o.id AS obligacion_id,
       o.contribuyente_id,
       o.documento_id,
       o.naturaleza,
       o.moneda,
       o.monto_original,
       COALESCE(p.pagos, 0) AS pagos_aplicados,
       COALESCE(a.ajustes, 0) AS ajustes_aplicados,
       o.monto_original - COALESCE(p.pagos, 0) - COALESCE(a.ajustes, 0)
         AS saldo_calculado,
       CASE
         WHEN o.monto_original - COALESCE(p.pagos, 0) - COALESCE(a.ajustes, 0) < 0
           THEN 'SOBREAPLICADO'
         WHEN o.monto_original - COALESCE(p.pagos, 0) - COALESCE(a.ajustes, 0) = 0
           THEN 'LIQUIDADO'
         WHEN COALESCE(p.pagos, 0) + COALESCE(a.ajustes, 0) = 0
           THEN 'PENDIENTE'
         ELSE 'PARCIAL'
       END AS estado_calculado
  FROM obligacion o
  LEFT JOIN (
    SELECT obligacion_id, SUM(importe_moneda_obligacion) AS pagos
      FROM aplicacion_pago GROUP BY obligacion_id
  ) p ON p.obligacion_id = o.id
  LEFT JOIN (
    SELECT obligacion_id, SUM(importe_moneda_obligacion) AS ajustes
      FROM ajuste_obligacion GROUP BY obligacion_id
  ) a ON a.obligacion_id = o.id;

-- 16. REGLAS DE INTEGRIDAD NO EXPRESABLES CON FK/CHECK SIMPLES.
-- DELIMITER es una instruccion del cliente mysql; ejecutar como SCRIPT SQL.
DELIMITER $$

-- Un contribuyente solo puede asociar CFDI donde su RFC aparece como
-- emisor y/o receptor segun el sentido indicado. Los participantes se insertan antes.
CREATE TRIGGER trg_contrib_doc_sentido_bi BEFORE INSERT ON contribuyente_documento FOR EACH ROW
BEGIN
  IF NEW.sentido IN ('EMITIDO','AMBOS') AND NOT EXISTS (
       SELECT 1 FROM contribuyente c
       JOIN documento_participante p ON p.rfc = c.rfc
       WHERE c.id = NEW.contribuyente_id
         AND p.documento_id = NEW.documento_id AND p.rol = 'E') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El RFC del contribuyente no coincide con el emisor';
  END IF;
  IF NEW.sentido IN ('RECIBIDO','AMBOS') AND NOT EXISTS (
       SELECT 1 FROM contribuyente c
       JOIN documento_participante p ON p.rfc = c.rfc
       WHERE c.id = NEW.contribuyente_id
         AND p.documento_id = NEW.documento_id AND p.rol = 'R') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El RFC del contribuyente no coincide con el receptor';
  END IF;
END$$

CREATE TRIGGER trg_contrib_doc_sentido_bu BEFORE UPDATE ON contribuyente_documento FOR EACH ROW
BEGIN
  IF NEW.sentido IN ('EMITIDO','AMBOS') AND NOT EXISTS (
       SELECT 1 FROM contribuyente c
       JOIN documento_participante p ON p.rfc = c.rfc
       WHERE c.id = NEW.contribuyente_id
         AND p.documento_id = NEW.documento_id AND p.rol = 'E') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El RFC del contribuyente no coincide con el emisor';
  END IF;
  IF NEW.sentido IN ('RECIBIDO','AMBOS') AND NOT EXISTS (
       SELECT 1 FROM contribuyente c
       JOIN documento_participante p ON p.rfc = c.rfc
       WHERE c.id = NEW.contribuyente_id
         AND p.documento_id = NEW.documento_id AND p.rol = 'R') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El RFC del contribuyente no coincide con el receptor';
  END IF;
END$$

-- Impedir que la clasificacion de una factura se asigne a otro contribuyente.
CREATE TRIGGER trg_clas_concepto_tenant_bi BEFORE INSERT ON clasificacion_concepto FOR EACH ROW
BEGIN
  IF NOT EXISTS (
       SELECT 1 FROM contribuyente_documento cd
       JOIN cfdi_concepto cc ON cc.documento_id = cd.documento_id
       WHERE cc.id = NEW.concepto_id AND cd.contribuyente_id = NEW.contribuyente_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El concepto no corresponde al contribuyente';
  END IF;
END$$

CREATE TRIGGER trg_clas_concepto_tenant_bu BEFORE UPDATE ON clasificacion_concepto FOR EACH ROW
BEGIN
  IF NOT EXISTS (
       SELECT 1 FROM contribuyente_documento cd
       JOIN cfdi_concepto cc ON cc.documento_id = cd.documento_id
       WHERE cc.id = NEW.concepto_id AND cd.contribuyente_id = NEW.contribuyente_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El concepto no corresponde al contribuyente';
  END IF;
END$$

CREATE TRIGGER trg_clas_impuesto_tenant_bi BEFORE INSERT ON clasificacion_impuesto_concepto FOR EACH ROW
BEGIN
  IF NOT EXISTS (
       SELECT 1 FROM contribuyente_documento cd
       JOIN cfdi_concepto cc ON cc.documento_id = cd.documento_id
       JOIN cfdi_concepto_impuesto ci ON ci.concepto_id = cc.id
       WHERE ci.id = NEW.impuesto_concepto_id AND cd.contribuyente_id = NEW.contribuyente_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El impuesto no corresponde al contribuyente';
  END IF;
END$$

CREATE TRIGGER trg_clas_impuesto_tenant_bu BEFORE UPDATE ON clasificacion_impuesto_concepto FOR EACH ROW
BEGIN
  IF NOT EXISTS (
       SELECT 1 FROM contribuyente_documento cd
       JOIN cfdi_concepto cc ON cc.documento_id = cd.documento_id
       JOIN cfdi_concepto_impuesto ci ON ci.concepto_id = cc.id
       WHERE ci.id = NEW.impuesto_concepto_id AND cd.contribuyente_id = NEW.contribuyente_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El impuesto no corresponde al contribuyente';
  END IF;
END$$

CREATE TRIGGER trg_cfdi_clase_bi BEFORE INSERT ON cfdi FOR EACH ROW
BEGIN
  IF (SELECT clase FROM documento_fiscal WHERE id = NEW.documento_id) <> 'CFDI' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El documento no pertenece a la familia CFDI';
  END IF;
END$$

CREATE TRIGGER trg_retencion_clase_bi BEFORE INSERT ON retencion FOR EACH ROW
BEGIN
  IF (SELECT clase FROM documento_fiscal WHERE id = NEW.documento_id) <> 'RETENCION' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El documento no pertenece a retenciones';
  END IF;
END$$

CREATE TRIGGER trg_pago_fiscal_tipo_bi BEFORE INSERT ON pago_fiscal FOR EACH ROW
BEGIN
  IF (SELECT tipo FROM cfdi WHERE documento_id = NEW.documento_id) <> 'P' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El complemento de pagos requiere CFDI tipo P';
  END IF;
END$$

CREATE TRIGGER trg_nomina_tipo_bi BEFORE INSERT ON nomina FOR EACH ROW
BEGIN
  IF (SELECT tipo FROM cfdi WHERE documento_id = NEW.documento_id) <> 'N' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El complemento de nomina requiere CFDI tipo N';
  END IF;
END$$

-- No permitir asociar pagos de un contribuyente a obligaciones de otro.
CREATE TRIGGER trg_aplicacion_pago_tenant_bi BEFORE INSERT ON aplicacion_pago FOR EACH ROW
BEGIN
  IF (SELECT contribuyente_id FROM movimiento_pago WHERE id = NEW.movimiento_pago_id)
     <> (SELECT contribuyente_id FROM obligacion WHERE id = NEW.obligacion_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pago y obligacion son de contribuyentes distintos';
  END IF;
END$$

CREATE TRIGGER trg_aplicacion_pago_tenant_bu BEFORE UPDATE ON aplicacion_pago FOR EACH ROW
BEGIN
  IF (SELECT contribuyente_id FROM movimiento_pago WHERE id = NEW.movimiento_pago_id)
     <> (SELECT contribuyente_id FROM obligacion WHERE id = NEW.obligacion_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pago y obligacion son de contribuyentes distintos';
  END IF;
END$$

CREATE TRIGGER trg_conciliacion_tenant_bi BEFORE INSERT ON conciliacion_bancaria FOR EACH ROW
BEGIN
  IF (SELECT cb.contribuyente_id
        FROM movimiento_bancario mb
        JOIN importacion_estado_bancario eb ON eb.id = mb.importacion_estado_id
        JOIN cuenta_bancaria cb ON cb.id = eb.cuenta_bancaria_id
       WHERE mb.id = NEW.movimiento_bancario_id)
     <> (SELECT contribuyente_id FROM movimiento_pago WHERE id = NEW.movimiento_pago_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cuenta y movimiento corresponden a contribuyentes distintos';
  END IF;
END$$

CREATE TRIGGER trg_conciliacion_tenant_bu BEFORE UPDATE ON conciliacion_bancaria FOR EACH ROW
BEGIN
  IF (SELECT cb.contribuyente_id
        FROM movimiento_bancario mb
        JOIN importacion_estado_bancario eb ON eb.id = mb.importacion_estado_id
        JOIN cuenta_bancaria cb ON cb.id = eb.cuenta_bancaria_id
       WHERE mb.id = NEW.movimiento_bancario_id)
     <> (SELECT contribuyente_id FROM movimiento_pago WHERE id = NEW.movimiento_pago_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cuenta y movimiento corresponden a contribuyentes distintos';
  END IF;
END$$

DELIMITER ;

-- FIN DEL ESQUEMA v1.0
