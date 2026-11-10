# Visor CFDI XML — Instrucciones de desarrollo

Consultar `docs/ARQUITECTURA.md`, `docs/RF-002.md` y el esquema canónico `app/database/schema/visor_cfdi_mysql8_schema_v1.sql` antes de modificar la persistencia.

## Entorno

- Java SE 17, Maven, MySQL 8, Swing, FlatLaf.
- **Requisito obligatorio:** toda ventana/panel Swing debe ser editable mediante Eclipse WindowBuilder, con constructor público sin argumentos y GroupLayout explícito como layout predeterminado. No inicializar JPA, JDBC, red ni archivos obligatorios en constructores de vistas.
- Reutilizar el patrón de `Kath_ERP/src/main/java/.../app/view/Fr_principal.java` y de los paneles de catálogo `PanelClientes` y `PanelTipoCliente`.
- **Una sola ventana principal** con `JMenuBar`, barra de accesos rápidos y `JPanel` central con `CardLayout`. NO abrir `JFrame` para catálogos, listas, dashboards ni reportes.
- Cada módulo de listado debe tener su propia clase `Panel<Modulo> extends JPanel` con `GroupLayout`, barra de acciones, contenido central y herramientas de filtro abajo.
- Usar diálogos de detalle independientes (`Fr_Datos<Entidad> extends JDialog`) solo para alta, edición o inspección de un registro, reutilizando el mismo formulario para crear/editar.
- Priorizar escritorios de 900×700 o menores: ventana principal inicial aproximada 860×600, anchura mínima no superior a 740; evitar diseños que requieran múltiples monitores o ancho fijo mayor que la pantalla.
- El panel principal se añade al `CardLayout` y se activa desde `JMenuItem` o acceso rápido. El botón de una función pendiente debe quedar deshabilitado, no fingir que funciona.
- Las ventanas de inicio de sesión y configuración inicial pueden ser independientes, porque son flujos previos a la ventana principal.
- La especificación completa y obligatoria está en `docs/UI-SWING-WINDOWBUILDER.md`.
- Javadoc en clases, métodos públicos y contratos relevantes.

## Acceso a datos

- Hibernate/JPA para CRUD y transacciones habituales.
- JDBC + procedimientos almacenados para operaciones de alto volumen, previa justificación por tamaño o rendimiento.
- Los límites de la transacción son responsabilidad de Java. Cuando una operación combina ORM y procedimientos, usar la conexión de `Session.doWork` bajo la misma transacción JPA. No abrir conexiones independientes dentro de una operación indivisible.
- Si un procedimiento es llamado dentro de una transacción Java, no debe confirmar ni revertir esa transacción por su cuenta.
- No permitir que Hibernate modifique el esquema: `hibernate.hbm2ddl.auto=validate`.
- Las vistas Swing no deben contener SQL ni `EntityManager`; se comunica con servicios y DTO.
- Mantener `BigDecimal` para montos fiscales; evitar `float/double` en cálculos monetarios.

## Seguridad e integridad

- No almacenar credenciales MySQL en repositorio, archivos de configuración ni logs.
- Contraseñas de usuarios: hash con sal, nunca texto plano.
- Los roles definidos en el cliente no defienden contra usuarios que poseen acceso de escritura directo a MySQL. Para entornos no confiables diseñar capa de autorización server-side.
- Una factura UUID se almacena una vez; las clasificaciones y los pagos tienen tablas separadas.
- Guardar los archivos XML originales fuera de MySQL y asociarlos con metadatos de integridad.

## Verificación

- Usar `mvn --batch-mode --no-transfer-progress -f app/pom.xml verify` para unitarias e integración (Docker requerido).
- `*Test` para unitarias y `*IT` para integración aislada con MySQL 8 y SQL original del repositorio.
- No ejecutar tests de integración contra una base real de usuario, ni modificar tablas en producción.
- Git en ramas `feat/*`, PR revisable contra `main`. No fusionar automáticamente.
