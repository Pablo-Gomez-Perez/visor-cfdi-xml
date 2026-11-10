# Reglas de arquitectura — Visor CFDI XML

## Pila establecida

- Java SE 17, Maven, Swing y FlatLaf; **todos los formularios editables con Eclipse WindowBuilder**.
- El layout predeterminado es `javax.swing.GroupLayout` con componentes declarados en campos y constructor público sin parámetros.
- Hibernate ORM + JPA para operaciones transaccionales ordinarias, mapeo de entidades y CRUD.
- JDBC especializado y procedimientos almacenados para consultas extensas, inserciones masivas y otras operaciones donde se justifique por medición o complejidad.
- MySQL 8, esquema de referencia `app/database/schema/visor_cfdi_mysql8_schema_v1.sql`.
- Java controla el alcance de cada transacción, `commit` y `rollback`, tanto en Hibernate como en JDBC.

## Transacciones mixtas Hibernate/JDBC

**Nunca abrir dos conexiones independientes para una operación que debe ser atómica.** Las operaciones que combinen Hibernate y un procedimiento almacenado han de compartir el mismo `EntityManager`, `Session` y conexión JDBC bajo una sola transacción iniciada en Java.

Ejemplo conceptual, cuando exista un procedimiento que ejecutar:

```java
jpa.enTransaccion(em -> {
    em.flush(); // sincroniza cambios JPA anteriores con la conexión
    em.unwrap(org.hibernate.Session.class).doWork(conexion -> {
        try (var sentencia = conexion.prepareCall("{call sp_procesar_lote(?)}")) {
            sentencia.setLong(1, loteId);
            sentencia.execute();
        }
    });
    em.clear(); // si el SP modifica datos que podrían permanecer en el contexto JPA
    return null;
});
```

Se recomienda que los procedimientos utilizados dentro de transacciones Java no ejecuten `COMMIT`, `ROLLBACK`, `START TRANSACTION` ni operaciones DDL con commit implícito. Para procesos masivos, establecer tamaños de lote, verificar índices y medir tiempo, memoria y bloqueos.

## Diseño de dominio

- `view`: exclusivamente componentes, eventos Swing, presentación y validaciones de formulario; no JDBC ni EntityManager.
- `service`: autorización efectiva, reglas de negocio, transacciones y unidades de trabajo.
- `model/entity`: mapeos fieles al esquema, sin código de GUI.
- `model/dto`: datos desacoplados que pueden salir del contexto JPA.
- `persistence`: factoría de entidades y transacciones acotadas.
- Mantener el documento fiscal original separado de ajustes y clasificaciones.
- Conservar XML originales en almacenamiento documental fuera de MySQL.
- El periodo de emisión no es necesariamente el periodo de pago o acreditamiento.

## Seguridad y restricciones actuales

La contraseña de MySQL se solicita por ejecución y no se guarda en disco; la de usuario se conserva como hash PBKDF2-HMAC-SHA256 con sal individual y 600 000 iteraciones. Los cambios de cuentas requieren volver a validar el rol ADMIN y quedan en `bitacora_evento`.

**Importante:** los controles de rol implementados en un cliente Swing que se conecta directamente a MySQL no constituyen una barrera de seguridad contra una persona con credenciales de escritura directas a la base. En instalaciones con usuarios no confiables o acceso por Internet será necesario un servicio intermedio que imponga autorización en el servidor, además de TLS y privilegios MySQL mínimos. RF-003 definirá permisos finos por contribuyente y módulo.

Nunca usar `hibernate.hbm2ddl.auto=update`, `create` ni `create-drop` sobre la base del usuario; debe mantenerse `validate`. Los cambios de esquema se versionarán mediante migraciones SQL.

## Arquitectura de la interfaz

Se aplica la guía obligatoria [UI Swing y WindowBuilder](UI-SWING-WINDOWBUILDER.md).
La ventana principal contiene `JMenuBar`, accesos rápidos y un `CardLayout`
central que aloja los catálogos como `JPanel` independientes. Los detalles de
un registro se editan en un único `JDialog` reutilizable para alta y cambio.
No crear ventanas de listado independientes ni ejecutar consultas JPA desde
los constructores visuales.
