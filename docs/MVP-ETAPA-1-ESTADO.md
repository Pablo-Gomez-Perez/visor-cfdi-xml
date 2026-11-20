# MVP etapa 1 — Estado de ingeniería

## Base y publicación

- Base `main`: `d4263d86348c00e6896f0bdabfe48bae08ea395f`.
- Rama: `feat/mvp-etapa-1-clasificacion-fiscal`.
- [PR #21](https://github.com/Pablo-Gomez-Perez/visor-cfdi-xml/pull/21), borrador contra `main`; no fusionada.
- RF-020 y su experiencia de usuario estaban integrados en la base. Se ampliaron, no se reconstruyeron.
- La rama se publicó mediante el conector GitHub porque el transporte Git local carece de credenciales de escritura. Los SHA de algunos commits equivalentes difieren por metadatos; sus árboles coinciden. El último cambio funcional remoto es `08e8b9a`.
- La hoja `06-EGRESOS JUNIO.xlsx` se inspeccionó sólo como referencia de estructura y fórmulas. No se publicaron datos privados.

## Matriz de implementación

| Fase | Estado | Evidencia |
|---|---|---|
| 0. Inspección | Completa | Esquema, documentación, PR #20, seguridad, servicios y Swing revisados. |
| 1. RF-021 | Implementada y probada | Clasificación mixta y parcial por concepto/impuesto, precedencia, revisión, historial, diálogo integrado con RF-020. |
| 2. RF-022 | Implementada y probada | Originales por concepto, global, DR, P y Totales; conciliación y agrupación SQL con rastro. |
| 3. RF-023 | Implementada con límites jurídicos visibles | IVA documental/realizado/tratamiento; REP por FechaPago, manual pendiente, parcialidades, otras monedas, ajustes, cancelaciones e incidencias. |
| 4. RF-024 | Implementada y probada | Terceros, obligaciones, aplicaciones, movimientos, ajustes y saldos documentados históricos. |
| 5. Carátulas | Integradas y probadas | Ingresos, egresos, IVA, IEPS documental, ISR/retenciones, terceros, incidencias, detalle y CSV. |
| 6–7. Integración y pruebas | Completas en Maven/CI | 113 unitarias y 16 integraciones; escenario integral, EXPLAIN sintético y regresión de zona horaria. |
| 8. Documentación | Actualizada | RF-021 a RF-024, MOTOR-FISCAL, arquitectura, UI, seguridad, README y este estado. |
| 9. Publicación | PR borrador publicada | Revisión manual de WindowBuilder pendiente; no marcar lista ni fusionar. |

No quedan funciones obligatorias simuladas ni métodos `TODO` dentro de esta etapa. La verificación visual real con WindowBuilder es la única comprobación del Definition of Done pendiente en el entorno disponible.

## Decisiones arquitectónicas

1. Se reutilizaron `clasificacion_concepto`, `clasificacion_impuesto_concepto`, `tratamiento_impuesto`, `obligacion`, `movimiento_pago`, `aplicacion_pago`, `ajuste_obligacion` y la bitácora. Originales XML, clasificación, evidencia y derivados permanecen separados.
2. La distribución detallada sustituye la clasificación general del concepto, incluso para el remanente pendiente. Sin detalle, la categoría general sirve de predeterminado. Nunca se suman ambos importes.
3. Las distribuciones se validan con `BigDecimal`, se guardan atómicamente con revisión documental y bloqueo, y generan historial anterior/resultante. Un cambio sin efecto no crea evento.
4. `ImpuestosDR` es fuente del detalle fiscal del REP; `ImpuestosP` y `Totales` concilian. No se suman los tres. `FechaPago` define el periodo financiero. PUE sin evidencia no confirma cobro o pago.
5. Las carátulas separan emisión, realización financiera, tratamientos y pendientes. No suman monedas diferentes ni presentan ISR o IEPS definitivos. Estado SAT desconocido, cancelación, sustitución, falta de evidencia o diferencias generan incidencias.
6. Los servicios revalidan permiso y asignación explícita al contribuyente. Los nuevos permisos se incorporan incrementalmente, sin restaurar revocaciones.
7. Los filtros MySQL `DATETIME` enlazan límites civiles como texto ISO con `CAST(... AS DATETIME)`. Esto preserva la medianoche aunque la JVM use `America/Mexico_City` y JDBC UTC.
8. Swing usa paneles `JPanel`/diálogos `JDialog`, `GroupLayout`, constructores públicos sin argumentos y `SwingWorker` con descarte de respuestas obsoletas. No se accede a JPA en constructores ni renderizadores.

## Persistencia y archivos modificados

- No hay DDL ni procedimientos nuevos. La etapa usa el esquema canónico y las migraciones anteriores; `hibernate.hbm2ddl.auto=validate` permanece. No se ejecutaron migraciones sobre datos de usuario.
- Persistencia: entidades JPA existentes mapeadas para clasificaciones, obligaciones, movimientos, aplicaciones y ajustes; `persistence.xml` amplía el registro.
- Negocio: `app/src/main/java/com/kathsoft/visor/app/service/fiscal/` contiene distribución, impuestos, conciliación, IVA, evidencia, terceros, carátulas y exportación.
- Seguridad: `Permisos`, bootstrap incremental de `UsuariosService`, `AccesoFiscal`.
- UI: `Fr_DatosClasificacionDetalle`, `Fr_DatosEvidencia`, `PanelResumenFiscal`; integración con `PanelClasificacionOperaciones` y `Fr_Principal`.
- Pruebas: `app/src/test/java/com/kathsoft/visor/app/service/fiscal/` y regresiones de importación, operación y UI.
- Documentos: `README.md`, `docs/ARQUITECTURA.md`, `docs/UI-SWING-WINDOWBUILDER.md`, `docs/RF-003.md`, `docs/RF-020.md`, `docs/RF-021.md` a `docs/RF-024.md`, `docs/MOTOR-FISCAL.md` y este seguimiento.

## Verificación y correcciones

- Comando local exacto: `mvn --batch-mode --no-transfer-progress -f app/pom.xml verify` — **BUILD SUCCESS**, 113 unitarias, 16 integraciones Testcontainers MySQL 8, cero fallas y tres integraciones XSD omitidas por faltar el espejo XSD local. Duración 05:31 min; 19-11-2026, zona México.
- [GitHub Actions 44678765691](https://github.com/Pablo-Gomez-Perez/visor-cfdi-xml/actions/runs/44678765691) — **BUILD SUCCESS**, 113 unitarias y 16 integraciones, cero omisiones; instala XSD SAT desde revisión fija. Duración 04:00 min; 20-11-2026 UTC.
- Escenario MySQL sintético de dos meses: CFDI mixtos, IVA 16/0/exento, IEPS, retenciones, PUE/PPD, pagos 80 + 80 en meses distintos, factura de ejercicio anterior, otras monedas, ajustes, cancelación, sustitución, dos titulares y más de una página. Comprueba que el IVA original de 160 no se duplica; importes y trazas se contrastan con expectativas independientes.
- Pruebas de permisos, revocación, aislamiento, rollback, concurrencia y agrupación tributaria parcial. EXPLAIN se ejecutó sobre datos sintéticos; no equivale a benchmark de producción con decenas de miles de CFDI.
- Fallas corregidas durante CI: sentido incorrecto de un CFDI sintético RF-020, expresión SQL `:categoriaGROUP`, residuos monetarios por `HALF_UP`, firma de retenciones DR/P, `ObjetoImp` ausente en 3.3 y límites civiles `DATETIME` desplazados por la zona horaria. También se corrigieron enlace manual/REP duplicado y trazabilidad de facturas de meses anteriores.
- `git diff --check` sin problemas. Las pruebas Swing cubren construcción sin persistencia, acciones, permisos y respuestas asíncronas; no sustituyen abrir los formularios en Eclipse Design.

## Riesgos y límites visibles

- Eclipse Snap inicia, pero WindowBuilder no está instalado en Snap ni en `~/.p2`. Falta abrir los formularios en **Design** y comprobar edición y tamaños 900×700 y 740×490. No se declara compatibilidad visual comprobada.
- Los saldos históricos dependen de movimientos y obligaciones disponibles; ausencia de historia no equivale a saldo inicial cero. `v_saldo_obligacion` no representa cierres históricos por sí misma.
- Un REP puede referir UUID sin FK a factura; el servicio valida disponibilidad del titular. La vinculación manual y REP requiere conciliación explícita para evitar doble evidencia.
- El último estado SAT almacenado no reconstruye el estado legal en cada fecha histórica. Los casos sin desglose tributario, tipo de cambio o fundamento aplicable quedan pendientes de revisión profesional.
- `efecto_fiscal_periodo` no tiene moneda ni fuente suficientes para materializar esta etapa sin política de invalidación. Los resultados se calculan desde originales, clasificaciones y evidencia.
- Exportación CSV pagina y revalida acceso; cambios concurrentes entre páginas pueden variar el corte. Se recomienda exportar con operaciones de carga quietas.
- ISR por régimen e IEPS definitivo están fuera de alcance, según `docs/MOTOR-FISCAL.md`. Ninguna carátula se presenta como declaración definitiva.

## Próxima actividad

1. Publicar este cierre documental y actualizar cuerpo de PR #21 con matriz, CI, instalación y límites.
2. Abrir los formularios en Eclipse con WindowBuilder instalado y verificar edición visual y resoluciones. Registrar resultado antes de marcar la PR lista para revisión final.
3. Revisar la PR y fusionar sólo mediante autorización del responsable; esta orden no autoriza fusión.
