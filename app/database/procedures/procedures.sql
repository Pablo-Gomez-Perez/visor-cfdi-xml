CREATE PROCEDURE `visor_cfdi`.`sp_rf007_importar_cfdi`(
    IN p_lote_id BIGINT UNSIGNED,
    IN p_ordinal INT UNSIGNED,
    IN p_repositorio_id BIGINT UNSIGNED,
    IN p_clave_objeto VARCHAR(700),
    IN p_sha256_hex CHAR(64),
    IN p_tamano BIGINT UNSIGNED,
    IN p_nombre_archivo VARCHAR(260),
    IN p_ruta_origen VARCHAR(1024),
    IN p_cfdi JSON,
    OUT p_duplicado TINYINT UNSIGNED
)
BEGIN
    DECLARE v_contribuyente_id BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_usuario_id BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_rfc_contribuyente VARCHAR(13) DEFAULT NULL;
    DECLARE v_rfc_emisor VARCHAR(13) DEFAULT NULL;
    DECLARE v_rfc_receptor VARCHAR(13) DEFAULT NULL;
    DECLARE v_sentido VARCHAR(10) DEFAULT NULL;
    DECLARE v_documento_id BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_archivo_id BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_clase VARCHAR(15) DEFAULT NULL;
    DECLARE v_uuid CHAR(36) DEFAULT NULL;
    DECLARE v_version VARCHAR(8) DEFAULT NULL;
    DECLARE v_tipo CHAR(1) DEFAULT NULL;
    DECLARE v_fecha_emision DATETIME DEFAULT NULL;

    SET p_duplicado = 0;
    SELECT contribuyente_id, usuario_id
      INTO v_contribuyente_id, v_usuario_id
      FROM lote_importacion
     WHERE id = p_lote_id AND estado = 'EN_PROCESO'
     FOR UPDATE;

    IF v_contribuyente_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'Lote RF-007 inexistente o cerrado';
    END IF;

    SELECT rfc INTO v_rfc_contribuyente
      FROM contribuyente WHERE id = v_contribuyente_id AND activo = 1;

    IF v_rfc_contribuyente IS NULL OR p_cfdi IS NULL
       OR p_sha256_hex IS NULL OR p_sha256_hex NOT REGEXP '^[0-9a-fA-F]{64}$'
       OR p_tamano IS NULL OR p_tamano = 0
       OR p_ordinal IS NULL OR p_ordinal = 0 THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'Metadatos de importacion invalidos';
    END IF;

    IF p_clave_objeto <> CONCAT('cfdi/', LEFT(LOWER(p_sha256_hex), 2), '/',
                                LOWER(p_sha256_hex), '.xml') THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'Clave de archivo distinta del SHA-256';
    END IF;

    SET v_uuid = UPPER(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi, '$.uuid')));
    SET v_version = JSON_UNQUOTE(JSON_EXTRACT(p_cfdi, '$.version'));
    SET v_tipo = JSON_UNQUOTE(JSON_EXTRACT(p_cfdi, '$.cfdi.tipo'));
    SET v_rfc_emisor = UPPER(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi, '$.emisor.rfc')));
    SET v_rfc_receptor = UPPER(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi, '$.receptor.rfc')));
    SET v_fecha_emision = STR_TO_DATE(
        JSON_UNQUOTE(JSON_EXTRACT(p_cfdi, '$.fecha')), '%Y-%m-%d %H:%i:%s');

    -- Mensajes diferenciados para evitar confundir un CFDI inválido con un SP desactualizado.
    IF v_uuid IS NULL OR CHAR_LENGTH(v_uuid) <> 36 THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'RF007: falta UUID del CFDI';
    END IF;
    IF v_version IS NULL OR v_version NOT IN ('3.3','4.0') THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'RF007: version CFDI no admitida';
    END IF;
    IF v_tipo IS NULL OR v_tipo NOT IN ('I','E','T','P','N') THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'RF007: tipo CFDI no admitido (I/E/T/P/N)';
    END IF;
    IF v_fecha_emision IS NULL THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'RF007: fecha de emision CFDI ausente o invalida';
    END IF;
    IF JSON_LENGTH(p_cfdi, '$.conceptos') IS NULL
       OR JSON_LENGTH(p_cfdi, '$.conceptos') = 0 THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'RF007: comprobante sin conceptos';
    END IF;

    IF v_rfc_contribuyente = v_rfc_emisor
       AND v_rfc_contribuyente = v_rfc_receptor THEN
        SET v_sentido = 'AMBOS';
    ELSEIF v_rfc_contribuyente = v_rfc_emisor THEN
        SET v_sentido = 'EMITIDO';
    ELSEIF v_rfc_contribuyente = v_rfc_receptor THEN
        SET v_sentido = 'RECIBIDO';
    ELSE
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'RFC de contribuyente ajeno al XML';
    END IF;

    INSERT INTO documento_fiscal(clase,uuid,version_estandar,fecha_emision)
    VALUES ('CFDI',v_uuid,v_version,v_fecha_emision)
    ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);
    SET v_documento_id = LAST_INSERT_ID();

    SELECT clase INTO v_clase
      FROM documento_fiscal WHERE id = v_documento_id FOR UPDATE;
    IF v_clase <> 'CFDI' THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'UUID ya registrado como otra familia documental';
    END IF;

    SELECT COUNT(*) INTO p_duplicado FROM cfdi
     WHERE documento_id = v_documento_id;

    IF p_duplicado = 1 THEN
        -- La identidad UUID no autoriza sobrescribir XML distinto.
        IF NOT EXISTS (
            SELECT 1 FROM documento_archivo da
            JOIN archivo_documental ad ON ad.id=da.archivo_id
            WHERE da.documento_id=v_documento_id
              AND da.tipo_uso='ORIGINAL'
              AND ad.sha256=UNHEX(p_sha256_hex)
        ) OR EXISTS (
            SELECT 1 FROM documento_archivo da
            JOIN archivo_documental ad ON ad.id=da.archivo_id
            WHERE da.documento_id=v_documento_id
              AND da.tipo_uso='ORIGINAL'
              AND ad.sha256<>UNHEX(p_sha256_hex)
        ) THEN
            SIGNAL SQLSTATE '45000'
               SET MESSAGE_TEXT = 'Conflicto UUID: XML original con SHA distinto';
        END IF;
    ELSE
        IF EXISTS (SELECT 1 FROM documento_participante
                    WHERE documento_id = v_documento_id) THEN
            SIGNAL SQLSTATE '45000'
               SET MESSAGE_TEXT = 'Documento previo parcialmente almacenado';
        END IF;

        INSERT INTO documento_participante
          (documento_id,rol,rfc,nombre_razon_social,regimen_fiscal)
        VALUES
          (v_documento_id,'E',v_rfc_emisor,
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.emisor.nombre')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.emisor.regimen')));

        INSERT INTO documento_participante
          (documento_id,rol,rfc,nombre_razon_social,regimen_fiscal,
           domicilio_fiscal_cp,residencia_fiscal,num_registro_fiscal_extranjero,uso_cfdi)
        VALUES
          (v_documento_id,'R',v_rfc_receptor,
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.nombre')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.regimen')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.domicilio')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.residencia')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.registroExtranjero')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.uso')));

        INSERT INTO cfdi
          (documento_id,tipo,serie,folio,lugar_expedicion,moneda,
           tipo_cambio,subtotal,descuento,total,forma_pago,metodo_pago,
           condiciones_pago,exportacion,confirmacion,no_certificado,sello_emisor)
        VALUES
          (v_documento_id,v_tipo,
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.serie')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.folio')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.lugar')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.moneda')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.tipoCambio')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.subtotal')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.descuento')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.total')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.forma')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.metodo')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.condiciones')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.exportacion')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.confirmacion')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.noCertificado')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.cfdi.sello')));

        INSERT INTO documento_timbre
          (documento_id,version_timbre,fecha_timbrado,
           rfc_proveedor_certificacion,no_certificado_sat,sello_cfd,sello_sat)
        VALUES
          (v_documento_id,
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.version')),
           STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.fecha')),
                       '%Y-%m-%d %H:%i:%s'),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.rfcPac')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.noCertificadoSat')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.selloCfd')),
           JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.selloSat')));

        -- Inserción de conceptos en una operación SQL: no N viajes JDBC.
        INSERT INTO cfdi_concepto
          (documento_id,ordinal,clave_prod_serv,no_identificacion,cantidad,
           clave_unidad,unidad,descripcion,valor_unitario,importe,descuento,objeto_impuesto)
        SELECT v_documento_id,c.ordinal,c.clave,c.identificacion,c.cantidad,
               c.unidad_clave,c.unidad,c.descripcion,c.valor_unitario,
               c.importe,c.descuento,c.objeto
        FROM JSON_TABLE(p_cfdi,'$.conceptos[*]' COLUMNS (
            ordinal FOR ORDINALITY,
            clave VARCHAR(12) PATH '$.clave',
            identificacion VARCHAR(100) PATH '$.identificacion',
            cantidad DECIMAL(20,6) PATH '$.cantidad',
            unidad_clave VARCHAR(10) PATH '$.unidadClave',
            unidad VARCHAR(50) PATH '$.unidad',
            descripcion VARCHAR(6000) PATH '$.descripcion',
            valor_unitario DECIMAL(20,6) PATH '$.valorUnitario',
            importe DECIMAL(20,6) PATH '$.importe',
            descuento DECIMAL(20,6) PATH '$.descuento',
            objeto VARCHAR(5) PATH '$.objeto'
        )) AS c;

        INSERT INTO cfdi_concepto_impuesto
          (concepto_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
        SELECT c.id,i.movimiento,i.ordinal,i.impuesto,i.factor,i.base,i.tasa,i.importe
          FROM JSON_TABLE(p_cfdi,'$.conceptos[*]' COLUMNS (
            ordinal_concepto FOR ORDINALITY,
            NESTED PATH '$.impuestos[*]' COLUMNS (
              ordinal FOR ORDINALITY,
              movimiento VARCHAR(10) PATH '$.movimiento',
              impuesto VARCHAR(5) PATH '$.impuesto',
              factor VARCHAR(12) PATH '$.factor',
              base DECIMAL(20,6) PATH '$.base',
              tasa DECIMAL(18,6) PATH '$.tasa',
              importe DECIMAL(20,6) PATH '$.importe'
            )
          )) AS i
          JOIN cfdi_concepto c
            ON c.documento_id=v_documento_id AND c.ordinal=i.ordinal_concepto
         WHERE i.ordinal IS NOT NULL;

        IF JSON_CONTAINS_PATH(p_cfdi,'one','$.totalesImpuestos') THEN
            INSERT INTO cfdi_impuesto_totales
              (documento_id,total_trasladados,total_retenidos)
            VALUES (v_documento_id,
              JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.totalesImpuestos.trasladados')),
              JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.totalesImpuestos.retenidos')));
        END IF;

        INSERT INTO cfdi_impuesto_global
          (documento_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
        SELECT v_documento_id,i.movimiento,i.ordinal,i.impuesto,i.factor,
               i.base,i.tasa,i.importe
          FROM JSON_TABLE(p_cfdi,'$.impuestos[*]' COLUMNS (
            ordinal FOR ORDINALITY,
            movimiento VARCHAR(10) PATH '$.movimiento',
            impuesto VARCHAR(5) PATH '$.impuesto',
            factor VARCHAR(12) PATH '$.factor',
            base DECIMAL(20,6) PATH '$.base',
            tasa DECIMAL(18,6) PATH '$.tasa',
            importe DECIMAL(20,6) PATH '$.importe'
          )) AS i;

        IF JSON_CONTAINS_PATH(p_cfdi,'one','$.informacionGlobal') THEN
            INSERT INTO cfdi_informacion_global
              (documento_id,periodicidad,meses,ejercicio)
            VALUES (v_documento_id,
              JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.informacionGlobal.periodicidad')),
              JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.informacionGlobal.meses')),
              JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.informacionGlobal.ejercicio')));
        END IF;

        INSERT INTO cfdi_relacion_grupo (documento_id,ordinal,tipo_relacion)
        SELECT v_documento_id,r.ordinal,r.tipo
          FROM JSON_TABLE(p_cfdi,'$.relaciones[*]' COLUMNS (
            ordinal FOR ORDINALITY,
            tipo VARCHAR(5) PATH '$.tipo'
          )) AS r;

        INSERT INTO cfdi_relacion_uuid (grupo_id,uuid_relacionado)
        SELECT g.id,r.uuid_relacionado
          FROM JSON_TABLE(p_cfdi,'$.relaciones[*]' COLUMNS (
            ordinal_grupo FOR ORDINALITY,
            NESTED PATH '$.uuids[*]' COLUMNS (
              uuid_relacionado CHAR(36) PATH '$'
            )
          )) AS r
          JOIN cfdi_relacion_grupo g ON g.documento_id=v_documento_id
             AND g.ordinal=r.ordinal_grupo
         WHERE r.uuid_relacionado IS NOT NULL;

        -- Complementos especializados bajo la MISMA transacción Java.
        IF v_tipo = 'P' THEN
            IF NOT JSON_CONTAINS_PATH(p_cfdi,'one','$.pagos') THEN
                SIGNAL SQLSTATE '45000'
                  SET MESSAGE_TEXT = 'CFDI P requiere complemento Pagos 2.0';
            END IF;
            CALL sp_rf007_importar_pago(v_documento_id,p_cfdi);
        ELSEIF v_tipo = 'N' THEN
            IF NOT JSON_CONTAINS_PATH(p_cfdi,'one','$.nomina') THEN
                SIGNAL SQLSTATE '45000'
                  SET MESSAGE_TEXT = 'CFDI N requiere complemento Nomina 1.2';
            END IF;
            CALL sp_rf007_importar_nomina(v_documento_id,p_cfdi);
        END IF;

        -- No afirmar que SAT/sellos/XSD fueron verificados al importar.
        INSERT INTO documento_verificacion
          (documento_id,tipo,resultado,detalle,fuente)
        VALUES
          (v_documento_id,'XSD','NO_VERIFICADO','Pendiente de RF de validacion','RF-007'),
          (v_documento_id,'SELLO','NO_VERIFICADO','Pendiente de RF de validacion','RF-007'),
          (v_documento_id,'SAT','NO_VERIFICADO','Pendiente de consulta SAT','RF-007');
    END IF;

    -- Contenido inmutable y separado de la base de datos.
    INSERT INTO archivo_documental
      (repositorio_id,clave_objeto,sha256,tamano_bytes,tipo_mime)
    VALUES
      (p_repositorio_id,p_clave_objeto,UNHEX(p_sha256_hex),p_tamano,'application/xml')
    ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);
    SET v_archivo_id=LAST_INSERT_ID();

    IF NOT EXISTS (
        SELECT 1 FROM archivo_documental
         WHERE id=v_archivo_id AND sha256=UNHEX(p_sha256_hex)
           AND tamano_bytes=p_tamano AND repositorio_id=p_repositorio_id
    ) THEN
        SIGNAL SQLSTATE '45000'
           SET MESSAGE_TEXT = 'Colision o alteracion de metadatos del XML';
    END IF;

    INSERT INTO contribuyente_documento
      (contribuyente_id,documento_id,sentido)
    VALUES (v_contribuyente_id,v_documento_id,v_sentido)
    ON DUPLICATE KEY UPDATE documento_id=VALUES(documento_id);

    INSERT INTO documento_archivo(documento_id,archivo_id,tipo_uso)
    VALUES (v_documento_id,v_archivo_id,'ORIGINAL')
    ON DUPLICATE KEY UPDATE archivo_id=VALUES(archivo_id);

    INSERT INTO importacion_archivo
      (lote_id,ordinal,nombre_archivo,ruta_origen,sha256,
       documento_id,archivo_documental_id,resultado)
    VALUES
      (p_lote_id,p_ordinal,p_nombre_archivo,p_ruta_origen,
       UNHEX(p_sha256_hex),v_documento_id,v_archivo_id,
       IF(p_duplicado=1,'DUPLICADO','ACEPTADO'));

    INSERT INTO bitacora_evento
      (usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo)
    VALUES (v_usuario_id,v_contribuyente_id,'DOCUMENTO_FISCAL',v_documento_id,
            IF(p_duplicado=1,'VINCULAR','IMPORTAR'),
            CONCAT('RF-007; UUID=',v_uuid,'; lote=',p_lote_id));
END;

CREATE PROCEDURE `visor_cfdi`.`sp_rf007_importar_nomina`(
 IN p_documento_id BIGINT UNSIGNED,
 IN p_cfdi JSON
)
BEGIN
  IF JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.version')) <> '1.2'
     OR JSON_EXTRACT(p_cfdi,'$.nomina.receptor.curp') IS NULL
  THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Complemento Nomina 1.2 incompleto';
  END IF;
  INSERT INTO nomina(documento_id,version_complemento,tipo_nomina,fecha_pago,
       fecha_inicial_pago,fecha_final_pago,num_dias_pagados,total_percepciones,
       total_deducciones,total_otros_pagos)
  VALUES (p_documento_id,'1.2',
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.tipo')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.fechaPago')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.fechaInicial')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.fechaFinal')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.dias')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.percepcionesTotal')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.deduccionesTotal')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.otrosTotal')));

  IF JSON_CONTAINS_PATH(p_cfdi,'one','$.nomina.emisor') THEN
    INSERT INTO nomina_emisor(documento_id,curp,registro_patronal,rfc_patron_origen,
         origen_recurso,monto_recurso_propio)
    VALUES (p_documento_id,
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.emisor.curp')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.emisor.registro')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.emisor.rfcPatron')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.emisor.origen')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.nomina.emisor.montoRecurso')));
  END IF;
  INSERT INTO nomina_receptor(documento_id,curp,num_seguridad_social,
       fecha_inicio_relacion_laboral,antiguedad,tipo_contrato,sindicalizado,
       tipo_jornada,tipo_regimen,num_empleado,departamento,puesto,riesgo_puesto,
       periodicidad_pago,banco,cuenta_bancaria,salario_base_cot_apor,
       salario_diario_integrado,clave_entidad_federativa)
  SELECT p_documento_id,r.curp,r.nss,r.inicio_laboral,r.antiguedad,
       r.tipo_contrato,r.sindicalizado,r.jornada,r.regimen,r.empleado,
       r.departamento,r.puesto,r.riesgo,r.periodicidad,r.banco,r.cuenta,
       r.salario_base,r.salario_integrado,r.entidad
    FROM JSON_TABLE(p_cfdi,'$.nomina.receptor' COLUMNS (
      curp VARCHAR(18) PATH '$.curp',
      nss VARCHAR(20) PATH '$.nss',
      inicio_laboral DATE PATH '$.inicioLaboral',
      antiguedad VARCHAR(20) PATH '$.antiguedad',
      tipo_contrato VARCHAR(5) PATH '$.tipoContrato',
      sindicalizado CHAR(2) PATH '$.sindicalizado',
      jornada VARCHAR(5) PATH '$.jornada',
      regimen VARCHAR(5) PATH '$.regimen',
      empleado VARCHAR(25) PATH '$.empleado',
      departamento VARCHAR(120) PATH '$.departamento',
      puesto VARCHAR(120) PATH '$.puesto',
      riesgo VARCHAR(5) PATH '$.riesgo',
      periodicidad VARCHAR(5) PATH '$.periodicidad',
      banco VARCHAR(5) PATH '$.banco',
      cuenta VARCHAR(60) PATH '$.cuenta',
      salario_base DECIMAL(20,6) PATH '$.salarioBase',
      salario_integrado DECIMAL(20,6) PATH '$.salarioIntegrado',
      entidad VARCHAR(5) PATH '$.entidad'
    )) AS r;

  INSERT INTO nomina_percepcion
    (documento_id,ordinal,tipo_percepcion,clave,concepto,importe_gravado,importe_exento)
  SELECT p_documento_id,p.ordinal,p.tipo,p.clave,p.concepto,p.gravado,p.exento
    FROM JSON_TABLE(p_cfdi,'$.nomina.percepciones[*]' COLUMNS (
      ordinal FOR ORDINALITY,
      tipo VARCHAR(5) PATH '$.tipo',
      clave VARCHAR(20) PATH '$.clave',
      concepto VARCHAR(250) PATH '$.concepto',
      gravado DECIMAL(20,6) PATH '$.gravado',
      exento DECIMAL(20,6) PATH '$.exento'
    )) AS p;

  INSERT INTO nomina_deduccion (documento_id,ordinal,tipo_deduccion,clave,concepto,importe)
  SELECT p_documento_id,d.ordinal,d.tipo,d.clave,d.concepto,d.importe
    FROM JSON_TABLE(p_cfdi,'$.nomina.deducciones[*]' COLUMNS (
      ordinal FOR ORDINALITY,
      tipo VARCHAR(5) PATH '$.tipo',
      clave VARCHAR(20) PATH '$.clave',
      concepto VARCHAR(250) PATH '$.concepto',
      importe DECIMAL(20,6) PATH '$.importe'
    )) AS d;

  INSERT INTO nomina_otro_pago
    (documento_id,ordinal,tipo_otro_pago,clave,concepto,importe)
  SELECT p_documento_id,o.ordinal,o.tipo,o.clave,o.concepto,o.importe
    FROM JSON_TABLE(p_cfdi,'$.nomina.otros[*]' COLUMNS (
      ordinal FOR ORDINALITY,
      tipo VARCHAR(5) PATH '$.tipo',
      clave VARCHAR(20) PATH '$.clave',
      concepto VARCHAR(250) PATH '$.concepto',
      importe DECIMAL(20,6) PATH '$.importe'
    )) AS o;

  INSERT INTO nomina_otro_pago_subsidio(otro_pago_id,subsidio_causado)
  SELECT op.id,o.subsidio
    FROM JSON_TABLE(p_cfdi,'$.nomina.otros[*]' COLUMNS (
      ordinal FOR ORDINALITY,
      subsidio DECIMAL(20,6) PATH '$.subsidio'
    )) AS o
    JOIN nomina_otro_pago op ON op.documento_id=p_documento_id
      AND op.ordinal=o.ordinal
   WHERE o.subsidio IS NOT NULL;

  INSERT INTO nomina_incapacidad
    (documento_id,ordinal,dias_incapacidad,tipo_incapacidad,importe_monetario)
  SELECT p_documento_id,i.ordinal,i.dias,i.tipo,i.importe
    FROM JSON_TABLE(p_cfdi,'$.nomina.incapacidades[*]' COLUMNS (
      ordinal FOR ORDINALITY,
      dias INT PATH '$.dias',
      tipo VARCHAR(5) PATH '$.tipo',
      importe DECIMAL(20,6) PATH '$.importe'
    )) AS i;
