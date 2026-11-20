# MVP etapa 1 — Seguimiento de ingeniería

## Base comprobada

- Base: `d4263d86348c00e6896f0bdabfe48bae08ea395f`, `main` limpio al comenzar.
- Rama: `feat/mvp-etapa-1-clasificacion-fiscal`.
- PR #20 integrada; conserva SplitPane, edición reutilizable, atajos, historial y resumen RF-020.
- `git fetch origin` falló por autenticación SSH. La consulta HTTPS de `main` confirmó el mismo commit.
- No había PR abiertas al inspeccionar GitHub.
- CI heredado de RF-020: ejecución `44489981223`, fallida. Diagnóstico pendiente.
- Referencia Excel disponible: se inspeccionaron únicamente estructura y familias de fórmulas. Hojas EGRESOS, REP, Hoja1 y Hoja2. No se incorporan datos privados.

## Matriz de implementación

| Fase | Estado | Resultado verificable |
|---|---|---|
| 0. Inspección | En curso | Base, capas, esquema, rutinas y documentación revisados |
| 1. RF-021 | Pendiente | Distribuciones y tratamientos, revisión, historial e interfaz |
| 2. RF-022 | Pendiente | Impuestos originales, agrupaciones y conciliación |
| 3. RF-023 | Pendiente | Evidencia y auxiliar de IVA por fecha de pago |
| 4. RF-024 | Pendiente | Terceros, obligaciones, aplicaciones, ajustes y saldos históricos |
| 5. Carátulas | Pendiente | Resumen fiscal, trazabilidad y CSV |
| 6–7. Integración y pruebas | Pendiente | Unitarias, MySQL 8 y escenario de dos meses |
| 8. Documentación | En curso | Este documento registra hechos, no funcionalidades supuestas |
| 9. Publicación | Pendiente | Commits atómicos y PR contra main; sin fusionar |

## Decisiones e invariantes

1. Reutilizar `clasificacion_concepto`, `clasificacion_impuesto_concepto`, `tratamiento_impuesto`, `obligacion`, `movimiento_pago`, `aplicacion_pago` y `ajuste_obligacion`.
2. Mantener originales independientes de distribuciones, evidencia y cálculos. `BigDecimal`/`DECIMAL`; no mezclar monedas.
3. Distribución detallada sustituye la categoría general para el concepto completo, incluido su remanente pendiente. Sin detalle se hereda la categoría general. Nunca sumar ambos.
4. Guardar distribuciones en una transacción, bloquear vínculo documental y exigir revisión visible. Auditoría anterior/resultante en las estructuras existentes.
5. PUE no acredita pago. ImpuestosDR son fuente de detalle REP; ImpuestosP/Totales sólo concilian. FechaPago determina el periodo del auxiliar financiero.
6. Estado SAT desconocido, cancelaciones, sustituciones, falta de evidencia y reglas no verificadas generan revisión, nunca certeza fiscal artificial.
7. Servicios revalidan permisos efectivos y asignación explícita. SwingWorker y protección contra respuestas obsoletas.

## Persistencia y riesgos detectados

- Tablas de distribuciones existentes carecen revisión e historial especializado y límites agregados; requieren servicio atómico y migración compatible.
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
