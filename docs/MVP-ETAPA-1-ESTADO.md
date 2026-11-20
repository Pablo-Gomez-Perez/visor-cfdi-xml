# MVP etapa 1 — Seguimiento de ingeniería

## Base comprobada

- Base: `d4263d86348c00e6896f0bdabfe48bae08ea395f`, `main` limpio al comenzar.
- Rama: `feat/mvp-etapa-1-clasificacion-fiscal`.
- PR #20 integrada; conserva SplitPane, edición reutilizable, atajos, historial y resumen RF-020.
- `git fetch origin` falló por autenticación SSH. La consulta HTTPS de `main` confirmó el mismo commit.
- No había PR abiertas al inspeccionar GitHub.
- CI heredado de RF-020: ejecución `44489981223`, fallida. Causa comprobada: el test insertaba RECIBIDO para el titular emisor. Corregido en `aaaf420`, conservando el trigger.
- Referencia Excel disponible: se inspeccionaron únicamente estructura y familias de fórmulas. Hojas EGRESOS, REP, Hoja1 y Hoja2. No se incorporan datos privados.

## Matriz de implementación

| Fase | Estado | Resultado verificable |
|---|---|---|
| 0. Inspección | Completada | Base, capas, esquema, rutinas y documentación revisados |
| 1. RF-021 | Implementada; revisión final en CI | Distribuciones, tratamientos, revisión, historial, RF-020 e interfaz |
| 2. RF-022 | Implementada; revisión final en CI | Cinco niveles originales y conciliación concepto/global/totales y DR/P/Totales |
| 3. RF-023 | Implementada con límites jurídicos; revisión final en CI | REP por FechaPago, manual pendiente, parcialidades y vinculación posterior sin duplicar |
| 4. RF-024 | Implementada; revisión final en CI | Terceros, obligaciones, aplicaciones, ajustes y saldos históricos |
| 5. Carátulas | Integradas; revisión final en CI | Ingresos, egresos, IVA, IEPS documental, retenciones, trazabilidad y CSV |
| 6–7. Integración y pruebas | En curso | 112 unitarias locales; CI MySQL anterior satisfactorio; revisión actual pendiente |
| 8. Documentación | En curso | Reglas, arquitectura, UI, roles y estado actualizados |
| 9. Publicación | PR borrador creada | Rama publicada por GitHub; sin fusionar |

## Decisiones e invariantes

1. Reutilizar `clasificacion_concepto`, `clasificacion_impuesto_concepto`, `tratamiento_impuesto`, `obligacion`, `movimiento_pago`, `aplicacion_pago` y `ajuste_obligacion`.
2. Mantener originales independientes de distribuciones, evidencia y cálculos. `BigDecimal`/`DECIMAL`; no mezclar monedas.
3. Distribución detallada sustituye la categoría general para el concepto completo, incluido su remanente pendiente. Sin detalle se hereda la categoría general. Nunca sumar ambos.
4. Guardar distribuciones en una transacción, bloquear vínculo documental y exigir revisión visible. Auditoría anterior/resultante en las estructuras existentes.
5. PUE no acredita pago. ImpuestosDR son fuente de detalle REP; ImpuestosP/Totales sólo concilian. FechaPago determina el periodo del auxiliar financiero.
6. Estado SAT desconocido, cancelaciones, sustituciones, falta de evidencia y reglas no verificadas generan revisión, nunca certeza fiscal artificial.
7. Servicios revalidan permisos efectivos y asignación explícita. SwingWorker y protección contra respuestas obsoletas.

## Persistencia y riesgos detectados

- Tablas de distribuciones existentes carecen revisión e historial especializado y límites agregados; se reutilizan con revisión documental y snapshots en bitacora_evento/bitacora_cambio. No requieren DDL adicional.
- `v_saldo_obligacion` representa toda la historia, no un cierre histórico. Se requieren cortes por fechas.
- REP referencia UUID sin FK a factura; comprobar disponibilidad del mismo titular.
- Vinculación manual/REP puede duplicar evidencia si no se concilia explícitamente.
- `efecto_fiscal_periodo` no contiene moneda ni fuente suficiente; no usar para materializar resultados derivados sin invalidación.
- No se ha modificado esquema, rutina ni base operativa.

## Verificación y errores

- Comando obligatorio iniciado: `mvn --batch-mode --no-transfer-progress -f app/pom.xml verify`.
- Primer intento bloqueado por caché Maven fuera del sandbox; reejecutado con permisos de entorno.
- Docker no está instalado (`docker: command not found`): integración Testcontainers no ejecutable actualmente.
- Diseñador Eclipse WindowBuilder: pendiente; no se declara compatibilidad comprobada.

## Próxima actividad

Completar diagnóstico CI y normativo; implementar RF-021 reutilizando tablas existentes y documentar migración, pruebas y limitaciones reales.

## Continuidad tras interrupción del servidor

Se recuperó el árbol de trabajo sin sobrescribir cambios. El intento de publicación no alcanzó a ejecutarse antes de la interrupción. Tras retomarlo, HTTPS rechazó el push por falta de credenciales. Se utilizará el conector GitHub para publicar si mantiene acceso.

