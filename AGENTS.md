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

- La contraseña MySQL se almacena recuperable **solo cifrada** mediante `CredencialesMySqlService`: AES-256-GCM en `database.credentials` con clave aleatoria separada `connection.key`, dentro del perfil del usuario; nunca en `config.properties`, Git ni logs. Este mecanismo no es una bóveda de seguridad y no se deben copiar juntos los dos ficheros.
- Mantener conexión automática tras la primera configuración y el login de aplicación separado del login MySQL. No introducir prompts MySQL por sesión.
- Para `caching_sha2_password`, solo localhost/127.0.0.1/::1 permite `allowPublicKeyRetrieval=true` con TLS deshabilitado; en remoto exigir `VERIFY_IDENTITY` y prohibir recuperación RSA no verificada.
- Contraseñas de usuarios: hash con sal, nunca texto plano.
- Los roles definidos en el cliente no defienden contra usuarios que poseen acceso de escritura directo a MySQL. Para entornos no confiables diseñar capa de autorización server-side.
- Una factura UUID se almacena una vez; las clasificaciones y los pagos tienen tablas separadas.
- Guardar los archivos XML originales fuera de MySQL y asociarlos con metadatos de integridad.

## Verificación

- Usar `mvn --batch-mode --no-transfer-progress -f app/pom.xml verify` para unitarias e integración (Docker requerido).
- `*Test` para unitarias y `*IT` para integración aislada con MySQL 8 y SQL original del repositorio.
- No ejecutar tests de integración contra una base real de usuario, ni modificar tablas en producción.
- Git en ramas `feat/*`, PR revisable contra `main`. No fusionar automáticamente.

## RF-003 — RBAC y catálogo editable

- Consultar `docs/RF-003.md` y los mapeos `RolEntity`/`PermisoEntity` antes de añadir operaciones protegidas.
- Los privilegios de aplicación usan la pareja `permiso.recurso` + `permiso.accion` (por ejemplo `USUARIOS.MODIFICAR`). Los concede el administrador con `PanelRoles` + `Fr_PermisosRol` y se conservan en `rol_permiso`.
- Añadir privilegios únicamente cuando un módulo implemente y verifique esa acción desde el servicio. Los botones Swing no son la barrera de seguridad.
- Usar `AutorizacionService.exigir(em, usuarioId, permiso)` **dentro de la transacción** para escrituras y `AutorizacionService.permisosDe` para obtener la unión de roles activos.
- Nunca quitar `ROLES.CONFIGURAR_PERMISOS` del rol ADMIN; esta regla protege la capacidad de recuperación. El resto de sus privilegios son configurables y los demás roles empiezan sin permisos.
- `USUARIOS.ASIGNAR_ADMIN` se exige por separado al otorgar el rol privilegiado ADMIN.
- Un rol personalizado es editable pero no puede borrarse si hay usuarios asociados. La clave del rol no cambia tras el alta.
- Para el cliente Swing, recuperar permisos vigentes en `SwingWorker` al entrar a cada catálogo; la pantalla debe mantener GroupLayout y WindowBuilder.

## RF-004 — Contribuyentes y obligatoriedad de actualizar RBAC

- Cada nueva feat/módulo debe auditar la matriz RF-003: definir claves estables
  en `Permisos` únicamente para operaciones implementadas, insertar de forma
  idempotente en `permiso`, verificar `AutorizacionService.exigir` en el
  servicio y añadir pruebas positivas/negativas contra MySQL real.
- **Bootstrap incremental de permisos:** dar a ADMIN únicamente los permisos
  realmente nuevos. No restaurar automáticamente permisos revocados tras
  reiniciar. Proteger siempre `ROLES.CONFIGURAR_PERMISOS`.
- Diferenciar `contribuyente` de `tercero`: no crear contribuyentes propios
  por importar automáticamente un emisor/receptor ajeno.
- RFC es identidad fiscal de negocio; se valida solo estructura y no se
  presenta como verificado ante SAT. En RF-004 no se permite cambiar RFC
  después del alta; nombre, CP y correo sí.
- Acceso a contribuyentes requiere **permiso de acción** y **asignación
  explícita en usuario_contribuyente**; el privilegio excepcional
  `CONTRIBUYENTES.ASIGNAR_USUARIOS` administra alcance global.
- No eliminar físicamente contribuyentes con historia fiscal; baja lógica.
- No solapar periodos para una misma clave de régimen y contribuyente.
  No asumir que la clave de tres dígitos está vigente o existe en SAT
  sin cargar catálogos fiscales oficiales.
- UI RF-004 usa `PanelContribuyentes` dentro de CardLayout y un solo
  `Fr_DatosContribuyente` con pestañas de datos, regímenes y usuarios.
- Documentación: `docs/RF-004.md` y `docs/UI-SWING-WINDOWBUILDER.md`.
