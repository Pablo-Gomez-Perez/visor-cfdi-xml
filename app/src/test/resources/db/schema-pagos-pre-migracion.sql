-- Extracto del esquema base 026f87b, previo a migración RF-007.
-- Solo tablas necesarias para probar DDL y backfill sin datos reales.

CREATE TABLE `cat_tipo_cfdi` (
  `clave` char(1) NOT NULL,
  `descripcion` varchar(80) NOT NULL,
  PRIMARY KEY (`clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `pago_fiscal_totales` (
  `documento_id` bigint unsigned NOT NULL,
  `monto_total_pagos` decimal(20,6) NOT NULL,
  PRIMARY KEY (`documento_id`),
  CONSTRAINT `fk_pft_cfdi` FOREIGN KEY (`documento_id`) REFERENCES `cfdi` (`documento_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `pago_fiscal_total_impuesto` (
  `documento_id` bigint unsigned NOT NULL,
  `atributo` varchar(70) NOT NULL,
  `importe_mxn` decimal(20,6) NOT NULL,
  PRIMARY KEY (`documento_id`,`atributo`),
  CONSTRAINT `fk_pfti_tot` FOREIGN KEY (`documento_id`) REFERENCES `pago_fiscal_totales` (`documento_id`),
  CONSTRAINT `chk_pfti_importe` CHECK ((`importe_mxn` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO cat_tipo_cfdi(clave,descripcion) VALUES ('P','Pago');
INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,fecha_emision)
VALUES(1,'CFDI','123e4567-e89b-12d3-a456-426614174111','4.0','2023-04-15 10:00:00');
INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total) VALUES(1,'P','XXX',0,0);
INSERT INTO pago_fiscal(id,documento_id,ordinal,fecha_pago,forma_pago,moneda,monto)
VALUES(1,1,1,'2023-04-15 09:00:00','01','MXN',50);
INSERT INTO pago_fiscal_documento(pago_fiscal_id,ordinal,uuid_documento,moneda,importe_pagado)
VALUES(1,1,'123e4567-e89b-12d3-a456-426614174112','MXN',50);
INSERT INTO pago_fiscal_totales(documento_id,monto_total_pagos) VALUES(1,50);