## Avance técnico comprobado

- Entidades JPA para distribuciones, obligaciones, movimientos, aplicaciones y ajustes sobre tablas existentes; `validate` conservado.
- Servicio RF-021 con lectura actual bloqueante de revisión para evitar el snapshot obsoleto de MySQL REPEATABLE READ; orden canónico evita historial de no-ops.
- Editor de conceptos/tratamientos integrado con RF-020. Se conserva SplitPane y sus acciones.
- Proyecciones RF-022 por nivel original y conciliación de conceptos/globales/totales.
- Auxiliar REP con controles acumulados y motor puro de IVA; manuales y consolidación adicional todavía en revisión.
- Servicios de evidencia y saldos históricos; revisión detectó cruces de titular en REP/ajustes compartidos y obligación heredada incompatible. Correcciones en curso con pruebas específicas pendientes.
- PanelResumenFiscal con filtros explícitos, pestañas, paginación, detalle y CSV; pendiente prueba integral.
- Pruebas unitarias anteriores al último lote: 90 satisfactorias. Nuevas pruebas detectaron regex de moneda permisiva, ya corregida. Nueva ejecución en curso.
- Maven verify local falla por Docker ausente. Pruebas Swing requieren `java.awt.headless=true` porque DISPLAY apunta a X11 inaccesible; se configuró Surefire sin omitir pruebas.

Próxima actividad: validar compilación y navegación, completar agregación IVA/manuales, ejecutar escenario MySQL en CI, corregir incidencias y documentar límites reales.

## Validación remota y correcciones de integración

- PR borrador: https://github.com/Pablo-Gomez-Perez/visor-cfdi-xml/pull/21. No se ha fusionado.
- CI `44671133327`: Maven verify satisfactorio para RF-021/022 y regresión RF-020.
- CI `44671650580` sobre `58f3a84`: Maven verify satisfactorio para integración inicial completa, incluyendo escenario MySQL de dos meses, pagos 80 + 80, saldos, permisos, monedas y EXPLAIN sintético.
- La revisión posterior detectó y corrigió: residuos tributarios negativos por HALF_UP; ausencia de ObjetoImp en CFDI 3.3; reutilización de detalle anterior tras fallo de consulta Swing; trazabilidad por emisión que omitía facturas anteriores pagadas; firma de retención DR/P incompatible.
- El resumen RF-020 ahora consume la misma precedencia de distribuciones, preservando su universo y conteos documentales.
- Nuevas carátulas separan ingresos/egresos por emisión y movimientos financieros, incluyendo facturas de meses anteriores. IVA documental, realizado, potencial, tratamiento y pendientes se mantienen separados.
- Se añadieron regresiones para los fallos encontrados. Deben volver a pasar CI antes de considerar consolidada esta revisión.
- Publicación mediante conector GitHub: conserva commits atómicos y el mismo contenido. Sus identificadores difieren de los locales por metadatos del commit; se reconciliará el historial sin force push.

## Revisión 0729746 / a48e8b9

- Commit local `0729746`, remoto equivalente `a48e8b9`: carátulas por origen
  temporal, RF-020 con distribución detallada, controles REP/cancelación,
  conciliación de retenciones, vínculo manual previo con DR posterior y
  regresiones sobre residuos y CFDI 3.3. No se cambió el esquema.
- `mvn --batch-mode --no-transfer-progress -f app/pom.xml test`: 112 pruebas,
  cero fallos. `git diff --check`: sin problemas de espacios.
- CI `44673872992` ejecutó `Maven verify` con MySQL 8 y concluyó
  `BUILD SUCCESS`: 112 unitarias y 16 pruebas de integración;
  `MvpFiscalServiceIT` terminó sin fallas y ejecutó EXPLAIN sintético.
- El escenario nuevo verifica enlace manual/REP sin duplicar aplicación,
  aislamiento por titular y factura de ejercicio anterior pagada en 2026.
- Documentación obligatoria se actualiza sin declarar validación visual de
  WindowBuilder. Pendiente revisar consistencia final tras CI.

Próxima actividad: resolver el resultado de CI, cerrar brechas funcionales
comprobadas, documentar límites, reconciliar historia Git local y remota y
actualizar PR #21. No fusionar ni marcar lista con WindowBuilder pendiente.

## Extensión tributaria d5361ce / 71133c1

- `ImpuestosAgrupadosService` cruza distribuciones por concepto y tratamiento
  en agregados SQL. Conserva remanente pendiente y residuos a seis decimales.
- Carátula IEPS separa CFDI original, IEPS DR de REP y ajustes administrativos;
  no convierte ninguna de esas fuentes en IEPS definitivo.
- Las pestañas y el rastro del DR se integraron al mismo `PanelResumenFiscal`.
- `mvn --batch-mode --no-transfer-progress -f app/pom.xml test`: 113
  unitarias satisfactorias. CI MySQL para `71133c1` aún pendiente.
- Fuentes oficiales LIEPS y LISR verificadas; límites documentados.

Próxima actividad: esperar `Maven verify` de `71133c1`, corregir cualquier
falla SQL, confirmar integración y actualizar la PR. Validación real de
WindowBuilder continúa pendiente en este entorno.
