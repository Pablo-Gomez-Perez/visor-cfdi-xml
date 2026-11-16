package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.*;
import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Importación real con las rutinas SQL publicadas y MySQL 8 desechable. */
@Testcontainers
class ImportacionPagosHistoricosIT {
    @TempDir Path temporal;

    private static Path db(String ruta) {
        Path base = Path.of(System.getProperty("user.dir"));
        Path directa = base.resolve("database").resolve(ruta);
        return (Files.isRegularFile(directa) ? directa :
                base.resolve("app/database").resolve(ruta)).toAbsolutePath();
    }

    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
        .withDatabaseName("visor_cfdi").withUsername("visor_it").withPassword("Prueba#Rf007")
        .withLogConsumer(frame -> System.err.print(frame.getUtf8String()))
        .withCopyFileToContainer(MountableFile.forHostPath(db("schema/visor_cfdi_mysql8_schema_v1.sql")),
            "/docker-entrypoint-initdb.d/01-schema.sql")
        .withCopyFileToContainer(MountableFile.forHostPath(db("procedures/procedures.sql")),
            "/docker-entrypoint-initdb.d/02-procedures.sql")
        .withCopyFileToContainer(MountableFile.forClasspathResource("db/schema-pagos-pre-migracion.sql"),
            "/tmp/schema-pagos-pre-migracion.sql")
        .withCopyFileToContainer(MountableFile.forHostPath(db("migrations/20261115_rf007_pagos10.sql")),
            "/tmp/migracion-pagos10.sql");

    @Test void importaVersionesYContinuaTrasRechazo() throws Exception {
        Path repo = temporal.resolve("repo");
        var config = new ConfiguracionSistema(MYSQL.getHost(),MYSQL.getMappedPort(3306),
                MYSQL.getDatabaseName(),MYSQL.getUsername(),repo);
        try (JpaContext jpa = new JpaContext(config,MYSQL.getPassword().toCharArray())) {
            var usuarios = new UsuariosService(jpa);
            usuarios.inicializarRoles();
            var admin = usuarios.registrarAdministradorInicial(
                    new SolicitudUsuario("Admin histórico","adminhistorico",null,"ADMIN"),
                    "ClaveSegura#Rf007-2026".toCharArray());
            long titular = usuarios.contribuyentes().registrar(admin.id(),
                    new SolicitudContribuyente("BBB010101BBB","Titular",null,null));
            Path a = escritura("a.xml","3.3",1,EjemplosPagosHistoricos.PAGO10);
            Path rechazo = escritura("incompatible.xml","3.3",2,
                    EjemplosPagosHistoricos.PAGO10.replace("Version=\"1.0\"","Version=\"9.0\""));
            Path historico = escritura("historico.xml","3.3",3,EjemplosPagosHistoricos.PAGO10_IMPUESTOS);
            Path moderno = escritura("moderno.xml","4.0",4,EjemplosPagosHistoricos.PAGO20);
            var servicio = new ImportacionCfdiService(jpa,repo);
            var r = servicio.importar(admin.id(),new ImportacionCfdiService.Solicitud(
                    titular,List.of(a,rechazo,historico,moderno),false),p->{},()->false);
            assertEquals(4,r.encontrados());
            assertEquals(3,r.nuevos());
            assertEquals(1,r.rechazados());
            assertTrue(r.ultimoRechazo().contains("incompatible.xml"));
            assertTrue(r.ultimoRechazo().contains("000000000002"));
            jpa.consultar(em -> {
                assertEquals(2L,numero(em,"SELECT COUNT(*) FROM pago_fiscal_complemento WHERE version_complemento='1.0'"));
                assertEquals(1L,numero(em,"SELECT COUNT(*) FROM pago_fiscal_complemento WHERE version_complemento='2.0'"));
                assertEquals(1L,numero(em,"SELECT COUNT(*) FROM pago_fiscal_totales"));
                assertEquals(1L,numero(em,"SELECT COUNT(*) FROM pago_fiscal_documento WHERE importe_pagado IS NULL AND id_documento_historico='123-45-123456789'"));
                assertEquals(2L,numero(em,"SELECT COUNT(*) FROM pago_fiscal_grupo_impuesto10"));
                assertEquals(2L,numero(em,"SELECT COUNT(*) FROM pago_fiscal_impuesto_pago WHERE grupo_impuesto_10 IS NOT NULL"));
                assertEquals(2L,numero(em,"SELECT COUNT(*) FROM pago_fiscal_documento WHERE metodo_pago_dr='PPD' AND equivalencia_dr IS NULL"));
                assertEquals(1L,numero(em,"SELECT COUNT(*) FROM importacion_archivo WHERE resultado='RECHAZADO'"));
                assertEquals("PARCIAL",em.createNativeQuery("SELECT estado FROM lote_importacion WHERE id="+r.loteId()).getSingleResult());
                return null;
            });
            var dup = servicio.importar(admin.id(),new ImportacionCfdiService.Solicitud(
                    titular,List.of(a,moderno),false),p->{},()->false);
            assertEquals(2,dup.duplicados());
            assertEquals(0,dup.nuevos());
        }
    }

    /** Ejecuta exactamente el fichero de migración entregado sobre el esquema anterior. */
    @Test void migracionIncrementalConservaPago20Existente() throws Exception {
        var ejecucion = MYSQL.execInContainer("sh","-ec", """
            export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
            mysql -uroot -e 'CREATE DATABASE visor_migracion_it'
            mysql -uroot visor_migracion_it < /tmp/schema-pagos-pre-migracion.sql
            mysql -uroot visor_migracion_it < /tmp/migracion-pagos10.sql
            mysql -uroot --batch --skip-column-names visor_migracion_it -e "
                SELECT CONCAT(c.version_complemento,':',CAST(t.monto_total_pagos AS DECIMAL(10,2)))
                FROM pago_fiscal_complemento c JOIN pago_fiscal_totales t USING(documento_id);
                SELECT COUNT(*) FROM pago_fiscal_documento WHERE importe_pagado=50
                  AND uuid_documento IS NOT NULL AND id_documento_historico IS NULL;
                SELECT COUNT(*) FROM information_schema.routines
                  WHERE routine_schema='visor_migracion_it' AND routine_name='sp_rf007_importar_pago';
            "
            """);
        assertEquals(0,ejecucion.getExitCode(),ejecucion.getStderr());
        assertEquals("2.0:50.00\n1\n1",ejecucion.getStdout().strip());
    }

    private Path escritura(String nombre,String version,int n,String complemento) throws Exception {
        Path p=temporal.resolve(nombre);
        Files.writeString(p,EjemplosPagosHistoricos.xml(version,
                "123e4567-e89b-12d3-a456-"+String.format("%012d",n),complemento));
        return p;
    }

    private static long numero(jakarta.persistence.EntityManager em,String sql) {
        return ((Number)em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
