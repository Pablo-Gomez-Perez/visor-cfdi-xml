# RF-007 / RF-008 — Compatibilidad histórica Pagos 1.0

## Alcance
Se conserva CFDI 3.3/4.0 y Pagos 2.0. Se agrega recepción de pagos 1.0 con CFDI 3.3, identificando namespace y Version; no se convierte el XML ni se altera su sello. No abarca CFDI 3.2, Retenciones 1.0 ni otros complementos no implementados.

| Atributo | 1.0 | 2.0 |
| --- | --- | --- |
| Namespace | http://www.sat.gob.mx/Pagos | http://www.sat.gob.mx/Pagos20 |
| Totales en MXN | No existe | Requerido |
| TipoCambioDR, MetodoDePagoDR | Se conservan como campos históricos | No aplica |
| EquivalenciaDR, ObjetoImpDR | No aplica | Se conserva representación previa |
| ImpPagado | Puede omitirse conforme a las reglas históricas | Requerido |
| Impuestos | Grupos de pago, totales retenidos/trasladados en MonedaP | ImpuestosDR, ImpuestosP, Totales |

El lector Pagos 1.0 conserva el identificador histórico no UUID en una columna independiente. No se sintetiza un UUID, base de impuesto, importe pagado ni Totales. La presencia de atributos no implica validación de reglas fiscales, vigencia o firmas; RF-008 aplica XSD local, no consultas SAT.

## Instalación para BD ya creada
1. Respaldar base de datos, rutinas, privilegios y repositorio XML. Cerrar la aplicación y detener importaciones. Verificar que la base corresponde al esquema y procedimientos de main anteriores a esta PR.
2. Seleccionar la base existente (normalmente visor_cfdi).
3. Ejecutar UNA sola vez, en modo script, app/database/migrations/20261115_rf007_pagos10.sql. No ejecutar el esquema de creación completo sobre datos reales.
4. La migración añade dos tablas, columnas y constraints; reemplaza únicamente sp_rf007_importar_pago. Los DDL MySQL producen commits implícitos: no hay rollback global de DDL. Si falla, detenerse, revisar qué instrucciones se aplicaron y restaurar/conciliar desde respaldo. No usar --force ni volver a ejecutar a ciegas.
5. Comprobar SHOW CREATE PROCEDURE sp_rf007_importar_pago, permisos EXECUTE del usuario MySQL y acceso a las nuevas tablas. El definer será la cuenta que instale la rutina; utilizar una cuenta autorizada y mantener privilegios mínimos.
6. Actualizar XSD con scripts/instalar-esquemas-sat.sh; la distribución local debe incluir Pagos10.xsd y dependencias. Luego desplegar el código Java.

Los registros con Totales persistidos se reconocen como Pagos 2.0 y se insertan en pago_fiscal_complemento; no se presupone una versión para otros registros heredados. El dump de main de 15/11/2026 omitía semillas, vistas y triggers. El esquema para instalaciones nuevas recupera los objetos de integridad y datos iniciales del esquema previo RF-009, manteniendo las tablas del volcado actualizado. No se reinstalan esos objetos sobre la BD operativa mediante esta migración; verificar que existan allí.

Instalaciones nuevas: ejecutar el esquema y luego app/database/procedures/procedures.sql como scripts completos. Se eliminó el DEFINER root@localhost fijo del volcado para permitir instalaciones con una cuenta habilitada; no ejecutar el script completo si solo se necesita actualizar Pagos.

## Diagnóstico y transacciones
- La lectura de XML conserva el procesamiento StAX concurrente acotado; los rechazos por formato, versión/complemento incompatible, tamaño o RFC ajeno se registran con ordinal, archivo, ruta, SHA-256 si disponible y metadatos recuperables (UUID, versión, tipo, complemento/namespace). Diagnóstico adicional sobre el archivo temporal solo en caso de rechazo; no se imprime el XML completo.
- Los rechazados se registran independientemente en importacion_archivo con resultado RECHAZADO y detalle limitado a 1000 caracteres. Se mantiene el archivo fuente; no se copia un rechazado al repositorio.
- Los válidos se confirman en grupos de ocho. El estado PARCIAL indica rechazos; FINALIZADO significa ejecución sin rechazos. Contadores explícitos en UI y último rechazo al terminar.
- Fallos de BD, permisos, E/S, repositorio o transacción continúan siendo críticos. No se confunde una rutina obsoleta con un rechazo documental. Un fallo crítico revierte la transacción en curso, no las previamente confirmadas. No existe atomicidad entre archivos y BD como ya se documenta en RF-007.
- Para consultar todos los rechazos de un lote desde MySQL con una cuenta autorizada:

    SELECT ordinal,nombre_archivo,ruta_origen,HEX(sha256) AS sha256,detalle
      FROM importacion_archivo
     WHERE lote_id = <ID_DEL_LOTE> AND resultado='RECHAZADO'
     ORDER BY ordinal;

No se agregaron permisos nuevos a usuarios de aplicación ni nuevas ventanas. Se conserva GroupLayout y SwingWorker.

## Pruebas
Ejecutar mvn --batch-mode --no-transfer-progress -f app/pom.xml verify con Docker y XSD local instalado. LectorPagos10Test cubre estructura, campos opcionales, impuestos agrupados y compatibilidad 2.0. ImportacionPagosHistoricosIT verifica las rutinas reales en MySQL 8, lote mixto, persistencia, rechazo y duplicados. ValidadorXsdSatRealIT verifica que Pagos10.xsd compile y rechace XML deliberadamente incompleto. Las pruebas usan datos sintéticos, no XML privados. La comprobación visual del panel en Eclipse WindowBuilder y la migración en una copia de la BD real siguen siendo requisitos de despliegue.

## Referencias
- SAT, estándar histórico Pagos 1.0: https://www.sat.gob.mx/cs/Satellite?blobcol=urldata&blobkey=id&blobtable=MungoBlobs&blobwhere=1461173382719&ssbinary=true
- SAT, guía de llenado histórica: https://wwwmat.sat.gob.mx/cs/Satellite?blobcol=urldata&blobkey=id&blobtable=MungoBlobs&blobwhere=1461173739622&ssbinary=true
- XSD SAT histórico: http://www.sat.gob.mx/sitio_internet/cfd/Pagos/Pagos10.xsd
