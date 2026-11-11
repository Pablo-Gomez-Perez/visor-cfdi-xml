# Visor CFDI XML

Aplicación de escritorio multiplataforma para la gestión documental, fiscal y financiera de CFDI mexicanos.

## Tecnologías

Java SE 17, Maven, Swing, Eclipse WindowBuilder, GroupLayout, FlatLaf, Hibernate ORM/JPA, JDBC especializado y MySQL 8.

**Referencia principal del esquema:** `app/database/schema/visor_cfdi_mysql8_schema_v1.sql`. El código Java nunca crea ni modifica automáticamente las tablas.

## Funcionalidades implementadas

### RF-001 — Configuración inicial
- Configuración de servidor, puerto, esquema y usuario MySQL.
- Prueba de conexión JDBC sin bloquear Swing.
- Selección del directorio local de documentos XML.
- Persistencia local de parámetros no secretos en `~/.visor-cfdi-xml/config.properties`.
- Credenciales MySQL **cifradas en el perfil local** mediante AES-256-GCM, con clave aleatoria de instalación en un archivo separado; no se vuelven a pedir en cada arranque.
- Conexión automática a MySQL desde la pantalla de inicio; el usuario solo ingresa la contraseña de la **aplicación**.
- Política JDBC corregida para MySQL 8: `allowPublicKeyRetrieval=true` únicamente en loopback (localhost/127.0.0.1/::1); conexiones remotas con `sslMode=VERIFY_IDENTITY` y recuperación RSA deshabilitada.

### RF-002 — Administración de usuarios
- Arranque con Hibernate/JPA y conexión MySQL (pool HikariCP).
- Alta inicial del primer administrador, solo si no existen cuentas.
- Roles iniciales idempotentes `ADMIN` y `CONSULTA`.
- Autenticación con contraseña de la **aplicación**, independiente de MySQL.
- Administración por ADMIN: listar, crear, editar nombre completo, **login** y correo, activar/desactivar, cambiar rol básico y restablecer contraseña.
- Protección contra desactivar o degradar al último administrador activo.
- Registro de eventos administrativos en `bitacora_evento`.
- Contraseñas con PBKDF2-HMAC-SHA256, sal individual y 600 000 iteraciones.
- Formularios Swing con componentes explícitos, constructor vacío y GroupLayout para WindowBuilder.
- Operaciones JDBC/JPA fuera del Event Dispatch Thread.
- Transacciones manejadas en Java (`commit`/`rollback`).

### RF-003 — Roles y permisos
- Catálogo configurable de roles en `PanelRoles`, integrado al `CardLayout` principal.
- Diálogo `Fr_DatosRol` compartido para crear y modificar roles.
- Diálogo `Fr_PermisosRol`: matriz de `JTable` con casillas Boolean/JCheckBox por privilegio.
- Persistencia de privilegios mediante Hibernate/JPA en `rol_permiso`, sin alterar el esquema.
- Autorización por acción evaluada en cada servicio contra permisos efectivos; no basta con pertenecer a ADMIN.
- Roles personalizados sin permisos por defecto; el administrador puede asignarlos desde la aplicación.
- ADMIN conserva de forma obligatoria el permiso de recuperación de roles; cambio de rol a ADMIN exige autorización adicional.
- Acciones de roles auditadas y transaccionales.

**Guía y criterios de aceptación:** [docs/RF-003.md](docs/RF-003.md).

### RF-004 — Administración de contribuyentes
- Alta y consulta de contribuyentes propios con RFC y razón social obligatorios; CP y correo opcionales.
- Modificación de nombre/CP/correo, baja lógica y reactivación, sin alterar UUID de documentos.
- Historial independiente de regímenes fiscales (clave y fechas de vigencia).
- Control de acceso por contribuyente mediante `usuario_contribuyente`, con matriz de usuarios autorizados.
- Seis acciones nuevas incorporadas a la matriz editable de Roles y permisos.
- `PanelContribuyentes` en el CardLayout central, acceso de menú y botón rápido.
- Un solo `Fr_DatosContribuyente` con pestañas de datos fiscales, regímenes y usuarios vinculados.

**Documentación:** [docs/RF-004.md](docs/RF-004.md).

## Preparación

1. Instale JDK 17, Maven 3.9+ y MySQL 8.
2. Cree el esquema utilizando `app/database/schema/visor_cfdi_mysql8_schema_v1.sql`; **no lo reejecute sobre una instalación con datos**.
3. Configure un usuario de MySQL con permisos adecuados sobre `visor_cfdi` y privilegios mínimos de aplicación.
4. Ejecute desde la carpeta `app`:

   ```bash
   mvn clean verify
   mvn exec:java
   ```

5. En la primera ejecución, registre el servidor MySQL, compruebe su conexión y elija la carpeta de XML.
6. En la primera configuración, introduzca la contraseña MySQL y pulse **Guardar configuración**. La aplicación verifica la conexión y la guarda cifrada. En los siguientes arranques se conecta automáticamente; Hibernate valida el esquema pero no lo modifica.
7. Registre el **primer administrador**. No existe cuenta administrativa predeterminada.
8. Inicie sesión usando el usuario y la contraseña de aplicación recién creados.
9. En la ventana principal, elija **Administración → Administrar usuarios** o **Administración → Roles y permisos** o **Administración → Administrar contribuyentes**. Los catálogos aparecen en el área central mediante CardLayout, sin abrir otro JFrame.
10. En **Roles y permisos**, cree un rol, selecciónelo y pulse **Configurar privilegios** para marcar o desmarcar cada permiso. Después, asigne el rol desde Administración de usuarios.

