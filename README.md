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
- Contraseña de MySQL solicitada en cada arranque; no se guarda en disco.

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
6. Introduzca la contraseña MySQL al iniciar. Hibernate valida el esquema pero no lo modifica.
7. Registre el **primer administrador**. No existe cuenta administrativa predeterminada.
8. Inicie sesión usando el usuario y la contraseña de aplicación recién creados.
9. Abra **Administración de usuarios** para crear las demás cuentas.

Si la configuración de RF-001 ya existe, la aplicación comienza directamente solicitando la contraseña MySQL; no vuelve a pedir la carpeta de XML.

## Importación en Eclipse y WindowBuilder

Importe `app` como **Existing Maven Project**. Los formularios editables desde WindowBuilder están en
`com.kathsoft.visor.app.view.configuracion` y `com.kathsoft.visor.app.view.usuario`.

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
