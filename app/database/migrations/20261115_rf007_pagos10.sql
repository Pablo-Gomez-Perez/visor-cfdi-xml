-- RF-007 Pagos 1.0: ejecutar UNA sola vez sobre el esquema actual de main.
-- Seleccionar primero visor_cfdi. Respaldar BD/rutinas; detener la aplicación.
-- MySQL DDL hace commit implícito; no usar --force; no ejecutar en paralelo.
-- No elimina documentos ni sobrescribe XML existentes.

CREATE TABLE pago_fiscal_complemento (
  documento_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  version_complemento VARCHAR(8) NOT NULL,
  CONSTRAINT fk_pfcomp_cfdi FOREIGN KEY (documento_id) REFERENCES cfdi(documento_id),
  CONSTRAINT chk_pfcomp_version CHECK (version_complemento IN ('1.0','2.0'))
) ENGINE=InnoDB;

-- El nodo Totales persistido es evidencia de Pagos 2.0; no inventar versiones
-- de registros anteriores que carezcan de dicho nodo.
INSERT INTO pago_fiscal_complemento(documento_id,version_complemento)
SELECT documento_id,'2.0' FROM pago_fiscal_totales;

ALTER TABLE pago_fiscal_documento
  DROP CHECK chk_pagodoc_montos,
  MODIFY uuid_documento CHAR(36) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
  MODIFY importe_pagado DECIMAL(20,6) NULL,
  ADD COLUMN id_documento_historico VARCHAR(36) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
  ADD COLUMN tipo_cambio_dr DECIMAL(20,6) NULL,
  ADD COLUMN metodo_pago_dr VARCHAR(5) NULL,
  ADD CONSTRAINT chk_pagodoc_identidad CHECK (
    (uuid_documento IS NOT NULL AND id_documento_historico IS NULL)
    OR (uuid_documento IS NULL AND id_documento_historico IS NOT NULL)),
  ADD CONSTRAINT chk_pagodoc_montos CHECK (
    ordinal > 0 AND (importe_pagado IS NULL OR importe_pagado > 0)
    AND (saldo_anterior IS NULL OR saldo_anterior >= 0)
    AND (saldo_insoluto IS NULL OR saldo_insoluto >= 0));

CREATE TABLE pago_fiscal_grupo_impuesto10 (
  pago_fiscal_id BIGINT UNSIGNED NOT NULL,
  ordinal SMALLINT UNSIGNED NOT NULL,
  total_retenidos DECIMAL(20,6) NULL,
  total_trasladados DECIMAL(20,6) NULL,
  PRIMARY KEY (pago_fiscal_id, ordinal),
  CONSTRAINT fk_pfg10_pago FOREIGN KEY (pago_fiscal_id) REFERENCES pago_fiscal(id),
  CONSTRAINT chk_pfg10_ordinal CHECK (ordinal > 0)
) ENGINE=InnoDB;