**Migración de instalaciones existentes:** si ya existe `config.properties` de RF-001, pero no se habían guardado credenciales cifradas, la aplicación solicitará la contraseña MySQL **una sola vez** mediante «Guardar y conectar». Solo se guarda después de conectar correctamente; en las siguientes ejecuciones pasa al inicio de sesión de la aplicación sin solicitar la clave de MySQL.

## Credenciales y diagnóstico de MySQL 8

El archivo original `~/.visor-cfdi-xml/config.properties` sigue guardando **solo host, puerto, esquema, usuario y directorio XML**. La contraseña MySQL se cifra en archivos independientes:

```text
~/.visor-cfdi-xml/
├── config.properties        # Parámetros no secretos RF-001
├── database.credentials     # Contraseña cifrada AES-256-GCM
└── connection.key           # Clave aleatoria AES (NO compartir)
```

La aplicación protege los archivos cifrados con permisos 0600 y la carpeta con 0700 cuando el sistema admite POSIX (Ubuntu/Linux/macOS con sistemas de archivos compatibles). En Windows se heredan los permisos del perfil de usuario; **no debe almacenarse esta carpeta en una ubicación compartida**. Nunca agregue estos ficheros a Git, copias de proyecto ni sincronizaciones públicas.

**Límite de seguridad:** este mecanismo reproduce el enfoque de Kath ERP, pero **no es un almacén seguro del sistema operativo**: quien pueda leer tanto `database.credentials` como `connection.key` puede recuperar la contraseña. Debe usarse una cuenta MySQL con privilegios mínimos, distinta del administrador `root`. Para producción multiusuario es preferible un servicio intermedio que administre el secreto y la autorización del servidor.

**Error «Public Key Retrieval is not allowed»:** MySQL 8 utiliza habitualmente `caching_sha2_password`. Una conexión TCP sin TLS puede necesitar recuperar la clave pública RSA del servidor después de un reinicio o una limpieza de la caché de autenticación. La aplicación ahora habilita `allowPublicKeyRetrieval=true` **solo en loopback**. Para conexiones externas conserva TLS con verificación de identidad: debe configurarse un certificado de servidor válido y una CA confiable. No se recomienda cambiar el usuario a `mysql_native_password` para ocultar este error.

**Si cambia la contraseña de MySQL**, la conexión automática fallará, pero se mantiene el archivo cifrado. En la ventana de conexión seleccione **Actualizar contraseña**, escriba la nueva y pulse **Guardar y conectar**. Si MySQL está detenido, inicie el servicio y use **Reintentar**; no es necesario volver a escribir la clave.

**Si se pierde `connection.key`** o se daña el archivo cifrado: respalde primero ambos archivos, cierre la aplicación, borre solo `database.credentials` y `connection.key` (no `config.properties` ni la carpeta XML), y vuelva a abrir el programa. Se solicitará la contraseña una vez y se generará un par nuevo. No existe mecanismo para recuperar un secreto cuya clave AES se ha perdido.

## Importación en Eclipse y WindowBuilder

Importe `app` como **Existing Maven Project**. Los formularios y paneles editables desde WindowBuilder están en
`com.kathsoft.visor.app.view`, `com.kathsoft.visor.app.view.configuracion` y `com.kathsoft.visor.app.view.usuario`.

La interfaz sigue el patrón de Kath ERP: **una ventana principal**
`app.view.Fr_Principal` con `JMenuBar`, barra de accesos rápidos y
`CardLayout` central. Los módulos de lista son `JPanel` separados,
por ejemplo `app.view.usuario.PanelUsuarios`, y los datos de un
registro se editan desde un único diálogo modal `Fr_DatosUsuario`.
Las funciones de CFDI no desarrolladas se muestran deshabilitadas.

Guía obligatoria: [docs/UI-SWING-WINDOWBUILDER.md](docs/UI-SWING-WINDOWBUILDER.md).

Abra el archivo Java con **Open With → WindowBuilder Editor → Design**. El constructor sin parámetros de cada vista no debe consultar MySQL, leer el disco ni inicializar Hibernate. GroupLayout está declarado explícitamente y FlatLaf solo se configura en `App.main`, fuera de los constructores.

## Pruebas

`mvn test` ejecuta pruebas unitarias; `mvn verify` ejecuta además `UsuariosServiceIT` mediante Testcontainers con `mysql:8.0.46`. La integración importa el SQL completo del repositorio en un contenedor desechable y **no requiere ni modifica la base de desarrollo**. Necesita Docker disponible.

## Restricciones conocidas

- RF-003 incorporará permisos granulares y acceso por contribuyente; `ADMIN`/`CONSULTA` son roles iniciales, no la implementación final del módulo de autorización.
- Sin mecanismo de bloqueo temporizado por intentos fallidos ni segundo factor. Se requiere endurecimiento adicional antes de distribuir a usuarios externos.
- La aplicación usa un cliente Swing con conexión directa a MySQL; las comprobaciones de roles en Java **no reemplazan permisos de MySQL ni constituyen autorización a prueba de un usuario con acceso directo a la BD**. Para despliegues multiusuario a través de Internet debe evaluarse un servicio intermedio.
- Las operaciones de importación masiva, conciliación, reportes y descarga SAT permanecen fuera del alcance RF-002.
- Aún requiere comprobación manual del diseñador WindowBuilder y de la UI en Windows/Linux.
- Integraciones remotas de archivos (buckets, MEGA, Dropbox) son futuras.

## Arquitectura y decisiones

Ver [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) y [docs/RF-002.md](docs/RF-002.md). Hibernate/JPA gestiona las operaciones ordinarias; las operaciones masivas podrán usar JDBC con procedimientos almacenados sobre la **misma conexión y transacción** que la unidad de trabajo Java.