END;

CREATE PROCEDURE `visor_cfdi`.`sp_rf007_importar_pago`(
 IN p_documento_id BIGINT UNSIGNED,
 IN p_cfdi JSON
)
BEGIN
  IF JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.pagos.version')) <> '2.0'
     OR JSON_LENGTH(p_cfdi,'$.pagos.pagos') < 1
     OR JSON_EXTRACT(p_cfdi,'$.pagos.totales.MontoTotalPagos') IS NULL
  THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Complemento Pagos 2.0 incompleto';
  END IF;

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
     saldo_insoluto,objeto_impuesto)
  SELECT pf.id, d.ordinal_doc, d.uuid, d.serie, d.folio, d.moneda,
         d.equivalencia,d.parcialidad,d.saldo_anterior,d.pagado,
         d.saldo_insoluto,d.objeto
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
        objeto VARCHAR(5) PATH '$.objeto'
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

  INSERT INTO pago_fiscal_impuesto_pago
    (pago_fiscal_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
  SELECT pf.id,i.movimiento,i.ordinal_tax,i.impuesto,i.factor,
         i.base,i.tasa,i.importe
    FROM JSON_TABLE(p_cfdi,'$.pagos.pagos[*]' COLUMNS (
      ordinal_pago FOR ORDINALITY,
      NESTED PATH '$.impuestos[*]' COLUMNS (
        ordinal_tax FOR ORDINALITY,
        movimiento VARCHAR(10) PATH '$.movimiento',
        impuesto VARCHAR(5) PATH '$.impuesto',
        factor VARCHAR(12) PATH '$.factor',
        base DECIMAL(20,6) PATH '$.base',
        tasa DECIMAL(18,6) PATH '$.tasa',
        importe DECIMAL(20,6) PATH '$.importe'
      )
    )) AS i
    JOIN pago_fiscal pf ON pf.documento_id=p_documento_id
      AND pf.ordinal=i.ordinal_pago
   WHERE i.ordinal_tax IS NOT NULL;

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
END;

CREATE PROCEDURE `visor_cfdi`.`sp_rf007_importar_retencion`(
    IN p_lote_id BIGINT UNSIGNED,
    IN p_ordinal INT UNSIGNED,
    IN p_repositorio_id BIGINT UNSIGNED,
    IN p_clave_objeto VARCHAR(700),
    IN p_sha256_hex CHAR(64),
    IN p_tamano BIGINT UNSIGNED,
    IN p_nombre_archivo VARCHAR(260),
    IN p_ruta_origen VARCHAR(1024),
    IN p_cfdi JSON,
    OUT p_duplicado TINYINT UNSIGNED
)
BEGIN
  DECLARE v_contribuyente BIGINT UNSIGNED DEFAULT NULL;
  DECLARE v_usuario BIGINT UNSIGNED DEFAULT NULL;
  DECLARE v_rfc VARCHAR(13);
  DECLARE v_emisor VARCHAR(13);
  DECLARE v_receptor VARCHAR(13);
  DECLARE v_sentido VARCHAR(10);
  DECLARE v_uuid CHAR(36);
  DECLARE v_doc BIGINT UNSIGNED;
  DECLARE v_archivo BIGINT UNSIGNED;
  DECLARE v_clase VARCHAR(15);
  SET p_duplicado=0;

  SELECT contribuyente_id,usuario_id INTO v_contribuyente,v_usuario
    FROM lote_importacion WHERE id=p_lote_id AND estado='EN_PROCESO' FOR UPDATE;
  IF v_contribuyente IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lote de importacion no disponible';
  END IF;
  SELECT rfc INTO v_rfc FROM contribuyente
    WHERE id=v_contribuyente AND activo=1;
  SET v_emisor = UPPER(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.emisor.rfc')));
  SET v_receptor = UPPER(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.rfc')));
  SET v_uuid = UPPER(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.uuid')));

  IF v_rfc IS NULL OR p_sha256_hex NOT REGEXP '^[0-9a-fA-F]{64}$'
     OR p_tamano IS NULL OR p_tamano=0 OR p_ordinal IS NULL OR p_ordinal=0
     OR p_clave_objeto <> CONCAT('cfdi/',LEFT(LOWER(p_sha256_hex),2),'/',
                                LOWER(p_sha256_hex),'.xml')
     OR JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.version')) <> '2.0'
     OR JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.clase')) <> 'RETENCION'
     OR v_uuid IS NULL OR v_uuid NOT REGEXP
           '^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$'
  THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Datos de retenciones invalidos';
  END IF;

  IF v_rfc=v_emisor AND v_rfc=v_receptor THEN
    SET v_sentido='AMBOS';
  ELSEIF v_rfc=v_emisor THEN
    SET v_sentido='EMITIDO';
  ELSEIF v_rfc=v_receptor THEN
    SET v_sentido='RECIBIDO';
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RFC contribuyente no pertenece a retencion';
  END IF;

  INSERT INTO documento_fiscal(clase,uuid,version_estandar,fecha_emision)
  VALUES ('RETENCION',v_uuid,'2.0',STR_TO_DATE(
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.fecha')),'%Y-%m-%d %H:%i:%s'))
  ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);
  SET v_doc=LAST_INSERT_ID();
  SELECT clase INTO v_clase FROM documento_fiscal WHERE id=v_doc FOR UPDATE;
  IF v_clase <> 'RETENCION' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='UUID ya registrado en otra familia';
  END IF;
  SELECT COUNT(*) INTO p_duplicado FROM retencion WHERE documento_id=v_doc;
  IF p_duplicado=1 THEN
    IF NOT EXISTS (
       SELECT 1 FROM documento_archivo da JOIN archivo_documental a
         ON da.archivo_id=a.id
       WHERE da.documento_id=v_doc AND da.tipo_uso='ORIGINAL'
         AND a.sha256=UNHEX(p_sha256_hex)
    ) OR EXISTS (
       SELECT 1 FROM documento_archivo da JOIN archivo_documental a
         ON da.archivo_id=a.id
       WHERE da.documento_id=v_doc AND da.tipo_uso='ORIGINAL'
         AND a.sha256<>UNHEX(p_sha256_hex)
    ) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='UUID de retencion con SHA distinto';
    END IF;
  ELSE
    IF EXISTS (SELECT 1 FROM documento_participante WHERE documento_id=v_doc) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Documento previamente incompleto';
    END IF;
    INSERT INTO documento_participante(documento_id,rol,rfc,nombre_razon_social,regimen_fiscal)
    VALUES (v_doc,'E',v_emisor,
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.emisor.nombre')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.emisor.regimen')));
    INSERT INTO documento_participante(documento_id,rol,rfc,nombre_razon_social,
        domicilio_fiscal_cp,num_registro_fiscal_extranjero)
    VALUES (v_doc,'R',v_receptor,
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.nombre')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.domicilio')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptor.registroExtranjero')));
    INSERT INTO retencion(documento_id,folio_interno,clave_retencion,lugar_expedicion,
        certificado_numero,sello_emisor)
    VALUES (v_doc,
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.retencion.folio')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.retencion.clave')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.retencion.lugar')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.retencion.certificado')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.retencion.sello')));
    INSERT INTO retencion_periodo(documento_id,mes_inicial,mes_final,ejercicio)
    VALUES(v_doc,
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.periodo.mesInicial')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.periodo.mesFinal')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.periodo.ejercicio')));
    INSERT INTO retencion_totales(documento_id,monto_total_operacion,
        monto_total_gravado,monto_total_exento,monto_total_retenido)
    VALUES (v_doc,
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.totalesRetencion.operacion')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.totalesRetencion.gravado')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.totalesRetencion.exento')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.totalesRetencion.retenido')));
    INSERT INTO retencion_impuesto(documento_id,ordinal,base_retencion,impuesto,
        importe_retencion,tipo_pago_retencion)
    SELECT v_doc,i.ordinal,i.base,i.impuesto,i.importe,i.tipo_pago
      FROM JSON_TABLE(p_cfdi,'$.impuestosRetencion[*]' COLUMNS (
        ordinal FOR ORDINALITY,
        base DECIMAL(20,6) PATH '$.base',
        impuesto VARCHAR(10) PATH '$.impuesto',
        importe DECIMAL(20,6) PATH '$.importe',
        tipo_pago VARCHAR(5) PATH '$.tipoPago'
      )) AS i;
    IF JSON_CONTAINS_PATH(p_cfdi,'one','$.receptorExtranjero') THEN
      INSERT INTO retencion_receptor_extranjero
        (documento_id,nacionalidad,num_registro_fiscal)
      VALUES (v_doc,
        JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptorExtranjero.nacionalidad')),
        JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.receptorExtranjero.registro')));
    END IF;
    INSERT INTO retencion_relacion_uuid(documento_id,uuid_relacionado)
    SELECT v_doc,r.uuid FROM JSON_TABLE(
      p_cfdi,'$.relacionadosRetencion[*]' COLUMNS (uuid CHAR(36) PATH '$')) AS r;
    INSERT INTO documento_timbre(documento_id,version_timbre,fecha_timbrado,
        rfc_proveedor_certificacion,no_certificado_sat,sello_cfd,sello_sat)
    VALUES (v_doc,
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.version')),
      STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.fecha')),
          '%Y-%m-%d %H:%i:%s'),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.rfcPac')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.noCertificadoSat')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.selloCfd')),
      JSON_UNQUOTE(JSON_EXTRACT(p_cfdi,'$.timbre.selloSat')));
    INSERT INTO documento_verificacion(documento_id,tipo,resultado,detalle,fuente)
    VALUES (v_doc,'XSD','NO_VERIFICADO','Pendiente validacion','RF-007'),
      (v_doc,'SELLO','NO_VERIFICADO','Pendiente validacion','RF-007'),
      (v_doc,'SAT','NO_VERIFICADO','Pendiente consulta SAT','RF-007');
  END IF;

  INSERT INTO archivo_documental(repositorio_id,clave_objeto,sha256,tamano_bytes,tipo_mime)
  VALUES (p_repositorio_id,p_clave_objeto,UNHEX(p_sha256_hex),p_tamano,'application/xml')
  ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);
  SET v_archivo=LAST_INSERT_ID();
  IF NOT EXISTS (SELECT 1 FROM archivo_documental
      WHERE id=v_archivo AND repositorio_id=p_repositorio_id
        AND sha256=UNHEX(p_sha256_hex) AND tamano_bytes=p_tamano) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Metadatos XML inconsistentes';
  END IF;
  INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido)
  VALUES(v_contribuyente,v_doc,v_sentido)
  ON DUPLICATE KEY UPDATE documento_id=VALUES(documento_id);
  INSERT INTO documento_archivo(documento_id,archivo_id,tipo_uso)
  VALUES(v_doc,v_archivo,'ORIGINAL')
  ON DUPLICATE KEY UPDATE archivo_id=VALUES(archivo_id);
  INSERT INTO importacion_archivo(lote_id,ordinal,nombre_archivo,ruta_origen,sha256,
      documento_id,archivo_documental_id,resultado)
  VALUES(p_lote_id,p_ordinal,p_nombre_archivo,p_ruta_origen,UNHEX(p_sha256_hex),
      v_doc,v_archivo,IF(p_duplicado=1,'DUPLICADO','ACEPTADO'));
  INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,
      accion,motivo)
  VALUES(v_usuario,v_contribuyente,'DOCUMENTO_FISCAL',v_doc,
      IF(p_duplicado=1,'VINCULAR','IMPORTAR'),
      CONCAT('RF-007 Retenciones 2.0 UUID=',v_uuid,' lote=',p_lote_id));
END;