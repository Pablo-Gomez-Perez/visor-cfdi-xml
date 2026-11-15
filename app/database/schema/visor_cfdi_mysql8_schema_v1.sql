-- visor_cfdi.banco definición

CREATE TABLE `banco` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `clave` varchar(12) DEFAULT NULL,
  `nombre` varchar(150) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_banco_clave` (`clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cat_tipo_cfdi definición

CREATE TABLE `cat_tipo_cfdi` (
  `clave` char(1) NOT NULL,
  `descripcion` varchar(80) NOT NULL,
  PRIMARY KEY (`clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.contribuyente definición

CREATE TABLE `contribuyente` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `rfc` varchar(13) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `nombre_razon_social` varchar(300) NOT NULL,
  `codigo_postal_fiscal` char(5) DEFAULT NULL,
  `correo_contacto` varchar(254) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contribuyente_rfc` (`rfc`),
  CONSTRAINT `chk_contribuyente_rfc` CHECK ((char_length(`rfc`) between 12 and 13))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.documento_fiscal definición

CREATE TABLE `documento_fiscal` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `clase` varchar(15) NOT NULL,
  `uuid` char(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `version_estandar` varchar(8) NOT NULL,
  `fecha_emision` datetime NOT NULL,
  `registrado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_uuid` (`uuid`),
  KEY `idx_doc_fecha_emision` (`fecha_emision`),
  CONSTRAINT `chk_doc_clase` CHECK ((`clase` in (_utf8mb4'CFDI',_utf8mb4'RETENCION'))),
  CONSTRAINT `chk_doc_uuid` CHECK (regexp_like(`uuid`,_utf8mb4'^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$'))
) ENGINE=InnoDB AUTO_INCREMENT=157 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.permiso definición

CREATE TABLE `permiso` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `recurso` varchar(70) NOT NULL,
  `accion` varchar(40) NOT NULL,
  `descripcion` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permiso_recurso_accion` (`recurso`,`accion`)
) ENGINE=InnoDB AUTO_INCREMENT=379 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.repositorio_documental definición

CREATE TABLE `repositorio_documental` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `nombre` varchar(120) NOT NULL,
  `proveedor` varchar(30) NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_repositorio_nombre` (`nombre`),
  CONSTRAINT `chk_repo_proveedor` CHECK ((`proveedor` in (_utf8mb4'LOCAL',_utf8mb4'S3',_utf8mb4'DROPBOX',_utf8mb4'MEGA',_utf8mb4'OTRO')))
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.rol definición

CREATE TABLE `rol` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `clave` varchar(40) NOT NULL,
  `descripcion` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rol_clave` (`clave`)
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.sat_catalogo definición

CREATE TABLE `sat_catalogo` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `nombre` varchar(90) NOT NULL,
  `descripcion` varchar(250) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sat_catalogo_nombre` (`nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.tercero definición

CREATE TABLE `tercero` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `rfc` varchar(13) CHARACTER SET ascii COLLATE ascii_general_ci DEFAULT NULL,
  `nombre_razon_social` varchar(300) NOT NULL,
  `pais_fiscal` char(3) DEFAULT NULL,
  `num_registro_fiscal_extranjero` varchar(100) DEFAULT NULL,
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_tercero_rfc` (`rfc`),
  KEY `idx_tercero_nombre` (`nombre_razon_social`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.tipo_complemento definición

CREATE TABLE `tipo_complemento` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `espacio_nombres` varchar(400) NOT NULL,
  `version` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_namespace` (`espacio_nombres`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.tratamiento_impuesto definición

CREATE TABLE `tratamiento_impuesto` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `clave` varchar(35) NOT NULL,
  `descripcion` varchar(250) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tratamiento_clave` (`clave`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.usuario definición

CREATE TABLE `usuario` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `nombre_completo` varchar(200) NOT NULL,
  `login` varchar(80) NOT NULL,
  `correo` varchar(254) DEFAULT NULL,
  `hash_contrasena` varchar(255) NOT NULL,
  `estado` varchar(15) NOT NULL DEFAULT 'ACTIVO',
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `ultimo_acceso` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_usuario_login` (`login`),
  UNIQUE KEY `uk_usuario_correo` (`correo`),
  CONSTRAINT `chk_usuario_estado` CHECK ((`estado` in (_utf8mb4'ACTIVO',_utf8mb4'INACTIVO',_utf8mb4'BLOQUEADO')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.archivo_documental definición

CREATE TABLE `archivo_documental` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `repositorio_id` bigint unsigned NOT NULL,
  `clave_objeto` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `sha256` binary(32) NOT NULL,
  `tamano_bytes` bigint unsigned NOT NULL,
  `tipo_mime` varchar(100) NOT NULL DEFAULT 'application/xml',
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_archivo_ubicacion` (`repositorio_id`,`clave_objeto`),
  KEY `idx_archivo_sha256` (`sha256`),
  CONSTRAINT `fk_archivo_repositorio` FOREIGN KEY (`repositorio_id`) REFERENCES `repositorio_documental` (`id`),
  CONSTRAINT `chk_archivo_tamano` CHECK ((`tamano_bytes` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=157 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.bitacora_evento definición

CREATE TABLE `bitacora_evento` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `usuario_id` bigint unsigned DEFAULT NULL,
  `contribuyente_id` bigint unsigned DEFAULT NULL,
  `entidad` varchar(85) NOT NULL,
  `entidad_id` bigint unsigned DEFAULT NULL,
  `accion` varchar(50) NOT NULL,
  `ocurrido_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `motivo` varchar(600) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_evento_entidad` (`entidad`,`entidad_id`),
  KEY `idx_evento_fecha` (`ocurrido_en`),
  KEY `fk_evento_usuario` (`usuario_id`),
  KEY `fk_evento_contrib` (`contribuyente_id`),
  CONSTRAINT `fk_evento_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_evento_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=176 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.categoria_operacion definición

CREATE TABLE `categoria_operacion` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `clave` varchar(35) NOT NULL,
  `nombre` varchar(150) NOT NULL,
  `naturaleza` varchar(25) NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_catop_clave` (`contribuyente_id`,`clave`),
  UNIQUE KEY `uk_catop_id_contrib` (`id`,`contribuyente_id`),
  CONSTRAINT `fk_catop_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `chk_catop_nat` CHECK ((`naturaleza` in (_utf8mb4'GASTO',_utf8mb4'COSTO',_utf8mb4'GASTO_FINANCIERO',_utf8mb4'INVERSION',_utf8mb4'INGRESO',_utf8mb4'OTRA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi definición

CREATE TABLE `cfdi` (
  `documento_id` bigint unsigned NOT NULL,
  `tipo` char(1) NOT NULL,
  `serie` varchar(40) DEFAULT NULL,
  `folio` varchar(40) DEFAULT NULL,
  `lugar_expedicion` char(5) DEFAULT NULL,
  `moneda` varchar(3) NOT NULL,
  `tipo_cambio` decimal(20,6) DEFAULT NULL,
  `subtotal` decimal(20,6) NOT NULL,
  `descuento` decimal(20,6) DEFAULT NULL,
  `total` decimal(20,6) NOT NULL,
  `forma_pago` varchar(5) DEFAULT NULL,
  `metodo_pago` varchar(5) DEFAULT NULL,
  `condiciones_pago` varchar(1000) DEFAULT NULL,
  `exportacion` varchar(5) DEFAULT NULL,
  `confirmacion` varchar(20) DEFAULT NULL,
  `no_certificado` varchar(30) DEFAULT NULL,
  `sello_emisor` text,
  PRIMARY KEY (`documento_id`),
  KEY `idx_cfdi_tipo` (`tipo`),
  KEY `idx_cfdi_serie_folio` (`serie`,`folio`),
  CONSTRAINT `fk_cfdi_documento` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `fk_cfdi_tipo` FOREIGN KEY (`tipo`) REFERENCES `cat_tipo_cfdi` (`clave`),
  CONSTRAINT `chk_cfdi_importes` CHECK (((`subtotal` >= 0) and (`total` >= 0) and ((`descuento` is null) or (`descuento` >= 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_concepto definición

CREATE TABLE `cfdi_concepto` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `clave_prod_serv` varchar(12) NOT NULL,
  `no_identificacion` varchar(100) DEFAULT NULL,
  `cantidad` decimal(20,6) NOT NULL,
  `clave_unidad` varchar(10) NOT NULL,
  `unidad` varchar(50) DEFAULT NULL,
  `descripcion` text NOT NULL,
  `valor_unitario` decimal(20,6) NOT NULL,
  `importe` decimal(20,6) NOT NULL,
  `descuento` decimal(20,6) DEFAULT NULL,
  `objeto_impuesto` varchar(5) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_concepto_ordinal` (`documento_id`,`ordinal`),
  CONSTRAINT `fk_concepto_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`),
  CONSTRAINT `chk_concepto_montos` CHECK (((`ordinal` > 0) and (`cantidad` >= 0) and (`valor_unitario` >= 0) and (`importe` >= 0) and ((`descuento` is null) or (`descuento` >= 0))))
) ENGINE=InnoDB AUTO_INCREMENT=1438 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_concepto_cuenta_tercero definición

CREATE TABLE `cfdi_concepto_cuenta_tercero` (
  `concepto_id` bigint unsigned NOT NULL,
  `rfc` varchar(13) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `nombre` varchar(300) NOT NULL,
  `regimen_fiscal` varchar(5) NOT NULL,
  `domicilio_fiscal_cp` char(5) NOT NULL,
  PRIMARY KEY (`concepto_id`),
  CONSTRAINT `fk_cuentatercero_concepto` FOREIGN KEY (`concepto_id`) REFERENCES `cfdi_concepto` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_concepto_impuesto definición

CREATE TABLE `cfdi_concepto_impuesto` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `concepto_id` bigint unsigned NOT NULL,
  `movimiento` varchar(10) NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `impuesto` varchar(5) NOT NULL,
  `tipo_factor` varchar(12) DEFAULT NULL,
  `base` decimal(20,6) NOT NULL,
  `tasa_cuota` decimal(18,6) DEFAULT NULL,
  `importe` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_concepto_imp_ordinal` (`concepto_id`,`movimiento`,`ordinal`),
  KEY `idx_cimp_impuesto` (`impuesto`,`tipo_factor`,`tasa_cuota`),
  CONSTRAINT `fk_cimp_concepto` FOREIGN KEY (`concepto_id`) REFERENCES `cfdi_concepto` (`id`),
  CONSTRAINT `chk_cimp_montos` CHECK (((`ordinal` > 0) and (`base` >= 0) and ((`importe` is null) or (`importe` >= 0)))),
  CONSTRAINT `chk_cimp_mov` CHECK ((`movimiento` in (_utf8mb4'TRASLADO',_utf8mb4'RETENCION')))
) ENGINE=InnoDB AUTO_INCREMENT=1256 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_concepto_parte definición

CREATE TABLE `cfdi_concepto_parte` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `concepto_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `clave_prod_serv` varchar(12) NOT NULL,
  `no_identificacion` varchar(100) DEFAULT NULL,
  `cantidad` decimal(20,6) NOT NULL,
  `unidad` varchar(50) DEFAULT NULL,
  `descripcion` text NOT NULL,
  `valor_unitario` decimal(20,6) DEFAULT NULL,
  `importe` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cparte_ordinal` (`concepto_id`,`ordinal`),
  CONSTRAINT `fk_cparte_concepto` FOREIGN KEY (`concepto_id`) REFERENCES `cfdi_concepto` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_concepto_parte_pedimento definición

CREATE TABLE `cfdi_concepto_parte_pedimento` (
  `parte_id` bigint unsigned NOT NULL,
  `numero_pedimento` varchar(35) NOT NULL,
  PRIMARY KEY (`parte_id`,`numero_pedimento`),
  CONSTRAINT `fk_cparte_ped` FOREIGN KEY (`parte_id`) REFERENCES `cfdi_concepto_parte` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_concepto_pedimento definición

CREATE TABLE `cfdi_concepto_pedimento` (
  `concepto_id` bigint unsigned NOT NULL,
  `numero_pedimento` varchar(35) NOT NULL,
  PRIMARY KEY (`concepto_id`,`numero_pedimento`),
  CONSTRAINT `fk_cped_concepto` FOREIGN KEY (`concepto_id`) REFERENCES `cfdi_concepto` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_impuesto_global definición

CREATE TABLE `cfdi_impuesto_global` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `movimiento` varchar(10) NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `impuesto` varchar(5) NOT NULL,
  `tipo_factor` varchar(12) DEFAULT NULL,
  `base` decimal(20,6) DEFAULT NULL,
  `tasa_cuota` decimal(18,6) DEFAULT NULL,
  `importe` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gimp_ordinal` (`documento_id`,`movimiento`,`ordinal`),
  CONSTRAINT `fk_gimp_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`),
  CONSTRAINT `chk_gimp_mov` CHECK ((`movimiento` in (_utf8mb4'TRASLADO',_utf8mb4'RETENCION'))),
  CONSTRAINT `chk_gimp_positivos` CHECK (((`ordinal` > 0) and ((`importe` is null) or (`importe` >= 0)) and ((`base` is null) or (`base` >= 0))))
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_impuesto_totales definición

CREATE TABLE `cfdi_impuesto_totales` (
  `documento_id` bigint unsigned NOT NULL,
  `total_trasladados` decimal(20,6) DEFAULT NULL,
  `total_retenidos` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_cimptot_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_informacion_global definición

CREATE TABLE `cfdi_informacion_global` (
  `documento_id` bigint unsigned NOT NULL,
  `periodicidad` varchar(5) NOT NULL,
  `meses` varchar(5) NOT NULL,
  `ejercicio` smallint unsigned NOT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_cglobal_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_relacion_grupo definición

CREATE TABLE `cfdi_relacion_grupo` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `tipo_relacion` varchar(5) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rel_grupo_ordinal` (`documento_id`,`ordinal`),
  CONSTRAINT `fk_relgrupo_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`),
  CONSTRAINT `chk_relgrupo_ordinal` CHECK ((`ordinal` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cfdi_relacion_uuid definición

CREATE TABLE `cfdi_relacion_uuid` (
  `grupo_id` bigint unsigned NOT NULL,
  `uuid_relacionado` char(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  PRIMARY KEY (`grupo_id`,`uuid_relacionado`),
  KEY `idx_relacion_uuid` (`uuid_relacionado`),
  CONSTRAINT `fk_rel_uuid_grupo` FOREIGN KEY (`grupo_id`) REFERENCES `cfdi_relacion_grupo` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.clasificacion_concepto definición

CREATE TABLE `clasificacion_concepto` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `concepto_id` bigint unsigned NOT NULL,
  `categoria_id` bigint unsigned NOT NULL,
  `importe_asignado` decimal(20,6) NOT NULL,
  `nota` varchar(600) DEFAULT NULL,
  `usuario_id` bigint unsigned NOT NULL,
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_clasconcepto_concepto` (`contribuyente_id`,`concepto_id`),
  KEY `fk_clasconc_concepto` (`concepto_id`),
  KEY `fk_clasconc_categoria` (`categoria_id`,`contribuyente_id`),
  KEY `fk_clasconc_usuario` (`usuario_id`),
  CONSTRAINT `fk_clasconc_categoria` FOREIGN KEY (`categoria_id`, `contribuyente_id`) REFERENCES `categoria_operacion` (`id`, `contribuyente_id`),
  CONSTRAINT `fk_clasconc_concepto` FOREIGN KEY (`concepto_id`) REFERENCES `cfdi_concepto` (`id`),
  CONSTRAINT `fk_clasconc_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_clasconc_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `chk_clasconc_importe` CHECK ((`importe_asignado` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.clasificacion_impuesto_concepto definición

CREATE TABLE `clasificacion_impuesto_concepto` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `impuesto_concepto_id` bigint unsigned NOT NULL,
  `tratamiento_id` smallint unsigned NOT NULL,
  `importe_asignado` decimal(20,6) NOT NULL,
  `justificacion` varchar(600) DEFAULT NULL,
  `usuario_id` bigint unsigned NOT NULL,
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_clasimp_contrib` (`contribuyente_id`),
  KEY `fk_clasimp_impuesto` (`impuesto_concepto_id`),
  KEY `fk_clasimp_trat` (`tratamiento_id`),
  KEY `fk_clasimp_usuario` (`usuario_id`),
  CONSTRAINT `fk_clasimp_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_clasimp_impuesto` FOREIGN KEY (`impuesto_concepto_id`) REFERENCES `cfdi_concepto_impuesto` (`id`),
  CONSTRAINT `fk_clasimp_trat` FOREIGN KEY (`tratamiento_id`) REFERENCES `tratamiento_impuesto` (`id`),
  CONSTRAINT `fk_clasimp_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `chk_clasimp_importe` CHECK ((`importe_asignado` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.consulta_estado_sat definición

CREATE TABLE `consulta_estado_sat` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `estado` varchar(25) NOT NULL,
  `es_cancelable` varchar(50) DEFAULT NULL,
  `estado_cancelacion` varchar(70) DEFAULT NULL,
  `fecha_consulta` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `referencia_consulta` varchar(180) DEFAULT NULL,
  `detalle_error` varchar(600) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_estado_doc_fecha` (`documento_id`,`fecha_consulta`),
  CONSTRAINT `fk_estado_documento` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `chk_estado_sat` CHECK ((`estado` in (_utf8mb4'VIGENTE',_utf8mb4'CANCELADO',_utf8mb4'NO_ENCONTRADO',_utf8mb4'DESCONOCIDO',_utf8mb4'ERROR')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.contribuyente_documento definición

CREATE TABLE `contribuyente_documento` (
  `contribuyente_id` bigint unsigned NOT NULL,
  `documento_id` bigint unsigned NOT NULL,
  `sentido` varchar(10) NOT NULL,
  `primer_registro` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`contribuyente_id`,`documento_id`),
  KEY `idx_cd_doc` (`documento_id`),
  CONSTRAINT `fk_cd_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_cd_doc` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `chk_cd_sentido` CHECK ((`sentido` in (_utf8mb4'EMITIDO',_utf8mb4'RECIBIDO',_utf8mb4'AMBOS')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.contribuyente_regimen definición

CREATE TABLE `contribuyente_regimen` (
  `contribuyente_id` bigint unsigned NOT NULL,
  `clave_regimen` varchar(5) NOT NULL,
  `vigente_desde` date NOT NULL,
  `vigente_hasta` date DEFAULT NULL,
  PRIMARY KEY (`contribuyente_id`,`clave_regimen`,`vigente_desde`),
  CONSTRAINT `fk_contrib_regimen_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `chk_contrib_regimen_fechas` CHECK (((`vigente_hasta` is null) or (`vigente_hasta` >= `vigente_desde`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.contribuyente_tercero_rol definición

CREATE TABLE `contribuyente_tercero_rol` (
  `contribuyente_id` bigint unsigned NOT NULL,
  `tercero_id` bigint unsigned NOT NULL,
  `rol` varchar(30) NOT NULL,
  PRIMARY KEY (`contribuyente_id`,`tercero_id`,`rol`),
  KEY `fk_ctr_tercero` (`tercero_id`),
  CONSTRAINT `fk_ctr_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_ctr_tercero` FOREIGN KEY (`tercero_id`) REFERENCES `tercero` (`id`),
  CONSTRAINT `chk_ctr_rol` CHECK ((`rol` in (_utf8mb4'PROVEEDOR',_utf8mb4'CLIENTE',_utf8mb4'PRESTADOR',_utf8mb4'OTRO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.cuenta_bancaria definición

CREATE TABLE `cuenta_bancaria` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `banco_id` smallint unsigned NOT NULL,
  `alias` varchar(100) NOT NULL,
  `numero_enmascarado` varchar(32) DEFAULT NULL,
  `clabe` char(18) DEFAULT NULL,
  `moneda` varchar(3) NOT NULL,
  `activa` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cuenta_id_contrib` (`id`,`contribuyente_id`),
  UNIQUE KEY `uk_cuenta_clabe` (`clabe`),
  KEY `fk_cuenta_contrib` (`contribuyente_id`),
  KEY `fk_cuenta_banco` (`banco_id`),
  CONSTRAINT `fk_cuenta_banco` FOREIGN KEY (`banco_id`) REFERENCES `banco` (`id`),
  CONSTRAINT `fk_cuenta_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.documento_archivo definición

CREATE TABLE `documento_archivo` (
  `documento_id` bigint unsigned NOT NULL,
  `archivo_id` bigint unsigned NOT NULL,
  `tipo_uso` varchar(15) NOT NULL DEFAULT 'ORIGINAL',
  PRIMARY KEY (`documento_id`,`archivo_id`),
  KEY `fk_da_archivo` (`archivo_id`),
  CONSTRAINT `fk_da_archivo` FOREIGN KEY (`archivo_id`) REFERENCES `archivo_documental` (`id`),
  CONSTRAINT `fk_da_doc` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `chk_da_uso` CHECK ((`tipo_uso` in (_utf8mb4'ORIGINAL',_utf8mb4'RESPALDO',_utf8mb4'ANEXO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.documento_complemento definición

CREATE TABLE `documento_complemento` (
  `documento_id` bigint unsigned NOT NULL,
  `tipo_complemento_id` smallint unsigned NOT NULL,
  PRIMARY KEY (`documento_id`,`tipo_complemento_id`),
  KEY `fk_dcomp_tipo` (`tipo_complemento_id`),
  CONSTRAINT `fk_dcomp_doc` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `fk_dcomp_tipo` FOREIGN KEY (`tipo_complemento_id`) REFERENCES `tipo_complemento` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.documento_participante definición

CREATE TABLE `documento_participante` (
  `documento_id` bigint unsigned NOT NULL,
  `rol` char(1) NOT NULL,
  `tercero_id` bigint unsigned DEFAULT NULL,
  `rfc` varchar(13) CHARACTER SET ascii COLLATE ascii_general_ci DEFAULT NULL,
  `nombre_razon_social` varchar(300) DEFAULT NULL,
  `regimen_fiscal` varchar(5) DEFAULT NULL,
  `domicilio_fiscal_cp` char(5) DEFAULT NULL,
  `residencia_fiscal` char(3) DEFAULT NULL,
  `num_registro_fiscal_extranjero` varchar(100) DEFAULT NULL,
  `uso_cfdi` varchar(5) DEFAULT NULL,
  PRIMARY KEY (`documento_id`,`rol`),
  KEY `idx_doc_part_rfc` (`rfc`,`rol`),
  KEY `fk_docpart_tercero` (`tercero_id`),
  CONSTRAINT `fk_docpart_documento` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `fk_docpart_tercero` FOREIGN KEY (`tercero_id`) REFERENCES `tercero` (`id`),
  CONSTRAINT `chk_docpart_rol` CHECK ((`rol` in (_utf8mb4'E',_utf8mb4'R')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.documento_timbre definición

CREATE TABLE `documento_timbre` (
  `documento_id` bigint unsigned NOT NULL,
  `version_timbre` varchar(8) NOT NULL,
  `fecha_timbrado` datetime NOT NULL,
  `rfc_proveedor_certificacion` varchar(13) DEFAULT NULL,
  `no_certificado_sat` varchar(30) NOT NULL,
  `sello_cfd` text,
  `sello_sat` text,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_timbre_documento` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.documento_verificacion definición

CREATE TABLE `documento_verificacion` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `tipo` varchar(20) NOT NULL,
  `resultado` varchar(25) NOT NULL,
  `detalle` varchar(1000) DEFAULT NULL,
  `verificado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `fuente` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_dver_doc_fecha` (`documento_id`,`verificado_en`),
  CONSTRAINT `fk_dver_doc` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `chk_dver_resultado` CHECK ((`resultado` in (_utf8mb4'VALIDO',_utf8mb4'INVALIDO',_utf8mb4'NO_VERIFICADO',_utf8mb4'ERROR',_utf8mb4'ADVERTENCIA'))),
  CONSTRAINT `chk_dver_tipo` CHECK ((`tipo` in (_utf8mb4'XSD',_utf8mb4'SELLO',_utf8mb4'TIMBRADO',_utf8mb4'SAT',_utf8mb4'NEGOCIO')))
) ENGINE=InnoDB AUTO_INCREMENT=343 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.exportacion_reporte definición

CREATE TABLE `exportacion_reporte` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `usuario_id` bigint unsigned NOT NULL,
  `tipo_reporte` varchar(80) NOT NULL,
  `formato` varchar(15) NOT NULL,
  `archivo_documental_id` bigint unsigned DEFAULT NULL,
  `solicitado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_export_contrib` (`contribuyente_id`),
  KEY `fk_export_usuario` (`usuario_id`),
  KEY `fk_export_archivo` (`archivo_documental_id`),
  CONSTRAINT `fk_export_archivo` FOREIGN KEY (`archivo_documental_id`) REFERENCES `archivo_documental` (`id`),
  CONSTRAINT `fk_export_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_export_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `chk_export_formato` CHECK ((`formato` in (_utf8mb4'PDF',_utf8mb4'XLSX',_utf8mb4'CSV',_utf8mb4'TXT',_utf8mb4'MD')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.importacion_estado_bancario definición

CREATE TABLE `importacion_estado_bancario` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `cuenta_bancaria_id` bigint unsigned NOT NULL,
  `archivo_documental_id` bigint unsigned DEFAULT NULL,
  `formato_origen` varchar(30) NOT NULL,
  `periodo_desde` date DEFAULT NULL,
  `periodo_hasta` date DEFAULT NULL,
  `importado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `usuario_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_estado_bancario_cuenta` (`cuenta_bancaria_id`,`importado_en`),
  KEY `fk_ieb_archivo` (`archivo_documental_id`),
  KEY `fk_ieb_usuario` (`usuario_id`),
  CONSTRAINT `fk_ieb_archivo` FOREIGN KEY (`archivo_documental_id`) REFERENCES `archivo_documental` (`id`),
  CONSTRAINT `fk_ieb_cuenta` FOREIGN KEY (`cuenta_bancaria_id`) REFERENCES `cuenta_bancaria` (`id`),
  CONSTRAINT `fk_ieb_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.lote_importacion definición

CREATE TABLE `lote_importacion` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `usuario_id` bigint unsigned NOT NULL,
  `directorio_origen` varchar(1024) DEFAULT NULL,
  `inicio` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `fin` datetime(6) DEFAULT NULL,
  `estado` varchar(15) NOT NULL DEFAULT 'EN_PROCESO',
  PRIMARY KEY (`id`),
  KEY `fk_lote_contrib` (`contribuyente_id`),
  KEY `fk_lote_usuario` (`usuario_id`),
  CONSTRAINT `fk_lote_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_lote_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `chk_lote_estado` CHECK ((`estado` in (_utf8mb4'EN_PROCESO',_utf8mb4'FINALIZADO',_utf8mb4'PARCIAL',_utf8mb4'ERROR')))
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.movimiento_bancario definición

CREATE TABLE `movimiento_bancario` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `importacion_estado_id` bigint unsigned NOT NULL,
  `ordinal` int unsigned NOT NULL,
  `fecha_operacion` date NOT NULL,
  `fecha_valor` date DEFAULT NULL,
  `naturaleza` varchar(5) NOT NULL,
  `importe` decimal(20,6) NOT NULL,
  `saldo_reportado` decimal(20,6) DEFAULT NULL,
  `descripcion` varchar(1000) DEFAULT NULL,
  `referencia` varchar(200) DEFAULT NULL,
  `identificador_bancario` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bancmov_ordinal` (`importacion_estado_id`,`ordinal`),
  KEY `idx_bancmov_fecha` (`fecha_operacion`,`naturaleza`),
  CONSTRAINT `fk_bancmov_import` FOREIGN KEY (`importacion_estado_id`) REFERENCES `importacion_estado_bancario` (`id`),
  CONSTRAINT `chk_bancmov_importe` CHECK ((`importe` > 0)),
  CONSTRAINT `chk_bancmov_nat` CHECK ((`naturaleza` in (_utf8mb4'CARGO',_utf8mb4'ABONO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.movimiento_pago definición

CREATE TABLE `movimiento_pago` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `tercero_id` bigint unsigned DEFAULT NULL,
  `naturaleza` varchar(10) NOT NULL,
  `fecha_movimiento` datetime NOT NULL,
  `moneda` varchar(3) NOT NULL,
  `importe` decimal(20,6) NOT NULL,
  `origen` varchar(15) NOT NULL,
  `referencia` varchar(160) DEFAULT NULL,
  `observaciones` varchar(1000) DEFAULT NULL,
  `usuario_id` bigint unsigned NOT NULL,
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_movpago_id_contrib` (`id`,`contribuyente_id`),
  KEY `idx_movpago_contrib_fecha` (`contribuyente_id`,`fecha_movimiento`),
  KEY `fk_movpago_tercero` (`tercero_id`),
  KEY `fk_movpago_usuario` (`usuario_id`),
  CONSTRAINT `fk_movpago_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_movpago_tercero` FOREIGN KEY (`tercero_id`) REFERENCES `tercero` (`id`),
  CONSTRAINT `fk_movpago_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `chk_movpago_importe` CHECK ((`importe` > 0)),
  CONSTRAINT `chk_movpago_nat` CHECK ((`naturaleza` in (_utf8mb4'PAGO',_utf8mb4'COBRO'))),
  CONSTRAINT `chk_movpago_origen` CHECK ((`origen` in (_utf8mb4'MANUAL',_utf8mb4'COMPLEMENTO',_utf8mb4'BANCO',_utf8mb4'OTRO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina definición

CREATE TABLE `nomina` (
  `documento_id` bigint unsigned NOT NULL,
  `version_complemento` varchar(10) NOT NULL,
  `tipo_nomina` char(1) NOT NULL,
  `fecha_pago` date NOT NULL,
  `fecha_inicial_pago` date NOT NULL,
  `fecha_final_pago` date NOT NULL,
  `num_dias_pagados` decimal(12,3) NOT NULL,
  `total_percepciones` decimal(20,6) DEFAULT NULL,
  `total_deducciones` decimal(20,6) DEFAULT NULL,
  `total_otros_pagos` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_nomina_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`),
  CONSTRAINT `chk_nomina_fecha` CHECK ((`fecha_final_pago` >= `fecha_inicial_pago`)),
  CONSTRAINT `chk_nomina_tipo` CHECK ((`tipo_nomina` in (_utf8mb4'O',_utf8mb4'E')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina_deduccion definición

CREATE TABLE `nomina_deduccion` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `tipo_deduccion` varchar(5) NOT NULL,
  `clave` varchar(20) NOT NULL,
  `concepto` varchar(250) NOT NULL,
  `importe` decimal(20,6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nomded_ordinal` (`documento_id`,`ordinal`),
  CONSTRAINT `fk_nomded_nomina` FOREIGN KEY (`documento_id`) REFERENCES `nomina` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina_emisor definición

CREATE TABLE `nomina_emisor` (
  `documento_id` bigint unsigned NOT NULL,
  `curp` varchar(18) DEFAULT NULL,
  `registro_patronal` varchar(30) DEFAULT NULL,
  `rfc_patron_origen` varchar(13) DEFAULT NULL,
  `origen_recurso` varchar(5) DEFAULT NULL,
  `monto_recurso_propio` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_nomemi_nomina` FOREIGN KEY (`documento_id`) REFERENCES `nomina` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina_incapacidad definición

CREATE TABLE `nomina_incapacidad` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `dias_incapacidad` int unsigned NOT NULL,
  `tipo_incapacidad` varchar(5) NOT NULL,
  `importe_monetario` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nominc_ordinal` (`documento_id`,`ordinal`),
  CONSTRAINT `fk_nominc_nomina` FOREIGN KEY (`documento_id`) REFERENCES `nomina` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina_otro_pago definición

CREATE TABLE `nomina_otro_pago` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `tipo_otro_pago` varchar(5) NOT NULL,
  `clave` varchar(20) NOT NULL,
  `concepto` varchar(250) NOT NULL,
  `importe` decimal(20,6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nomotro_ordinal` (`documento_id`,`ordinal`),
  CONSTRAINT `fk_nomotro_nomina` FOREIGN KEY (`documento_id`) REFERENCES `nomina` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina_otro_pago_subsidio definición

CREATE TABLE `nomina_otro_pago_subsidio` (
  `otro_pago_id` bigint unsigned NOT NULL,
  `subsidio_causado` decimal(20,6) NOT NULL,
  PRIMARY KEY (`otro_pago_id`),
  CONSTRAINT `fk_nomsub_otro` FOREIGN KEY (`otro_pago_id`) REFERENCES `nomina_otro_pago` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina_percepcion definición

CREATE TABLE `nomina_percepcion` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `tipo_percepcion` varchar(5) NOT NULL,
  `clave` varchar(20) NOT NULL,
  `concepto` varchar(250) NOT NULL,
  `importe_gravado` decimal(20,6) NOT NULL,
  `importe_exento` decimal(20,6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nomperc_ordinal` (`documento_id`,`ordinal`),
  CONSTRAINT `fk_nomperc_nomina` FOREIGN KEY (`documento_id`) REFERENCES `nomina` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.nomina_receptor definición

CREATE TABLE `nomina_receptor` (
  `documento_id` bigint unsigned NOT NULL,
  `curp` varchar(18) NOT NULL,
  `num_seguridad_social` varchar(20) DEFAULT NULL,
  `fecha_inicio_relacion_laboral` date DEFAULT NULL,
  `antiguedad` varchar(20) DEFAULT NULL,
  `tipo_contrato` varchar(5) DEFAULT NULL,
  `sindicalizado` char(2) DEFAULT NULL,
  `tipo_jornada` varchar(5) DEFAULT NULL,
  `tipo_regimen` varchar(5) NOT NULL,
  `num_empleado` varchar(25) NOT NULL,
  `departamento` varchar(120) DEFAULT NULL,
  `puesto` varchar(120) DEFAULT NULL,
  `riesgo_puesto` varchar(5) DEFAULT NULL,
  `periodicidad_pago` varchar(5) DEFAULT NULL,
  `banco` varchar(5) DEFAULT NULL,
  `cuenta_bancaria` varchar(60) DEFAULT NULL,
  `salario_base_cot_apor` decimal(20,6) DEFAULT NULL,
  `salario_diario_integrado` decimal(20,6) DEFAULT NULL,
  `clave_entidad_federativa` varchar(5) NOT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_nomrec_nomina` FOREIGN KEY (`documento_id`) REFERENCES `nomina` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.obligacion definición

CREATE TABLE `obligacion` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `documento_id` bigint unsigned DEFAULT NULL,
  `tercero_id` bigint unsigned DEFAULT NULL,
  `naturaleza` varchar(10) NOT NULL,
  `concepto` varchar(500) DEFAULT NULL,
  `moneda` varchar(3) NOT NULL,
  `monto_original` decimal(20,6) NOT NULL,
  `fecha_origen` date NOT NULL,
  `fecha_vencimiento` date DEFAULT NULL,
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_obligacion_documento` (`contribuyente_id`,`documento_id`,`naturaleza`),
  KEY `idx_obligacion_vencimiento` (`contribuyente_id`,`fecha_vencimiento`),
  KEY `fk_obliga_tercero` (`tercero_id`),
  CONSTRAINT `fk_obliga_contrib_doc` FOREIGN KEY (`contribuyente_id`, `documento_id`) REFERENCES `contribuyente_documento` (`contribuyente_id`, `documento_id`),
  CONSTRAINT `fk_obliga_tercero` FOREIGN KEY (`tercero_id`) REFERENCES `tercero` (`id`),
  CONSTRAINT `chk_obliga_monto` CHECK ((`monto_original` >= 0)),
  CONSTRAINT `chk_obliga_naturaleza` CHECK ((`naturaleza` in (_utf8mb4'PAGAR',_utf8mb4'COBRAR')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.pago_fiscal definición

CREATE TABLE `pago_fiscal` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `fecha_pago` datetime NOT NULL,
  `forma_pago` varchar(5) NOT NULL,
  `moneda` varchar(3) NOT NULL,
  `tipo_cambio` decimal(20,6) DEFAULT NULL,
  `monto` decimal(20,6) NOT NULL,
  `numero_operacion` varchar(100) DEFAULT NULL,
  `rfc_emisor_cta_ordenante` varchar(13) DEFAULT NULL,
  `nombre_banco_ordenante_ext` varchar(300) DEFAULT NULL,
  `cuenta_ordenante` varchar(60) DEFAULT NULL,
  `rfc_emisor_cta_beneficiaria` varchar(13) DEFAULT NULL,
  `cuenta_beneficiaria` varchar(60) DEFAULT NULL,
  `tipo_cadena_pago` varchar(5) DEFAULT NULL,
  `certificado_pago` text,
  `cadena_pago` text,
  `sello_pago` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pago_fiscal_ordinal` (`documento_id`,`ordinal`),
  KEY `idx_pago_fiscal_fecha` (`fecha_pago`),
  CONSTRAINT `fk_pago_fiscal_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`),
  CONSTRAINT `chk_pago_fiscal_monto` CHECK (((`ordinal` > 0) and (`monto` > 0)))
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.pago_fiscal_documento definición

CREATE TABLE `pago_fiscal_documento` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `pago_fiscal_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `uuid_documento` char(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `serie` varchar(40) DEFAULT NULL,
  `folio` varchar(40) DEFAULT NULL,
  `moneda` varchar(3) NOT NULL,
  `equivalencia_dr` decimal(20,10) DEFAULT NULL,
  `num_parcialidad` int unsigned DEFAULT NULL,
  `saldo_anterior` decimal(20,6) DEFAULT NULL,
  `importe_pagado` decimal(20,6) NOT NULL,
  `saldo_insoluto` decimal(20,6) DEFAULT NULL,
  `objeto_impuesto` varchar(5) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pagodoc_ordinal` (`pago_fiscal_id`,`ordinal`),
  KEY `idx_pagodoc_uuid` (`uuid_documento`),
  CONSTRAINT `fk_pagodoc_pago` FOREIGN KEY (`pago_fiscal_id`) REFERENCES `pago_fiscal` (`id`),
  CONSTRAINT `chk_pagodoc_montos` CHECK (((`ordinal` > 0) and (`importe_pagado` > 0) and ((`saldo_anterior` is null) or (`saldo_anterior` >= 0)) and ((`saldo_insoluto` is null) or (`saldo_insoluto` >= 0))))
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.pago_fiscal_impuesto_documento definición

CREATE TABLE `pago_fiscal_impuesto_documento` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `pago_documento_id` bigint unsigned NOT NULL,
  `movimiento` varchar(10) NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `impuesto` varchar(5) NOT NULL,
  `tipo_factor` varchar(12) DEFAULT NULL,
  `base` decimal(20,6) DEFAULT NULL,
  `tasa_cuota` decimal(18,6) DEFAULT NULL,
  `importe` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pfid_ordinal` (`pago_documento_id`,`movimiento`,`ordinal`),
  CONSTRAINT `fk_pfid_pagodoc` FOREIGN KEY (`pago_documento_id`) REFERENCES `pago_fiscal_documento` (`id`),
  CONSTRAINT `chk_pfid_mov` CHECK ((`movimiento` in (_utf8mb4'TRASLADO',_utf8mb4'RETENCION')))
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.pago_fiscal_impuesto_pago definición

CREATE TABLE `pago_fiscal_impuesto_pago` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `pago_fiscal_id` bigint unsigned NOT NULL,
  `movimiento` varchar(10) NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `impuesto` varchar(5) NOT NULL,
  `tipo_factor` varchar(12) DEFAULT NULL,
  `base` decimal(20,6) DEFAULT NULL,
  `tasa_cuota` decimal(18,6) DEFAULT NULL,
  `importe` decimal(20,6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pfip_ordinal` (`pago_fiscal_id`,`movimiento`,`ordinal`),
  CONSTRAINT `fk_pfip_pago` FOREIGN KEY (`pago_fiscal_id`) REFERENCES `pago_fiscal` (`id`),
  CONSTRAINT `chk_pfip_mov` CHECK ((`movimiento` in (_utf8mb4'TRASLADO',_utf8mb4'RETENCION')))
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.pago_fiscal_totales definición

CREATE TABLE `pago_fiscal_totales` (
  `documento_id` bigint unsigned NOT NULL,
  `monto_total_pagos` decimal(20,6) NOT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_pft_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.periodo_fiscal definición

CREATE TABLE `periodo_fiscal` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `ejercicio` smallint unsigned NOT NULL,
  `mes` tinyint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_periodo_contrib_ej_mes` (`contribuyente_id`,`ejercicio`,`mes`),
  UNIQUE KEY `uk_periodo_id_contrib` (`id`,`contribuyente_id`),
  CONSTRAINT `fk_periodo_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `chk_periodo_mes` CHECK ((`mes` between 1 and 12))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.repositorio_local definición

CREATE TABLE `repositorio_local` (
  `repositorio_id` bigint unsigned NOT NULL,
  `ruta_base` varchar(1024) NOT NULL,
  PRIMARY KEY (`repositorio_id`),
  CONSTRAINT `fk_repo_local` FOREIGN KEY (`repositorio_id`) REFERENCES `repositorio_documental` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.retencion definición

CREATE TABLE `retencion` (
  `documento_id` bigint unsigned NOT NULL,
  `folio_interno` varchar(60) DEFAULT NULL,
  `clave_retencion` varchar(10) NOT NULL,
  `lugar_expedicion` char(5) DEFAULT NULL,
  `certificado_numero` varchar(30) DEFAULT NULL,
  `sello_emisor` text,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_ret_documento` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.retencion_impuesto definición

CREATE TABLE `retencion_impuesto` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `documento_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `base_retencion` decimal(20,6) DEFAULT NULL,
  `impuesto` varchar(10) NOT NULL,
  `importe_retencion` decimal(20,6) NOT NULL,
  `tipo_pago_retencion` varchar(5) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_retimp_ordinal` (`documento_id`,`ordinal`),
  CONSTRAINT `fk_retimp_ret` FOREIGN KEY (`documento_id`) REFERENCES `retencion` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.retencion_periodo definición

CREATE TABLE `retencion_periodo` (
  `documento_id` bigint unsigned NOT NULL,
  `mes_inicial` tinyint unsigned NOT NULL,
  `mes_final` tinyint unsigned NOT NULL,
  `ejercicio` smallint unsigned NOT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_retper_ret` FOREIGN KEY (`documento_id`) REFERENCES `retencion` (`documento_id`),
  CONSTRAINT `chk_retper_mes` CHECK (((`mes_inicial` between 1 and 12) and (`mes_final` between 1 and 12) and (`mes_final` >= `mes_inicial`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.retencion_receptor_extranjero definición

CREATE TABLE `retencion_receptor_extranjero` (
  `documento_id` bigint unsigned NOT NULL,
  `nacionalidad` varchar(15) NOT NULL,
  `num_registro_fiscal` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_retrec_ret` FOREIGN KEY (`documento_id`) REFERENCES `retencion` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.retencion_relacion_uuid definición

CREATE TABLE `retencion_relacion_uuid` (
  `documento_id` bigint unsigned NOT NULL,
  `uuid_relacionado` char(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  PRIMARY KEY (`documento_id`,`uuid_relacionado`),
  CONSTRAINT `fk_retrel_ret` FOREIGN KEY (`documento_id`) REFERENCES `retencion` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.retencion_totales definición

CREATE TABLE `retencion_totales` (
  `documento_id` bigint unsigned NOT NULL,
  `monto_total_operacion` decimal(20,6) NOT NULL,
  `monto_total_gravado` decimal(20,6) NOT NULL,
  `monto_total_exento` decimal(20,6) NOT NULL,
  `monto_total_retenido` decimal(20,6) NOT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_rettot_ret` FOREIGN KEY (`documento_id`) REFERENCES `retencion` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.rol_permiso definición

CREATE TABLE `rol_permiso` (
  `rol_id` smallint unsigned NOT NULL,
  `permiso_id` smallint unsigned NOT NULL,
  PRIMARY KEY (`rol_id`,`permiso_id`),
  KEY `fk_rolperm_permiso` (`permiso_id`),
  CONSTRAINT `fk_rolperm_permiso` FOREIGN KEY (`permiso_id`) REFERENCES `permiso` (`id`),
  CONSTRAINT `fk_rolperm_rol` FOREIGN KEY (`rol_id`) REFERENCES `rol` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.sat_catalogo_clave definición

CREATE TABLE `sat_catalogo_clave` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `catalogo_id` bigint unsigned NOT NULL,
  `clave` varchar(45) NOT NULL,
  `descripcion` varchar(450) NOT NULL,
  `vigencia_inicio` date NOT NULL,
  `vigencia_fin` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_catalogo_clave_vigencia` (`catalogo_id`,`clave`,`vigencia_inicio`),
  CONSTRAINT `fk_scclave_catalogo` FOREIGN KEY (`catalogo_id`) REFERENCES `sat_catalogo` (`id`),
  CONSTRAINT `chk_scclave_vigencia` CHECK (((`vigencia_fin` is null) or (`vigencia_fin` >= `vigencia_inicio`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.usuario_contribuyente definición

CREATE TABLE `usuario_contribuyente` (
  `usuario_id` bigint unsigned NOT NULL,
  `contribuyente_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`usuario_id`,`contribuyente_id`),
  KEY `fk_uc_contrib` (`contribuyente_id`),
  CONSTRAINT `fk_uc_contrib` FOREIGN KEY (`contribuyente_id`) REFERENCES `contribuyente` (`id`),
  CONSTRAINT `fk_uc_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.usuario_rol definición

CREATE TABLE `usuario_rol` (
  `usuario_id` bigint unsigned NOT NULL,
  `rol_id` smallint unsigned NOT NULL,
  PRIMARY KEY (`usuario_id`,`rol_id`),
  KEY `fk_usrol_rol` (`rol_id`),
  CONSTRAINT `fk_usrol_rol` FOREIGN KEY (`rol_id`) REFERENCES `rol` (`id`),
  CONSTRAINT `fk_usrol_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.ajuste_obligacion definición

CREATE TABLE `ajuste_obligacion` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `obligacion_id` bigint unsigned NOT NULL,
  `documento_soporte_id` bigint unsigned DEFAULT NULL,
  `tipo` varchar(25) NOT NULL,
  `fecha_ajuste` date NOT NULL,
  `importe_moneda_obligacion` decimal(20,6) NOT NULL,
  `observacion` varchar(600) DEFAULT NULL,
  `usuario_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ajuste_obligacion` (`obligacion_id`),
  KEY `fk_ajuste_doc` (`documento_soporte_id`),
  KEY `fk_ajuste_usuario` (`usuario_id`),
  CONSTRAINT `fk_ajuste_doc` FOREIGN KEY (`documento_soporte_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `fk_ajuste_obligacion` FOREIGN KEY (`obligacion_id`) REFERENCES `obligacion` (`id`),
  CONSTRAINT `fk_ajuste_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `chk_ajuste_importe` CHECK ((`importe_moneda_obligacion` > 0)),
  CONSTRAINT `chk_ajuste_tipo` CHECK ((`tipo` in (_utf8mb4'NOTA_CREDITO',_utf8mb4'DESCUENTO',_utf8mb4'DEVOLUCION',_utf8mb4'ANTICIPO',_utf8mb4'OTRO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.aplicacion_pago definición

CREATE TABLE `aplicacion_pago` (
  `movimiento_pago_id` bigint unsigned NOT NULL,
  `obligacion_id` bigint unsigned NOT NULL,
  `importe_moneda_pago` decimal(20,6) NOT NULL,
  `importe_moneda_obligacion` decimal(20,6) NOT NULL,
  PRIMARY KEY (`movimiento_pago_id`,`obligacion_id`),
  KEY `fk_apago_obligacion` (`obligacion_id`),
  CONSTRAINT `fk_apago_mov` FOREIGN KEY (`movimiento_pago_id`) REFERENCES `movimiento_pago` (`id`),
  CONSTRAINT `fk_apago_obligacion` FOREIGN KEY (`obligacion_id`) REFERENCES `obligacion` (`id`),
  CONSTRAINT `chk_apago_importes` CHECK (((`importe_moneda_pago` > 0) and (`importe_moneda_obligacion` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.bitacora_cambio definición

CREATE TABLE `bitacora_cambio` (
  `evento_id` bigint unsigned NOT NULL,
  `atributo` varchar(85) NOT NULL,
  `valor_anterior` text,
  `valor_nuevo` text,
  PRIMARY KEY (`evento_id`,`atributo`),
  CONSTRAINT `fk_cambio_evento` FOREIGN KEY (`evento_id`) REFERENCES `bitacora_evento` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.conciliacion_bancaria definición

CREATE TABLE `conciliacion_bancaria` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `movimiento_bancario_id` bigint unsigned NOT NULL,
  `movimiento_pago_id` bigint unsigned NOT NULL,
  `importe_conciliado` decimal(20,6) NOT NULL,
  `estado` varchar(15) NOT NULL DEFAULT 'PROPUESTO',
  `puntuacion` decimal(5,4) DEFAULT NULL,
  `explicacion` varchar(500) DEFAULT NULL,
  `revisado_por` bigint unsigned DEFAULT NULL,
  `revisado_en` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conciliacion_par` (`movimiento_bancario_id`,`movimiento_pago_id`),
  KEY `fk_conc_pago` (`movimiento_pago_id`),
  KEY `fk_conc_revisor` (`revisado_por`),
  CONSTRAINT `fk_conc_banc` FOREIGN KEY (`movimiento_bancario_id`) REFERENCES `movimiento_bancario` (`id`),
  CONSTRAINT `fk_conc_pago` FOREIGN KEY (`movimiento_pago_id`) REFERENCES `movimiento_pago` (`id`),
  CONSTRAINT `fk_conc_revisor` FOREIGN KEY (`revisado_por`) REFERENCES `usuario` (`id`),
  CONSTRAINT `chk_conc_estado` CHECK ((`estado` in (_utf8mb4'PROPUESTO',_utf8mb4'CONFIRMADO',_utf8mb4'RECHAZADO'))),
  CONSTRAINT `chk_conc_importe` CHECK ((`importe_conciliado` > 0)),
  CONSTRAINT `chk_conc_score` CHECK (((`puntuacion` is null) or (`puntuacion` between 0 and 1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.efecto_fiscal_periodo definición

CREATE TABLE `efecto_fiscal_periodo` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `contribuyente_id` bigint unsigned NOT NULL,
  `documento_id` bigint unsigned NOT NULL,
  `periodo_id` bigint unsigned NOT NULL,
  `concepto_efecto` varchar(45) NOT NULL,
  `importe` decimal(20,6) NOT NULL,
  `descripcion` varchar(600) DEFAULT NULL,
  `usuario_id` bigint unsigned NOT NULL,
  `creado_en` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_efecto_doccontrib` (`contribuyente_id`,`documento_id`),
  KEY `fk_efecto_periodo` (`periodo_id`,`contribuyente_id`),
  KEY `fk_efecto_usuario` (`usuario_id`),
  CONSTRAINT `fk_efecto_doccontrib` FOREIGN KEY (`contribuyente_id`, `documento_id`) REFERENCES `contribuyente_documento` (`contribuyente_id`, `documento_id`),
  CONSTRAINT `fk_efecto_periodo` FOREIGN KEY (`periodo_id`, `contribuyente_id`) REFERENCES `periodo_fiscal` (`id`, `contribuyente_id`),
  CONSTRAINT `fk_efecto_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.exportacion_filtro definición

CREATE TABLE `exportacion_filtro` (
  `exportacion_id` bigint unsigned NOT NULL,
  `ordinal` smallint unsigned NOT NULL,
  `campo` varchar(80) NOT NULL,
  `operador` varchar(18) NOT NULL,
  `valor` varchar(500) NOT NULL,
  PRIMARY KEY (`exportacion_id`,`ordinal`),
  CONSTRAINT `fk_expfiltro_export` FOREIGN KEY (`exportacion_id`) REFERENCES `exportacion_reporte` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.importacion_archivo definición

CREATE TABLE `importacion_archivo` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `lote_id` bigint unsigned NOT NULL,
  `ordinal` int unsigned NOT NULL,
  `nombre_archivo` varchar(260) NOT NULL,
  `ruta_origen` varchar(1024) DEFAULT NULL,
  `sha256` binary(32) DEFAULT NULL,
  `documento_id` bigint unsigned DEFAULT NULL,
  `archivo_documental_id` bigint unsigned DEFAULT NULL,
  `resultado` varchar(20) NOT NULL,
  `detalle` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_imp_archivo_ordinal` (`lote_id`,`ordinal`),
  KEY `idx_imp_archivo_doc` (`documento_id`),
  KEY `fk_imparch_repo` (`archivo_documental_id`),
  CONSTRAINT `fk_imparch_doc` FOREIGN KEY (`documento_id`) REFERENCES `documento_fiscal` (`id`),
  CONSTRAINT `fk_imparch_lote` FOREIGN KEY (`lote_id`) REFERENCES `lote_importacion` (`id`),
  CONSTRAINT `fk_imparch_repo` FOREIGN KEY (`archivo_documental_id`) REFERENCES `archivo_documental` (`id`),
  CONSTRAINT `chk_imparch_resultado` CHECK ((`resultado` in (_utf8mb4'ACEPTADO',_utf8mb4'DUPLICADO',_utf8mb4'RECHAZADO',_utf8mb4'ERROR')))
) ENGINE=InnoDB AUTO_INCREMENT=157 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.movimiento_pago_complemento definición

CREATE TABLE `movimiento_pago_complemento` (
  `movimiento_pago_id` bigint unsigned NOT NULL,
  `pago_fiscal_documento_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`movimiento_pago_id`,`pago_fiscal_documento_id`),
  KEY `fk_mpc_pagofiscal` (`pago_fiscal_documento_id`),
  CONSTRAINT `fk_mpc_mov` FOREIGN KEY (`movimiento_pago_id`) REFERENCES `movimiento_pago` (`id`),
  CONSTRAINT `fk_mpc_pagofiscal` FOREIGN KEY (`pago_fiscal_documento_id`) REFERENCES `pago_fiscal_documento` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- visor_cfdi.pago_fiscal_total_impuesto definición

CREATE TABLE `pago_fiscal_total_impuesto` (
  `documento_id` bigint unsigned NOT NULL,
  `atributo` varchar(70) NOT NULL,
  `importe_mxn` decimal(20,6) NOT NULL,
  PRIMARY KEY (`documento_id`,`atributo`),
  CONSTRAINT `fk_pfti_tot` FOREIGN KEY (`documento_id`) REFERENCES `pago_fiscal_totales` (`documento_id`),
  CONSTRAINT `chk_pfti_importe` CHECK ((`importe_mxn` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;