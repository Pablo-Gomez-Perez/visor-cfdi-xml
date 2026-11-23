# Matriz fiscal mínima por régimen — plan de algoritmos del MVP

> **Estado:** especificación, NO motor fiscal implementado ni determinación oficial.
> Aprobación de clasificación y reglas pendiente de pruebas con contador y ejercicios.
> Debe integrarse en las pestañas **Periodo, Ingresos, Egresos, IVA, IEPS,
> ISR y retenciones** de `PanelResumenFiscal`. Prohibido crear otro JFrame,
> tablero o motor fiscal paralelo.

## Problema que resuelve

La carátula que requiere el usuario es una **síntesis mensual operativa**:
ingresos del periodo, egresos, pagos, NC, bases de IVA, retenciones, importes
pendientes e incidencias, sin recorrer 20 pestañas. Las bases acumulables,
deducibles y acreditables **dependen de impuesto, régimen, actividad y ejercicio**;
`SUM(CFDI I) - SUM(CFDI E)` no es por sí sola una base gravable legal.

Las clasificaciones documentales de RF-020/021 y los pagos/ajustes de RF-023/024
son fuentes; los cálculos por régimen consumen su trazabilidad, nunca modifican
XML, saldos, evidencia ni clasificadores.

## Familias de algoritmos: orden de implementación

| Prioridad | Claves SAT | Persona | Familia ISR | Fundamento de partida | Tratamiento |
|---|---|---|---|---|---|
| P0 | **612** | Física | Actividad empresarial / servicios profesionales | LISR Título IV, Cap. II, Sección I, particularmente arts. 100–110, 106 | ISR provisional con entradas/salidas y requisitos, retenciones; IVA con reglas propias |
| P0 | **626** | Física | RESICO PF | LISR arts. 113-E a 113-J; RMF ejercicio | ISR mensual bajo reglas de ingreso cobrado y retenciones; **no usar deducciones de 612 para base mensual RESICO** |
| P0 | **601** | Moral | Régimen general PM | LISR Título II, arts. 9, 14 y siguientes | ISR provisional conforme a coeficiente y datos fiscales previos; **no calcular como ingresos menos erogaciones del mes** |
| P0 | **626** | Moral | RESICO PM | LISR Título VII, Cap. XII, arts. 206–215 | Motor distinto del 626 PF, reconocimiento y deducciones propios; IVA independiente |
| P1 | **606** | Física | Arrendamiento | LISR arts. 114–118, especialmente 116; LIVA según inmueble y operación | ISR provisional por arrendamiento, alternativa de deducción comprobada u opción legal si procede, IVA cuando corresponda |
| P2 | **625** | Física | Plataformas tecnológicas | LISR arts. 113-A a 113-D, LIVA correspondiente y reglas | Debe registrar opción de retención definitiva o provisional y evidencias de plataforma; no sumar cobros de la plataforma dos veces |
| P2 | **622** | Física o Moral | Sector primario AGAPES | LISR arts. 74 y correlativos, disposiciones y transitorios aplicables | Motor especial separado por tipo de persona, exenciones y condiciones verificadas |
| P3 | **621** | Física | RIF histórico/transitorio | disposiciones y transitorios de permanencia | **Sólo periodos elegibles**; no permitir altas nuevas por omisión de reglas transitorias |
| P3 | **603** | Moral | Sin fines de lucro | LISR Título III | Auxiliares/retenciones primero; no presumir pago ISR general, ingresos específicos pueden tener obligaciones |
| P3 | **620, 623, 624** | Moral u otros | Cooperativas, sociedades coordinadas, grupos | LISR capítulos especiales | Postergar cálculos específicos |
| P3 | **605, 607, 608, 610, 611, 614, 615, 616, 609** y restantes | PF/PM según catálogo | Salarios, adquisiciones, intereses, dividendos, otros o regímenes especiales/históricos | Reglas específicas por hecho generador | Auxiliares documentales primero; no prometer motor mensual general |

**P0 significa primera entrega calculadora por régimen, no que el código actual
ya la implemente.** Para versiones vigentes y ejercicios históricos contrastar
el catálogo `c_RegimenFiscal`, leyes y RMF aplicables. El mismo código
`626` exige **dos motores distintos** para personas físicas y morales.

### Reutilización transversal (no es un nuevo régimen)

No implementar un motor IVA diferente por cada régimen ISR. La capa IVA
consume operaciones y opciones de la actividad/requisitos jurídicos y se
comparte con controles de causación/cobro, acreditamiento, exención, retención,
proporción, devoluciones y fecha. IEPS requiere motor autónomo por actividad,
producto, tasa/cuota y acreditamiento particular. Retenciones son módulo
transversal con tipo de contraparte y concepto tributario.

## Insumos mínimos antes de una determinación (no asumir cero)

- Tipo de persona según RFC, `contribuyente_regimen` con vigencia de fechas,
  regímenes **simultáneos**, obligaciones por impuesto y periodos.
