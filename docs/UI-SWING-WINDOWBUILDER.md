# Contrato de UI — Swing, FlatLaf y Eclipse WindowBuilder

## Objetivo y referencia

La aplicación está dirigida a profesionales de la contaduría que normalmente
trabajan con una sola pantalla, con áreas útiles reducidas (por ejemplo
900×700 píxeles o menos). Evitar la proliferación de ventanas y conservar
el patrón constructivo de
`Kath_ERP/src/main/java/com/kathsoft/kathpos/app/view/Fr_principal.java`:
`JMenuBar`, accesos rápidos y contenido central gestionado por `CardLayout`.

Para cada módulo de catálogo se toma como referencia el diseño de
`app/view/clientes/PanelClientes.java` y
`app/view/clientes/PanelTipoCliente.java` del repositorio Kath ERP.

**Esta guía es obligatoria para RF-002 y todos los nuevos módulos.**

## Jerarquía obligatoria

```text
Fr_Principal extends JFrame
├── JMenuBar
│   ├── Inicio
│   ├── Administración
│   │   ├── Administrar usuarios → CardLayout ("panelUsuarios")
│   │   └── Roles y permisos → CardLayout ("panelRoles")
│   ├── CFDI
│   │   ├── Administrar XML (pendiente)
│   │   └── Cargar XML (pendiente)
│   └── Sesión
├── panelSuperiorBotones: JPanel con accesos rápidos
│   ├── Inicio → CardLayout ("panelInicio")
│   ├── Administrar XML (deshabilitado por ahora)
│   └── Cargar XML (deshabilitado por ahora)
├── panelPrincipalContenedor: JPanel con CardLayout
│   ├── PanelInicio extends JPanel
│   ├── PanelUsuarios extends JPanel
│       ├── panelEtiquetaUsuarios
│       └── panelCentralContenedor
│           ├── panelSuperiorBotones (acciones)
│           ├── JScrollPane + JTable (contenido)
│           └── panelInferiorBusqueda (búsqueda y filtros)
│   └── PanelRoles extends JPanel
│       ├── barra de acciones: crear, editar, configurar privilegios, eliminar
│       ├── JTable de roles
│       └── herramientas de búsqueda
└── panelEstado: JPanel de sesión
```

Los futuros módulos `PanelContribuyentes`, `PanelCFDI`,
`PanelPagos`, `PanelConciliacion`, etc., se añadirán como tarjetas
y archivos Java separados, sin JFrames de catálogo.

## Reglas obligatorias

1. **Un solo JFrame principal** en sesión. Los catálogos, tablas,
   dashboards y reportes forman tarjetas `JPanel` dentro de `CardLayout`.
2. Únicamente los **datos detallados de un registro** se presentan en un
   diálogo independiente. Alta y modificación deben usar la misma clase
   `Fr_Datos<Entidad> extends JDialog`, con modo crear/editar. Diálogos
   breves de confirmación y contraseña (`JOptionPane`) están permitidos.
3. **GroupLayout es el layout predeterminado** de cada módulo y formulario.
   `CardLayout` se reserva para navegación y `FlowLayout` para barras
   de botones sencillas, como en Kath ERP.
4. Los componentes que deban editarse en WindowBuilder se declaran como
   **campos**, se crean explícitamente y se agrupan en un
   `javax.swing.GroupLayout`. Evitar interfaces generadas mediante
   bucles, reflexión, factories de componentes o builders opacos.
5. Todo JFrame, JDialog y JPanel de un módulo nuevo debe disponer de
   **constructor público sin parámetros** que no consulte BD, red,
   servicios externos ni archivos de usuario. La inyección de servicios
   ocurre en un constructor adicional o después de construir la vista.
6. **Swing no ejecuta consultas en el Event Dispatch Thread**; para
   operaciones de persistencia se utiliza `SwingWorker` o mecanismo
   equivalente. No pasar `EntityManager` a controles de la interfaz.
