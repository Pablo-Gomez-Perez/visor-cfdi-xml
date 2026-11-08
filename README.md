# Visor CFDI XML

Aplicación de escritorio multiplataforma para administración de CFDI mexicanos.

## Estado actual: RF-001 — Configuración inicial

Esta rama incorpora exclusivamente el primer módulo del MVP:

- Formulario Swing de configuración, diseñado con GroupLayout explícito y constructor sin argumentos para Eclipse WindowBuilder.
- Apariencia FlatLaf (sin reemplazar los componentes estándar de Swing).
- Captura de servidor, puerto, esquema y usuario de MySQL 8.
- Prueba real de conexión JDBC mediante SELECT 1, en un SwingWorker.
- Selección, creación y comprobación de la carpeta local para el repositorio de XML originales.
- Persistencia de parámetros no secretos en el directorio del usuario.

**No hay aún importador de XML, tablas de CFDI, administración de usuarios ni migraciones de base de datos.** La creación del esquema MySQL es responsabilidad del administrador en esta etapa.

### Requisitos

- JDK 17.
- Maven 3.9 o compatible.
- Servidor MySQL 8 accesible, con una base de datos ya creada.

### Ejecución

Desde el subdirectorio `app`:

    mvn clean test
    mvn exec:java

El punto de entrada es `com.kathsoft.visor.App`. El formulario se abre automáticamente. En Eclipse, importe `app` como **Existing Maven Project**, instale WindowBuilder y abra `Fr_ConfiguracionInicial.java` con **Open With > WindowBuilder Editor**. No es necesario tener MySQL en ejecución para abrir la vista previa del diseñador.

### Configuración de MySQL

El formulario comprueba la conexión con una base de datos que ya debe existir. Para un entorno local de desarrollo puede crearse con:

    CREATE DATABASE visor_cfdi
      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

Utilice un usuario MySQL propio con permisos adecuados; no es recomendable usar `root` para ejecutar la aplicación.

El formulario **no crea tablas ni modifica el esquema**. No guarda la contraseña en disco: solicita una contraseña al realizar la prueba o guardar la configuración. Las credenciales de operación y la sesión de usuario se integrarán en módulos posteriores.

### Ubicaciones locales

- Configuración: `~/.visor-cfdi-xml/config.properties` (en Windows, dentro de la carpeta principal del usuario).
- Repositorio documental predeterminado: `~/VisorCFDI/xml/`.
- Se guarda la ruta del repositorio, no archivos XML ni contraseñas en MySQL.
- El directorio documental es independiente de los ejercicios y periodos fiscales de cada CFDI.

La carpeta de documentos puede estar dentro de una ruta sincronizada por el usuario. **Aún no existe integración con Dropbox, MEGA ni buckets**; esa funcionalidad tendrá un proveedor documental separado en una etapa posterior.

### Seguridad de conexión

Para `localhost`, `127.0.0.1` y loopback IPv6 la URL JDBC utiliza conexión local sin TLS. Para un host remoto se exige TLS con `VERIFY_IDENTITY`, por lo que el servidor debe presentar un certificado válido reconocido por el entorno Java.

### Estructura del módulo

- `app/model/configuracion/`: modelo validado de configuración.
- `app/service/configuracion/`: lectura/escritura local y comprobación MySQL.
- `app/view/configuracion/`: formulario Swing compatible con WindowBuilder.

## Desarrollo incremental

El siguiente módulo previsto es RF-002, registro e inicio de sesión de usuarios. La configuración de RF-001 es un primer paso y no debe interpretarse como un sistema de facturación o cumplimiento fiscal terminado.