- Regla aplicable al ejercicio; la etiqueta de régimen de un CFDI es
  metadato documental, no la totalidad de la situación fiscal del titular.
- Clasificación por actividad/origen de ingreso cuando coexistan regímenes;
  clasificación de gastos, deducciones e inversiones y soporte.
- Realización de pagos/cobros y devoluciones con evidencia; múltiples monedas;
  estado SAT conocido y vigencia histórica no presumida.
- ISR: retenciones, pagos provisionales previos y acumulados del ejercicio;
  coeficiente de utilidad y pérdidas fiscales en PM general, activos y
  requisitos de deducción donde corresponda; tarifas y reglas por ejercicio.
- IVA: impuestos originales, cobros/pagos, IVA retenido enterado cuando proceda,
  tratamiento de acreditamiento y proporciones; valores desconocidos distintos
  de cero.
- IEPS: clasificación de productos y actividades, tasas/cuotas, requisitos
  específicos, enteros y acreditamientos. Nunca usar el algoritmo IVA.

## Contrato funcional propuesto para la carátula existente

**En la pestaña Periodo** de `PanelResumenFiscal` (no crear vistas), mostrar:
`Ingresos documentados`, `Ingresos cobrados verificables`,
`NC relacionadas (propuestas/aplicadas)`, `Erogaciones documentadas`,
`Erogaciones pagadas`, `IVA trasladado/cobrado`,
`IVA pagado/acreditable/no acreditable/pendiente`,
`Retenciones`, `IEPS original` y `Incidencias`, desglosados por
moneda y fechas. Permitir consultar origen de cada cifra.

Separar tres estados de presentación:
1. **Documentado:** se conoce valor XML, no implica ingreso fiscal.
2. **Confirmado según controles definidos:** evidencia suficiente para el
   auxiliar, sin certificar automáticamente requisitos fiscales no codificados.
3. **Pendiente / incompleto:** sin evidencia, régimen/actividad no establecido,
   SAT desconocido o reglas fiscales no verificadas.

Para cada fórmula de ISR o impuesto elegido deben existir tests con cifras
esperadas obtenidas independientemente, Javadoc, norma/fecha aplicable y
celdas de entrada para **datos que no están en XML**. No introducir ceros
fiscales por ausencia de datos. No mezclar monedas, notas canceladas y
movimientos no conciliados. La modificación de tratamiento requiere auditoría.

## Secuencia técnica recomendada (PR futuras pequeñas)

1. **PERFILES:** leer regímenes vigentes de `contribuyente_regimen`, permitir
   confirmar obligación/actividad aplicable al ejercicio con datos existentes;
   bloquear cálculo final si hay ambigüedad.
2. **CARÁTULA OPERATIVA:** reunir en `Periodo` los agregados ya calculados
   por los servicios actuales, mostrando períodos, fuente y estado. No
   crear un nuevo motor.
3. **ISR PF 612 y 626:** algoritmos distintos, tests mensuales y anuales.
4. **ISR PM 601 y 626:** coeficiente/datos previos y reglas de cada capítulo,
   tests con acumulados y retenciones.
5. **ARRENDAMIENTO 606** y posteriormente 625/622 según demanda real.
6. **INTEGRACIÓN:** comparaciones con papeles de trabajo validados
   por contador, sin reproducir automáticamente un formulario SAT
   cuya estructura pueda cambiar.

## Fuentes primarias y vigencia

- [SAT — listado de regímenes](https://www.cloudb.sat.gob.mx/datos_fiscales/regimen)
  y `c_RegimenFiscal` del Anexo 20 (verificar catálogos vigentes por ejercicio).
- [Ley del ISR (Cámara de Diputados)](https://www.diputados.gob.mx/LeyesBiblio/pdf/LISR.pdf),
  artículos 9, 14, 74, 100–110, 106, 113-A–113-J, 114–118 y 206–215
  según el caso y transitorios.
- [LIVA](https://www.diputados.gob.mx/LeyesBiblio/pdf/LIVA.pdf), artículos
  1-B, 5, 5-D y 7, y [Reglamento LIVA](https://www.diputados.gob.mx/LeyesBiblio/regley/Reg_LIVA_250914.pdf).
- [LIEPS](https://www.diputados.gob.mx/LeyesBiblio/pdf/LIEPS.pdf),
  arts. 1, 2, 4, 5 y capítulos específicos.
- [SAT RMF 2026 y modificaciones](https://www.sat.gob.mx/minisitio/NormatividadRMFyRGCE/).
- [SAT art. 113-E](https://wwwmatnp.sat.gob.mx/articulo/58780/articulo-113-e)
  y [SAT art. 113-J](https://wwwmat.sat.gob.mx/articulo/59511/articulo-113-j).

**No utilizar esta matriz como sustituto de investigación normativa**:
cada PR calculadora verificará las normas y vigencias por ejercicio, exenciones,
opciones, transitorios y requisitos de la actividad antes de implementar
el algoritmo. La selección de un régimen no equivale a determinación válida.