ALTER TABLE pago_fiscal_impuesto_pago
  ADD COLUMN grupo_impuesto_10 SMALLINT UNSIGNED NULL,
  ADD CONSTRAINT fk_pfip_grupo10 FOREIGN KEY (pago_fiscal_id, grupo_impuesto_10)
    REFERENCES pago_fiscal_grupo_impuesto10(pago_fiscal_id, ordinal);

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_rf007_importar_pago$$
CREATE PROCEDURE sp_rf007_importar_pago(
 IN p_documento_id BIGINT UNSIGNED,
 IN p_cfdi JSON
)
BEGIN
  DECLARE v_version VARCHAR(8);
  SET v_version=JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.pagos.version'));
  IF v_version IS NULL OR v_version NOT IN ('1.0','2.0')
     OR COALESCE(JSON_LENGTH(p_cfdi,'$.pagos.pagos'),0) < 1
     OR (v_version='2.0' AND JSON_EXTRACT(p_cfdi,'$.pagos.totales.MontoTotalPagos') IS NULL)
     OR (v_version='1.0' AND (
       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.version')),'') <> '3.3'
       OR JSON_CONTAINS_PATH(p_cfdi,'one','$.pagos.totales')))
  THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Complemento Pagos incompleto o version incompatible';
  END IF;
  IF v_version='2.0' AND EXISTS (
    SELECT 1 FROM JSON_TABLE(p_cfdi,'$.pagos.pagos[*]' COLUMNS (
      NESTED PATH '$.documentos[*]' COLUMNS (
        ordinal_doc FOR ORDINALITY, pagado DECIMAL(20,6) PATH '$.pagado'
      )
    )) d WHERE d.ordinal_doc IS NOT NULL AND d.pagado IS NULL
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pagos 2.0 requiere ImpPagado';
  END IF;
  INSERT INTO pago_fiscal_complemento(documento_id,version_complemento)
  VALUES(p_documento_id,v_version);

  INSERT INTO pago_fiscal
    (documento_id,ordinal,fecha_pago,forma_pago,moneda,tipo_cambio,monto,
     numero_operacion,rfc_emisor_cta_ordenante,nombre_banco_ordenante_ext,
     cuenta_ordenante,rfc_emisor_cta_beneficiaria,cuenta_beneficiaria,
     tipo_cadena_pago,certificado_pago,cadena_pago,sello_pago)
  SELECT p_documento_id,p.ordinal,STR_TO_DATE(p.fecha,'%Y-%m-%d %H:%i:%s'),
         p.forma,p.moneda,p.tipo_cambio,p.monto,
         p.numero_operacion,p.rfc_ord,p.banco_ord,p.cuenta_ord,
         p.rfc_ben,p.cuenta_ben,p.tipo_cadena,p.certificado,p.cadena,p.sello
    FROM JSON_TABLE(p_cfdi,'$.pagos.pagos[*]' COLUMNS (
      ordinal FOR ORDINALITY,
      fecha VARCHAR(30) PATH '$.fecha',
      forma VARCHAR(5) PATH '$.forma',
      moneda VARCHAR(3) PATH '$.moneda',
      tipo_cambio DECIMAL(20,6) PATH '$.tipoCambio',
      monto DECIMAL(20,6) PATH '$.monto',
      numero_operacion VARCHAR(100) PATH '$.numeroOperacion',
      rfc_ord VARCHAR(13) PATH '$.rfcCuentaOrdenante',
      banco_ord VARCHAR(300) PATH '$.bancoOrdenante',
      cuenta_ord VARCHAR(60) PATH '$.cuentaOrdenante',
      rfc_ben VARCHAR(13) PATH '$.rfcCuentaBeneficiaria',
      cuenta_ben VARCHAR(60) PATH '$.cuentaBeneficiaria',
      tipo_cadena VARCHAR(5) PATH '$.tipoCadena',
      certificado TEXT PATH '$.certificado',
      cadena TEXT PATH '$.cadena',
      sello TEXT PATH '$.sello'
    )) AS p;

  INSERT INTO pago_fiscal_documento
    (pago_fiscal_id,ordinal,uuid_documento,serie,folio,moneda,
     equivalencia_dr,num_parcialidad,saldo_anterior,importe_pagado,
     saldo_insoluto,objeto_impuesto,id_documento_historico,tipo_cambio_dr,metodo_pago_dr)
  SELECT pf.id, d.ordinal_doc, d.uuid, d.serie, d.folio, d.moneda,
         d.equivalencia,d.parcialidad,d.saldo_anterior,d.pagado,
         d.saldo_insoluto,d.objeto,d.id_historico,d.tipo_cambio_dr,d.metodo_pago_dr
    FROM JSON_TABLE(p_cfdi,'$.pagos.pagos[*]' COLUMNS (
      ordinal_pago FOR ORDINALITY,
      NESTED PATH '$.documentos[*]' COLUMNS (
        ordinal_doc FOR ORDINALITY,
        uuid CHAR(36) PATH '$.uuid',
        serie VARCHAR(40) PATH '$.serie',
        folio VARCHAR(40) PATH '$.folio',
        moneda VARCHAR(3) PATH '$.moneda',
        equivalencia DECIMAL(20,10) PATH '$.equivalencia',
        parcialidad INT PATH '$.parcialidad',
        saldo_anterior DECIMAL(20,6) PATH '$.saldoAnterior',
        pagado DECIMAL(20,6) PATH '$.pagado',
        saldo_insoluto DECIMAL(20,6) PATH '$.saldoInsoluto',
        objeto VARCHAR(5) PATH '$.objeto',
        id_historico VARCHAR(36) PATH '$.idHistorico',
        tipo_cambio_dr DECIMAL(20,6) PATH '$.tipoCambioDR',
        metodo_pago_dr VARCHAR(5) PATH '$.metodoPagoDR'
      )
    )) AS d
    JOIN pago_fiscal pf ON pf.documento_id=p_documento_id
      AND pf.ordinal=d.ordinal_pago
   WHERE d.ordinal_doc IS NOT NULL;

  INSERT INTO pago_fiscal_impuesto_documento
    (pago_documento_id,movimiento,ordinal,impuesto,tipo_factor,
     base,tasa_cuota,importe)
  SELECT pd.id, i.movimiento, i.ordinal_tax, i.impuesto, i.factor,
         i.base, i.tasa, i.importe
    FROM JSON_TABLE(p_cfdi,'$.pagos.pagos[*]' COLUMNS (
      ordinal_pago FOR ORDINALITY,
      NESTED PATH '$.documentos[*]' COLUMNS (
        ordinal_doc FOR ORDINALITY,
        NESTED PATH '$.impuestos[*]' COLUMNS (
          ordinal_tax FOR ORDINALITY,
          movimiento VARCHAR(10) PATH '$.movimiento',
          impuesto VARCHAR(5) PATH '$.impuesto',
          factor VARCHAR(12) PATH '$.factor',
          base DECIMAL(20,6) PATH '$.base',
          tasa DECIMAL(18,6) PATH '$.tasa',
          importe DECIMAL(20,6) PATH '$.importe'
        )
      )
    )) AS i
    JOIN pago_fiscal pf ON pf.documento_id=p_documento_id
      AND pf.ordinal=i.ordinal_pago
    JOIN pago_fiscal_documento pd ON pd.pago_fiscal_id=pf.id
      AND pd.ordinal=i.ordinal_doc
   WHERE i.ordinal_tax IS NOT NULL;

  INSERT INTO pago_fiscal_grupo_impuesto10
    (pago_fiscal_id,ordinal,total_retenidos,total_trasladados)
  SELECT pf.id,g.ordinal_grupo,g.retenidos,g.trasladados
    FROM JSON_TABLE(p_cfdi,'$.pagos.pagos[*]' COLUMNS (
      ordinal_pago FOR ORDINALITY,
      NESTED PATH '$.gruposImpuestos10[*]' COLUMNS (
        ordinal_grupo FOR ORDINALITY,
        retenidos DECIMAL(20,6) PATH '$.retenidos',
        trasladados DECIMAL(20,6) PATH '$.trasladados'
      )
    )) g JOIN pago_fiscal pf ON pf.documento_id=p_documento_id
      AND pf.ordinal=g.ordinal_pago
    WHERE g.ordinal_grupo IS NOT NULL AND v_version='1.0';

  INSERT INTO pago_fiscal_impuesto_pago
    (pago_fiscal_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe,grupo_impuesto_10)
  SELECT pf.id,i.movimiento,i.ordinal_tax,i.impuesto,i.factor,
         i.base,i.tasa,i.importe,i.grupo10
    FROM JSON_TABLE(p_cfdi,'$.pagos.pagos[*]' COLUMNS (
      ordinal_pago FOR ORDINALITY,
      NESTED PATH '$.impuestos[*]' COLUMNS (
        ordinal_tax FOR ORDINALITY,
        movimiento VARCHAR(10) PATH '$.movimiento',
        impuesto VARCHAR(5) PATH '$.impuesto',
        factor VARCHAR(12) PATH '$.factor',
        base DECIMAL(20,6) PATH '$.base',
        tasa DECIMAL(18,6) PATH '$.tasa',
        importe DECIMAL(20,6) PATH '$.importe',
        grupo10 SMALLINT UNSIGNED PATH '$.grupo10'
      )
    )) AS i
    JOIN pago_fiscal pf ON pf.documento_id=p_documento_id
      AND pf.ordinal=i.ordinal_pago
   WHERE i.ordinal_tax IS NOT NULL;

  IF v_version='2.0' THEN
  INSERT INTO pago_fiscal_totales(documento_id,monto_total_pagos)
  VALUES (p_documento_id, JSON_UNQUOTE(
    JSON_EXTRACT(p_cfdi,'$.pagos.totales.MontoTotalPagos')));

  INSERT INTO pago_fiscal_total_impuesto
    (documento_id,atributo,importe_mxn)
  SELECT p_documento_id,t.atributo,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,
      CONCAT('$.pagos.totales."',t.atributo,'"'))) AS DECIMAL(20,6))
    FROM JSON_TABLE(JSON_KEYS(p_cfdi,'$.pagos.totales'),'$[*]' COLUMNS (
      atributo VARCHAR(70) PATH '$'
    )) AS t
   WHERE t.atributo <> 'MontoTotalPagos' AND t.atributo REGEXP '^Total';
  END IF;
END$
DELIMITER ;