7. Usar **FlatLaf** desde el punto de entrada `App.main`, no
   aplicarlo globalmente durante la construcción de formularios que
   renderiza WindowBuilder. No reemplazar `JTable`, `JMenu`, etc.,
   por controles incompatibles con el diseñador.
8. La ventana principal debe abrirse en aproximadamente **860×600 px**,
   poder contraerse hasta **740×490 px** y funcionar a 900×700 con
   espacio para menú y barras del sistema. El `JScrollPane` y los
   componentes flexibles ocupan el espacio restante. La `JTable`
   puede tener barra horizontal si hay muchas columnas.
9. Se evita fijar anchos elevados o abrir múltiples ventanas modales en
   cascada. La navegación al catálogo no cambia el tamaño de la ventana.
10. Accesos rápidos y menús de módulos aún no implementados deben estar
    **deshabilitados con mensajes/tooltip honestos**. No simular resultados.
11. Preservar las reglas de validación, autorización, auditoría y
    transacciones de los servicios. La homologación UI no modifica el
    esquema SQL ni realiza operaciones de base de datos.
12. Cerrar sesión retorna al login sin mantener otro JFrame principal
    abierto. Los diálogos de detalle deben ser modales y cerrarse después
    de guardar exitosamente.
13. El botón de modificar respeta la selección del JTable incluso cuando
    hay ordenamiento/filtros: convertir índice de vista a índice de modelo.
14. El listado no debe filtrar en SQL sin necesidad; filtrado local para
    catálogos pequeños. Para tablas masivas de CFDI se implementarán
    filtros en base de datos y paginación, evitando cargar todo en memoria.

## Regla de construcción de cada Panel<Modulo>

- `panelEtiqueta<Modulo>`: etiqueta de catálogo (en Kath ERP, azul marino).
- `panelCentralContenedor`: contenido flex y GroupLayout.
- `panelSuperiorBotones`: botones para crear, editar, cambiar estados,
  exportar si está implementado.
- `JScrollPane` para `JTable` o componente principal.
- `panelInferiorBusqueda`: campos, filtros y botones de consulta.
- Método `recargar()` para poblar la vista al activarla desde un menú.
- Ninguna operación automática de DB en el constructor vacío.

## Checklist de revisión por formulario

- [ ] Se abre desde **Open With → WindowBuilder Editor → Design** en Eclipse.
- [ ] Componentes y grupos seleccionables visualmente; no hay errores de parser.
- [ ] El constructor vacío renderiza sin MySQL, Hibernate o servicios.
- [ ] Se muestra en la tarjeta central si es un catálogo; no abre un JFrame.
- [ ] Alta y edición comparten un diálogo de detalle modal.
- [ ] Funciona en 900×700 y se mantiene usable alrededor de 740×490.
- [ ] Los botones, formularios y JTable no se recortan.
- [ ] La UI permanece responsiva durante consultas y escrituras.
- [ ] Se comprueba la selección bajo sort/filtro y el mensaje de error.
- [ ] Los permisos se validan en el servicio, no solo en los botones.
- [ ] Hay pruebas automatizadas de lógica y prueba manual del diseñador.

## Estado de la homologación

RF-002 se muestra dentro de `PanelUsuarios`, se navega desde
`Fr_Principal` y utiliza `Fr_DatosUsuario` para crear/editar
un registro. Los módulos de CFDI siguen pendientes de implementación.

## RF-003 — Matriz de privilegios

La tarjeta `PanelRoles` sigue el patrón de Kath ERP sin ventanas
de listado adicionales. El `Fr_DatosRol` modal permite crear y editar
un rol. El `Fr_PermisosRol` modal contiene una `JTable` con primera
columna `Boolean.class`: Swing presenta sus celdas con casillas
`JCheckBox`. Las columnas restantes explican módulo, acción y
descripción. Los checkboxes obligatorios de ADMIN no son editables.

Las operaciones se cargan y guardan en `SwingWorker`, y los servicios
verifican privilegios en MySQL antes de ejecutar cambios. Los catálogos
de usuarios y roles muestran controles según los permisos actualizados.
