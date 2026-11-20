package com.kathsoft.visor.app.service.fiscal;

import static org.junit.jupiter.api.Assertions.*;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.model.dto.SolicitudContribuyente;
import com.kathsoft.visor.app.model.dto.SolicitudUsuario;
import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService.Asignacion;
import com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService.Tipo;
import com.kathsoft.visor.app.service.fiscal.EvidenciaFinancieraService.Pago;
import com.kathsoft.visor.app.service.fiscal.ResumenFiscalService.Seccion;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Naturaleza;
import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Seleccion;
import com.kathsoft.visor.app.service.usuario.UsuariosService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Flujo contable sintético de junio/julio de 2026, con documentos compartidos y resultados independientes. */
@Testcontainers
class MvpFiscalServiceIT {
    private static final long FACTURA = 1001;
    private static final long MIXTA = 1002;
    private static final long ESPECIAL = 1003;
    private static final long PUE = 1004;
    private static final long NOTA = 1005;
    private static final long CANCELADA = 1006;
    private static final long SUSTITUCION = 1007;
    private static final long DIVISA = 1008;
    private static final long DESCONOCIDA = 1009;
    private static final long REP_CANCELADO = 1010;
    private static final long HISTORICA = 1011;
    private static final long EXCEDIDA = 1012;
    private static final long PRIVADA = 1100;
    private static final long TERCERO_EMISOR = 900001;
    private static final long TERCERO_RECEPTOR = 900002;
    @TempDir Path temporal;

    private static Path esquema() {
        Path p = Path.of("database/schema/visor_cfdi_mysql8_schema_v1.sql");
        return (Files.isRegularFile(p) ? p : Path.of("app").resolve(p)).toAbsolutePath();
    }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("visor_cfdi").withUsername("visor_it").withPassword("Prueba#MvpFiscal")
            .withCopyFileToContainer(MountableFile.forHostPath(esquema()), "/docker-entrypoint-initdb.d/01-schema.sql");

    private static BigDecimal decimal(String valor) { return valor == null ? null : new BigDecimal(valor); }
    private static String uuid(long id) { return "123e4567-e89b-12d3-a456-" + String.format("%012d", id); }
    private static LocalDateTime fecha(int mes, int dia) { return LocalDateTime.of(2026, mes, dia, 12, 0); }

    private static void ejecutar(EntityManager em, String sql, Object... valores) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < valores.length; i++) q.setParameter(i + 1, valores[i]);
        q.executeUpdate();
    }

    private static void igual(String esperado, Object actual) {
        assertNotNull(actual);
        assertEquals(0, decimal(esperado).compareTo((BigDecimal) actual));
    }

    private static int columna(TablaFiscal tabla, String nombre) {
        int indice = tabla.columnas().indexOf(nombre);
        assertTrue(indice >= 0, nombre);
        return indice;
    }

    private static List<Object> documento(TablaFiscal tabla, String columnaId, long id) {
        int indice = columna(tabla, columnaId);
        return tabla.filas().stream().filter(f -> f.get(indice) instanceof Number n && n.longValue() == id)
                .findFirst().orElseThrow(() -> new AssertionError("Falta documento " + id + " en " + tabla.columnas()));
    }

    private static FiltroFiscal mes(long titular, int mes) {
        LocalDate inicio = LocalDate.of(2026, mes, 1);
        return new FiltroFiscal(titular, inicio, inicio.withDayOfMonth(inicio.lengthOfMonth()));
    }

    private static FiltroFiscal categoria(long titular, long categoria) {
        return new FiltroFiscal(titular, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                "RECIBIDO", "", "", categoria, "MXN", "I", "VIGENTE");
    }

    private static void sat(EntityManager em, long documento, String estado) {
        if (estado != null) ejecutar(em,
                "INSERT INTO consulta_estado_sat(documento_id,estado,fecha_consulta) VALUES (?1,?2,'2026-07-31 23:00:00')",
                documento, estado);
    }

    private static void comprobante(EntityManager em, long a, long b, long id, String tipo, int mes, int dia,
            String moneda, String subtotal, String total, String metodo, String estado, boolean compartido) {
        ejecutar(em, "INSERT INTO documento_fiscal(id,clase,uuid,version_estandar,fecha_emision) VALUES (?1,'CFDI',?2,'4.0',?3)",
                id, uuid(id), Timestamp.valueOf(fecha(mes, dia)));
        ejecutar(em, "INSERT INTO cfdi(documento_id,tipo,moneda,subtotal,total,metodo_pago) VALUES (?1,?2,?3,?4,?5,?6)",
                id, tipo, moneda, decimal(subtotal), decimal(total), metodo);
        ejecutar(em, """
            INSERT INTO documento_participante(documento_id,rol,tercero_id,rfc,nombre_razon_social)
            VALUES (?1,'E',?2,'AAA010101AAA','Emisor sintético'),(?1,'R',?3,'BBB010101BBB','Receptor sintético')
            """, id, TERCERO_EMISOR, TERCERO_RECEPTOR);
        if (compartido) ejecutar(em,
                "INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,?2,'RECIBIDO')", a, id);
        ejecutar(em, "INSERT INTO contribuyente_documento(contribuyente_id,documento_id,sentido) VALUES (?1,?2,'EMITIDO')", b, id);
        sat(em, id, estado);
    }

    private static long concepto(EntityManager em, long documento, int ordinal, String importe, String objeto) {
        long id = documento * 10 + ordinal;
        ejecutar(em, """
            INSERT INTO cfdi_concepto(id,documento_id,ordinal,clave_prod_serv,cantidad,clave_unidad,
                descripcion,valor_unitario,importe,objeto_impuesto)
            VALUES (?1,?2,?3,'01010101',1,'H87','Concepto sintético',?4,?4,?5)
            """, id, documento, ordinal, decimal(importe), objeto);
        return id;
    }

    private static long impuesto(EntityManager em, long concepto, String movimiento, int ordinal, String codigo,
            String factor, String base, String tasa, String importe) {
        long id = concepto * 10 + ordinal + (movimiento.equals("RETENCION") ? 5 : 0);
        ejecutar(em, """
            INSERT INTO cfdi_concepto_impuesto(id,concepto_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
            VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)
            """, id, concepto, movimiento, ordinal, codigo, factor, decimal(base), decimal(tasa), decimal(importe));
        return id;
    }

    private static void iva(EntityManager em, long documento, String base, String importe) {
        long c = concepto(em, documento, 1, base, "02");
        impuesto(em, c, "TRASLADO", 1, "002", "Tasa", base, "0.16", importe);
        ejecutar(em, """
            INSERT INTO cfdi_impuesto_global(documento_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
            VALUES (?1,'TRASLADO',1,'002','Tasa',?2,0.16,?3)
            """, documento, decimal(base), decimal(importe));
        ejecutar(em, "INSERT INTO cfdi_impuesto_totales(documento_id,total_trasladados) VALUES (?1,?2)", documento, decimal(importe));
    }

    private static long rep(EntityManager em, long a, long b, long id, long factura, int mes, int dia,
            String monedaDr, String monedaP, String montoPago, String importeDr, String saldoAnterior,
            String saldoInsoluto, int parcialidad, String base, String iva, String equivalencia,
            String cambio, String estado, String version) {
        comprobante(em, a, b, id, "P", mes, dia, "XXX", "0", "0", null, estado, true);
        ejecutar(em, "INSERT INTO pago_fiscal_complemento(documento_id,version_complemento) VALUES (?1,?2)", id, version);
        long pago = id * 10, dr = id * 10 + 1;
        ejecutar(em, """
            INSERT INTO pago_fiscal(id,documento_id,ordinal,fecha_pago,forma_pago,moneda,tipo_cambio,monto)
            VALUES (?1,?2,1,?3,'03',?4,?5,?6)
            """, pago, id, Timestamp.valueOf(fecha(mes, dia)), monedaP, decimal(cambio), decimal(montoPago));
        ejecutar(em, """
            INSERT INTO pago_fiscal_documento(id,pago_fiscal_id,ordinal,uuid_documento,moneda,equivalencia_dr,
                num_parcialidad,saldo_anterior,importe_pagado,saldo_insoluto,objeto_impuesto)
            VALUES (?1,?2,1,?3,?4,?5,?6,?7,?8,?9,'02')
            """, dr, pago, uuid(factura), monedaDr, decimal(equivalencia), parcialidad,
                decimal(saldoAnterior), decimal(importeDr), decimal(saldoInsoluto));
        if (iva != null) {
            ejecutar(em, """
                INSERT INTO pago_fiscal_impuesto_documento(pago_documento_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
                VALUES (?1,'TRASLADO',1,'002','Tasa',?2,0.16,?3)
                """, dr, decimal(base), decimal(iva));
            BigDecimal ivaPago = monedaDr.equals(monedaP) ? decimal(iva)
                    : decimal(iva).divide(decimal(equivalencia));
            BigDecimal basePago = monedaDr.equals(monedaP) ? decimal(base)
                    : decimal(base).divide(decimal(equivalencia));
            ejecutar(em, """
                INSERT INTO pago_fiscal_impuesto_pago(pago_fiscal_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
                VALUES (?1,'TRASLADO',1,'002','Tasa',?2,0.16,?3)
                """, pago, basePago, ivaPago);
        }
        if (version.equals("2.0")) {
            BigDecimal montoMxn = monedaP.equals("MXN") ? decimal(montoPago) : decimal(montoPago).multiply(decimal(cambio));
            ejecutar(em, "INSERT INTO pago_fiscal_totales(documento_id,monto_total_pagos) VALUES (?1,?2)", id, montoMxn);
            if (iva != null) ejecutar(em, "INSERT INTO pago_fiscal_total_impuesto(documento_id,atributo,importe_mxn) VALUES (?1,'TotalTrasladosImpuestoIVA16',?2)",
                    id, monedaDr.equals("MXN") ? decimal(iva) : decimal(iva).divide(decimal(equivalencia)));
        }
        return dr;
    }

    private static Pago pago(int mes, int dia, String importe, String referencia, Long dr) {
        return new Pago(fecha(mes, dia), "MXN", decimal(importe), decimal(importe), referencia,
                "Evidencia administrativa sintética comprobada", dr);
    }

    private static void estructura(TablaFiscal tabla) {
        assertNotNull(tabla.aviso());
        assertFalse(tabla.columnas().isEmpty());
        for (var fila : tabla.filas()) assertEquals(tabla.columnas().size(), fila.size(), tabla.columnas().toString());
    }

    @Test void flujoDeDosMesesConCaratulasTrazabilidadPagosYAlcance() {
        var config = new ConfiguracionSistema(MYSQL.getHost(), MYSQL.getMappedPort(3306),
                MYSQL.getDatabaseName(), MYSQL.getUsername(), temporal.resolve("repo"));
        try (var jpa = new JpaContext(config, MYSQL.getPassword().toCharArray())) {
            var usuarios = new UsuariosService(jpa);
            usuarios.inicializarRoles();
            long actor = usuarios.registrarAdministradorInicial(new SolicitudUsuario("Admin MVP", "adminmvp", null, "ADMIN"),
                    "ClaveSegura#Mvp-2026".toCharArray()).id();
            long a = usuarios.contribuyentes().registrar(actor, new SolicitudContribuyente("BBB010101BBB", "Receptor MVP", null, null));
            long b = usuarios.contribuyentes().registrar(actor, new SolicitudContribuyente("AAA010101AAA", "Emisor MVP", null, null));
            usuarios.registrar(actor, new SolicitudUsuario("Lector MVP", "lectormvp", null, "CONSULTA"), "ClaveSegura#LectorMvp-2026".toCharArray());
            long lector = jpa.<Number>consultar(em -> (Number) em.createNativeQuery("SELECT id FROM usuario WHERE login='lectormvp'").getSingleResult()).longValue();
            jpa.enTransaccion(em -> {
                ejecutar(em, "INSERT INTO tercero(id,rfc,nombre_razon_social) VALUES (?1,'AAA010101AAA','Emisor sintético'),(?2,'BBB010101BBB','Receptor sintético')", TERCERO_EMISOR, TERCERO_RECEPTOR);
                ejecutar(em, "INSERT INTO contribuyente_tercero_rol(contribuyente_id,tercero_id,rol) VALUES (?1,?3,'PROVEEDOR'),(?2,?4,'CLIENTE')", a, b, TERCERO_EMISOR, TERCERO_RECEPTOR);
                comprobante(em, a, b, FACTURA, "I", 6, 5, "MXN", "1000", "1160", "PPD", "VIGENTE", true);
                iva(em, FACTURA, "1000", "160");
                comprobante(em, a, b, MIXTA, "I", 6, 6, "MXN", "27000", "31320", "PPD", "VIGENTE", true);
                long materia = concepto(em, MIXTA, 1, "10000", "02");
                long papel = concepto(em, MIXTA, 2, "2000", "02");
                long equipo = concepto(em, MIXTA, 3, "15000", "02");
                impuesto(em, materia, "TRASLADO", 1, "002", "Tasa", "10000", "0.16", "1600");
                impuesto(em, papel, "TRASLADO", 1, "002", "Tasa", "2000", "0.16", "320");
                impuesto(em, equipo, "TRASLADO", 1, "002", "Tasa", "15000", "0.16", "2400");
                ejecutar(em, "INSERT INTO cfdi_impuesto_totales(documento_id,total_trasladados) VALUES (?1,4320)", MIXTA);
                comprobante(em, a, b, ESPECIAL, "I", 6, 7, "MXN", "500", "541.2", "PPD", "VIGENTE", true);
                long cero = concepto(em, ESPECIAL, 1, "100", "02");
                long exento = concepto(em, ESPECIAL, 2, "200", "02");
                long cuota = concepto(em, ESPECIAL, 3, "200", "02");
                impuesto(em, cero, "TRASLADO", 1, "002", "Tasa", "100", "0", "0");
                impuesto(em, exento, "TRASLADO", 1, "002", "Exento", "200", null, null);
                impuesto(em, cuota, "TRASLADO", 1, "003", "Cuota", "10", "2", "20");
                impuesto(em, cuota, "TRASLADO", 2, "002", "Tasa", "220", "0.16", "35.2");
                impuesto(em, cuota, "RETENCION", 1, "001", "Tasa", "200", "0.05", "10");
                impuesto(em, cuota, "RETENCION", 2, "002", "Tasa", "200", "0.02", "4");
                ejecutar(em, "INSERT INTO cfdi_impuesto_totales(documento_id,total_trasladados,total_retenidos) VALUES (?1,55.2,14)", ESPECIAL);
                for (long id : new long[] { PUE, CANCELADA, SUSTITUCION, DIVISA, DESCONOCIDA, REP_CANCELADO, HISTORICA, EXCEDIDA }) {
                    comprobante(em, a, b, id, "I", 6, (int) (id - 996), id == DIVISA ? "USD" : "MXN", "100", "116",
                            id == PUE ? "PUE" : "PPD", id == CANCELADA ? "CANCELADO" : id == DESCONOCIDA ? null : "VIGENTE", true);
                    iva(em, id, "100", "16");
                }
                comprobante(em, a, b, NOTA, "E", 7, 10, "MXN", "100", "116", null, "VIGENTE", true);
                iva(em, NOTA, "100", "16");
                ejecutar(em, "INSERT INTO cfdi_relacion_grupo(id,documento_id,ordinal,tipo_relacion) VALUES (800001,?1,1,'04'),(800002,?2,1,'01')", SUSTITUCION, NOTA);
                ejecutar(em, "INSERT INTO cfdi_relacion_uuid(grupo_id,uuid_relacionado) VALUES (800001,?1),(800002,?2)", uuid(CANCELADA), uuid(PUE));
                rep(em, a, b, 2001, FACTURA, 6, 15, "MXN", "MXN", "580", "580", "1160", "580", 1, "500", "80", "1", null, "VIGENTE", "2.0");
                rep(em, a, b, 2002, FACTURA, 7, 15, "MXN", "MXN", "580", "580", "580", "0", 2, "500", "80", "1", null, "VIGENTE", "2.0");
                rep(em, a, b, 2007, SUSTITUCION, 6, 25, "MXN", "MXN", "116", "116", "116", "0", 1, "100", "16", "1", null, "VIGENTE", "2.0");
                rep(em, a, b, 2008, DIVISA, 7, 16, "USD", "MXN", "2320", "116", "116", "0", 1, "100", "16", "0.05", null, "VIGENTE", "2.0");
                rep(em, a, b, 2009, DESCONOCIDA, 7, 17, "MXN", "MXN", "116", "116", "116", "0", 1, "100", "16", "1", null, "VIGENTE", "2.0");
                rep(em, a, b, 2010, REP_CANCELADO, 7, 18, "MXN", "MXN", "116", "116", "116", "0", 1, "100", "16", "1", null, "CANCELADO", "2.0");
                rep(em, a, b, 2011, 999999, 7, 19, "MXN", "MXN", "116", "116", "116", "0", 1, "100", "16", "1", null, "VIGENTE", "2.0");
                rep(em, a, b, 2012, HISTORICA, 7, 20, "MXN", "MXN", "116", "116", "116", "0", 1, null, null, "1", null, "VIGENTE", "1.0");
                rep(em, a, b, 2013, EXCEDIDA, 7, 21, "MXN", "MXN", "80", "80", "116", "36", 1, "62.5", "10", "1", null, "VIGENTE", "2.0");
                rep(em, a, b, 2014, EXCEDIDA, 7, 22, "MXN", "MXN", "80", "80", "116", "36", 2, "62.5", "10", "1", null, "VIGENTE", "2.0");
                comprobante(em, a, b, PRIVADA, "I", 7, 23, "MXN", "1", "1", "PUE", "VIGENTE", false);
                concepto(em, PRIVADA, 1, "1", "01");
                for (int i = 0; i < 205; i++) {
                    long id = 10000 + i;
                    comprobante(em, a, b, id, "I", 6, 21, "MXN", "1", "1", "PUE", null, true);
                    concepto(em, id, 1, "1", "01");
                }
                return null;
            });
            var operaciones = usuarios.operaciones();
            operaciones.crearPredeterminadas(actor, a);
            var categorias = operaciones.categorias(actor, a);
            long costo = categorias.stream().filter(c -> c.clave().equals("COSTO")).findFirst().orElseThrow().id();
            long gasto = categorias.stream().filter(c -> c.clave().equals("GASTO")).findFirst().orElseThrow().id();
            long inversion = categorias.stream().filter(c -> c.clave().equals("INVERSION")).findFirst().orElseThrow().id();
            operaciones.clasificar(actor, a, List.of(new Seleccion(MIXTA, 0)), gasto, "Referencia general sustituida por detalle");
            var detalle = new ClasificacionDetalleService(jpa);
            long revision = detalle.guardar(actor, a, MIXTA, Tipo.CONCEPTO, MIXTA * 10 + 1, 0,
                    List.of(new Asignacion(costo, decimal("10000"), "Materia prima")), "");
            revision = detalle.guardar(actor, a, MIXTA, Tipo.CONCEPTO, MIXTA * 10 + 2, revision,
                    List.of(new Asignacion(gasto, decimal("2000"), "Papelería")), "Gasto de papelería");
            revision = detalle.guardar(actor, a, MIXTA, Tipo.CONCEPTO, MIXTA * 10 + 3, revision,
                    List.of(new Asignacion(inversion, decimal("15000"), "Equipo")), "Inversión de equipo");
            long tratamiento = detalle.tratamientos(actor, a).stream().filter(t -> t.clave().equals("IVA_ACREDITABLE")).findFirst().orElseThrow().id();
            detalle.guardar(actor, a, MIXTA, Tipo.IMPUESTO, (MIXTA * 10 + 1) * 10 + 1, revision,
                    List.of(new Asignacion(tratamiento, decimal("400"), "Tratamiento parcial sintético")),
                    "Asignación parcial con remanente fiscal pendiente");
            detalle.guardar(actor, a, FACTURA, Tipo.IMPUESTO, (FACTURA * 10 + 1) * 10 + 1, 0,
                    List.of(new Asignacion(tratamiento, decimal("160"), "Tratamiento de prueba con evidencia")), "");
            var resumen = new ResumenFiscalService(jpa);
            var impuestos = new ImpuestosService(jpa);
            FiltroFiscal junio = mes(a, 6), julio = mes(a, 7);

            // Ejercitar todas las ramas SQL, ambas fechas y ambos sentidos del mismo documento.
            for (Seccion seccion : Seccion.values()) {
                estructura(resumen.consultar(actor, junio, seccion, 0));
                estructura(resumen.consultar(actor, julio, seccion, 0));
                estructura(resumen.consultar(actor, mes(b, 7), seccion, 0));
            }
            for (ImpuestosService.Nivel nivel : ImpuestosService.Nivel.values()) {
                estructura(impuestos.consultar(actor, junio, nivel, 0));
                estructura(impuestos.consultar(actor, julio, nivel, 0));
            }
            var documental = impuestos.consultar(actor, junio, ImpuestosService.Nivel.CONCEPTO, 0);
            igual("160", documento(documental, "Documento", FACTURA).get(columna(documental, "Importe original")));
            var ivaJunio = resumen.consultar(actor, junio, Seccion.IVA, 0);
            var ivaJulio = resumen.consultar(actor, julio, Seccion.IVA, 0);
            var cobroJunio = documento(ivaJunio, "Factura documento", FACTURA);
            var cobroJulio = documento(ivaJulio, "Factura documento", FACTURA);
            igual("80", cobroJunio.get(columna(ivaJunio, "IVA realizadoDR")));
            igual("80", cobroJulio.get(columna(ivaJulio, "IVA realizadoDR")));
            igual("80", cobroJunio.get(columna(ivaJunio, "IVA acreditable según registroDR")));
            igual("160", ((BigDecimal) cobroJunio.get(columna(ivaJunio, "IVA realizadoDR")))
                    .add((BigDecimal) cobroJulio.get(columna(ivaJulio, "IVA realizadoDR"))));
            assertFalse(ivaJunio.filas().stream().anyMatch(f -> f.get(columna(ivaJunio, "Factura documento")) instanceof Number n && n.longValue() == PUE));
            assertNull(documento(ivaJunio, "Factura documento", SUSTITUCION).get(columna(ivaJunio, "IVA realizadoDR")));
            assertTrue(documento(ivaJunio, "Factura documento", SUSTITUCION).get(columna(ivaJunio, "Incidencias")).toString().contains("SUSTITUCION"));
            igual("16", documento(ivaJulio, "Factura documento", DIVISA).get(columna(ivaJulio, "IVA realizadoDR")));
            igual("320", documento(ivaJulio, "Factura documento", DIVISA).get(columna(ivaJulio, "IVA realizado MXN derivado")));
            assertNull(documento(ivaJulio, "Factura documento", DESCONOCIDA).get(columna(ivaJulio, "IVA realizadoDR")));
            assertNull(documento(ivaJulio, "Factura documento", REP_CANCELADO).get(columna(ivaJulio, "IVA realizadoDR")));
            assertNull(documento(ivaJulio, "Factura documento", HISTORICA).get(columna(ivaJulio, "IVA realizadoDR")));
            assertTrue(ivaJulio.filas().stream().filter(f -> uuid(EXCEDIDA).equals(f.get(columna(ivaJulio, "UUID factura"))))
                    .allMatch(f -> f.get(columna(ivaJulio, "IVA realizadoDR")) == null));
            assertNull(documento(ivaJulio, "REP documento", 2011).get(columna(ivaJulio, "Factura documento")));

            var costoResumen = resumen.consultar(actor, categoria(a, costo), Seccion.EGRESOS, 0);
            var gastoResumen = resumen.consultar(actor, categoria(a, gasto), Seccion.EGRESOS, 0);
            var inversionResumen = resumen.consultar(actor, categoria(a, inversion), Seccion.EGRESOS, 0);
            assertEquals(1, costoResumen.filas().size());
            igual("10000", costoResumen.filas().get(0).get(columna(costoResumen, "Neto clasificado")));
            igual("2000", gastoResumen.filas().get(0).get(columna(gastoResumen, "Neto clasificado")));
            igual("15000", inversionResumen.filas().get(0).get(columna(inversionResumen, "Neto clasificado")));
            igual("1600", costoResumen.filas().get(0).get(columna(costoResumen, "IVA declarado distribuido")));
            var tributosCosto = resumen.consultar(actor, categoria(a, costo), Seccion.TRIBUTOS_AGRUPADOS, 0);
            assertEquals(2, tributosCosto.filas().size());
            igual("10000", tributosCosto.filas().stream()
                    .map(f -> (BigDecimal) f.get(columna(tributosCosto, "Base distribuida DERIVADA")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            igual("1600", tributosCosto.filas().stream()
                    .map(f -> (BigDecimal) f.get(columna(tributosCosto, "Impuesto distribuido DERIVADO")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            igual("400", tributosCosto.filas().stream()
                    .filter(f -> "IVA_ACREDITABLE".equals(f.get(columna(tributosCosto, "Tratamiento fiscal"))))
                    .findFirst().orElseThrow().get(columna(tributosCosto, "Impuesto distribuido DERIVADO")));
            igual("1200", tributosCosto.filas().stream()
                    .filter(f -> "PENDIENTE".equals(f.get(columna(tributosCosto, "Tratamiento fiscal"))))
                    .findFirst().orElseThrow().get(columna(tributosCosto, "Impuesto distribuido DERIVADO")));
            assertTrue(resumen.consultar(actor, categoria(a, costo), Seccion.IEPS, 0).filas().isEmpty());
            var ieps = resumen.consultar(actor, junio, Seccion.IEPS, 0);
            assertEquals(1, ieps.filas().size());
            assertEquals("Cuota", ieps.filas().get(0).get(columna(ieps, "Factor")));
            igual("20", ieps.filas().get(0).get(columna(ieps, "Importes originales")));
            var retenciones = resumen.consultar(actor, junio, Seccion.RETENCIONES, 0);
            igual("14", retenciones.filas().stream().map(f -> (BigDecimal) f.get(columna(retenciones, "Importes originales"))).reduce(BigDecimal.ZERO, BigDecimal::add));

            // Paginación sólo limita el detalle; el resumen conserva los 216 CFDI I de junio.
            var origenes = resumen.consultar(actor, junio, Seccion.ORIGENES, 0);
            assertEquals(200, origenes.filas().size());
            assertTrue(origenes.siguiente());
            var segunda = resumen.consultar(actor, junio, Seccion.ORIGENES, 1);
            assertFalse(segunda.filas().isEmpty());
            var general = resumen.consultar(actor, junio, Seccion.GENERALES, 0);
            assertEquals(216L, general.filas().stream().mapToLong(f -> ((Number) f.get(columna(general, "Documentos"))).longValue()).sum());
            var mxn = general.filas().stream().filter(f -> "MXN".equals(f.get(columna(general, "Moneda"))))
                    .map(f -> (BigDecimal) f.get(columna(general, "Subtotal documental"))).reduce(BigDecimal.ZERO, BigDecimal::add);
            igual("29405", mxn);

            // RF-020 conserva un conteo documental, aunque un CFDI aparezca en tres naturalezas.
            var filtroOperaciones = new ClasificacionOperacionesService.Filtro(a, junio.desde(), junio.hasta(), "RECIBIDO", "", gasto);
            var resumenOperaciones = operaciones.resumir(actor, filtroOperaciones);
            assertEquals(1, resumenOperaciones.operaciones());
            assertEquals(1, resumenOperaciones.clasificadas());
            assertEquals(3, resumenOperaciones.grupos().size());
            assertTrue(resumenOperaciones.grupos().stream().allMatch(g -> g.documentos() == 1));
            igual("10000", resumenOperaciones.grupos().stream().filter(g -> g.naturaleza().equals("COSTO")).findFirst().orElseThrow().subtotalNeto());
            igual("2000", resumenOperaciones.grupos().stream().filter(g -> g.naturaleza().equals("GASTO")).findFirst().orElseThrow().subtotalNeto());
            igual("15000", resumenOperaciones.grupos().stream().filter(g -> g.naturaleza().equals("INVERSION")).findFirst().orElseThrow().subtotalNeto());
            igual("4320", resumenOperaciones.grupos().stream().map(g -> g.ivaTrasladado()).reduce(BigDecimal.ZERO, BigDecimal::add));
            assertEquals(216, operaciones.resumir(actor, new ClasificacionOperacionesService.Filtro(a, junio.desde(), junio.hasta(), "RECIBIDO", "", -1)).operaciones());

            var financiera = new EvidenciaFinancieraService(jpa);
            assertEquals(2, financiera.referencias(actor, a, FACTURA).size());
            assertEquals(2, financiera.referencias(actor, b, FACTURA).size());
            financiera.registrar(actor, a, FACTURA, pago(6, 15, "580", "REP-junio-receptor", 20011L));
            financiera.registrar(actor, b, FACTURA, pago(6, 15, "580", "REP-junio-emisor", 20011L));
            financiera.registrar(actor, a, FACTURA, pago(7, 15, "580", "REP-julio-receptor", 20021L));
            financiera.registrar(actor, b, FACTURA, pago(7, 15, "580", "REP-julio-emisor", 20021L));
            assertTrue(financiera.referencias(actor, a, FACTURA).isEmpty());
            assertTrue(financiera.referencias(actor, b, FACTURA).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> financiera.registrar(actor, a, FACTURA,
                    pago(6, 15, "580", "REP-junio-receptor", 20011L)));
            financiera.registrar(actor, a, PUE, pago(6, 20, "60", "Banco-junio-PUE", null));
            financiera.registrar(actor, a, PUE, pago(7, 5, "20", "Banco-julio-PUE", null));
            financiera.ajustar(actor, a, PUE, NOTA, LocalDate.of(2026, 7, 11), "NOTA_CREDITO", decimal("16"), "Reducción documentada, sin presumir devolución efectiva");
            assertThrows(IllegalArgumentException.class, () -> financiera.registrar(actor, a, CANCELADA,
                    pago(7, 12, "10", "Cancelada-rechazada", null)));
            assertThrows(SecurityException.class, () -> financiera.ajustar(actor, a, PUE, PRIVADA,
                    LocalDate.of(2026, 7, 24), "OTRO", decimal("1"), "Soporte ajeno rechazado"));
            var saldoJunio = resumen.consultar(actor, junio, Seccion.FINANCIERO, 0);
            var saldoJulio = resumen.consultar(actor, julio, Seccion.FINANCIERO, 0);
            var ppdJunio = documento(saldoJunio, "Documento", FACTURA);
            var ppdJulio = documento(saldoJulio, "Documento", FACTURA);
            igual("580", ppdJunio.get(columna(saldoJunio, "Saldo al corte documentado")));
            igual("580", ppdJulio.get(columna(saldoJulio, "Saldo inicial documentado")));
            igual("580", ppdJulio.get(columna(saldoJulio, "Pagos/cobros del periodo")));
            igual("0", ppdJulio.get(columna(saldoJulio, "Saldo al corte documentado")));
            var pueJulio = documento(saldoJulio, "Documento", PUE);
            igual("56", pueJulio.get(columna(saldoJulio, "Saldo inicial documentado")));
            igual("20", pueJulio.get(columna(saldoJulio, "Pagos/cobros del periodo")));
            igual("16", pueJulio.get(columna(saldoJulio, "Ajustes del periodo")));
            igual("20", pueJulio.get(columna(saldoJulio, "Saldo al corte documentado")));
            assertNull(documento(saldoJulio, "Documento", MIXTA).get(columna(saldoJulio, "Saldo al corte documentado")));
            var ivaTrasAplicar = resumen.consultar(actor, julio, Seccion.IVA, 0);
            igual("80", documento(ivaTrasAplicar, "Factura documento", FACTURA).get(columna(ivaTrasAplicar, "IVA realizadoDR")));

            jpa.enTransaccion(em -> {
                ejecutar(em, "INSERT INTO usuario_contribuyente(usuario_id,contribuyente_id) VALUES (?1,?2)", lector, a);
                ejecutar(em, "INSERT INTO rol_permiso(rol_id,permiso_id) SELECT r.id,p.id FROM rol r CROSS JOIN permiso p WHERE r.clave='CONSULTA' AND p.recurso='FISCAL' AND p.accion='CONSULTAR'");
                return null;
            });
            estructura(resumen.consultar(lector, junio, Seccion.GENERALES, 0));
            assertThrows(SecurityException.class, () -> resumen.consultar(lector, mes(b, 6), Seccion.GENERALES, 0));
            assertThrows(SecurityException.class, () -> financiera.registrar(lector, a, PUE, pago(7, 12, "1", "Sin-permiso", null)));
            assertThrows(SecurityException.class, () -> detalle.consultar(actor, a, PRIVADA, Tipo.CONCEPTO, PRIVADA * 10 + 1));
            jpa.enTransaccion(em -> {
                ejecutar(em, "DELETE rp FROM rol_permiso rp JOIN rol r ON r.id=rp.rol_id JOIN permiso p ON p.id=rp.permiso_id WHERE r.clave='CONSULTA' AND p.recurso='FISCAL' AND p.accion='CONSULTAR'");
                return null;
            });
            usuarios.inicializarRoles();
            assertFalse(usuarios.permisosDe(lector).contains(Permisos.FISCAL_CONSULTAR));
            assertThrows(SecurityException.class, () -> resumen.consultar(lector, junio, Seccion.IVA, 0));

            // EXPLAIN usa datos sintéticos y la misma proyección parametrizada; no afirma rendimiento en producción.
            jpa.consultar(em -> {
                String sql = "EXPLAIN FORMAT=JSON " + FiscalSql.BASE + " SELECT u.documento_id,u.uuid FROM universo u WHERE u.tipo='I' ORDER BY u.fecha_emision,u.documento_id";
                String plan = FiscalSql.query(em, sql, junio).getSingleResult().toString();
                assertTrue(plan.contains("query_block"));
                assertTrue(plan.contains("table_name"));
                System.err.println("EXPLAIN fiscal sintético MySQL8: " + plan);
                return null;
            });

            // La factura se emitió en junio; el rastro desde IVA de julio conserva el periodo de pago.
            var rastroJulio = new FiltroFiscal(a, julio.desde(), julio.hasta(), "RECIBIDO", "", "", 0,
                    "MXN", "I", "VIGENTE", FACTURA, "002", "TRASLADO", "Tasa", decimal("0.16"));
            assertEquals(LocalDate.of(2026, 7, 1), rastroJulio.desde());
            assertEquals(FACTURA, rastroJulio.documento());
            var origenPrevio = resumen.consultar(actor, rastroJulio, Seccion.ORIGENES, 0);
            assertEquals(1, origenPrevio.filas().size());
            igual("1000", documento(origenPrevio, "Documento", FACTURA).get(columna(origenPrevio, "Neto original concepto")));
            igual("160", documento(origenPrevio, "Documento", FACTURA).get(columna(origenPrevio, "Impuesto original")));
            var impuestoPrevio = impuestos.consultar(actor, rastroJulio, ImpuestosService.Nivel.CONCEPTO, 0);
            assertTrue(documento(impuestoPrevio, "Documento", FACTURA).get(columna(impuestoPrevio, "Emisión")).toString().startsWith("2026-06-05"));

            var manuales = resumen.consultar(actor, julio, Seccion.IVA_MANUAL, 0);
            var pueManual = documento(manuales, "Documento", PUE);
            assertEquals("PENDIENTE_REVISION_FISCAL", pueManual.get(columna(manuales, "Estado auxiliar")));
            igual("20", pueManual.get(columna(manuales, "Aplicación en moneda factura")));
            igual("16", pueManual.get(columna(manuales, "IVA original factura")));
            assertNotNull(pueManual.get(columna(manuales, "IVA proporcional DERIVADO pendiente")));
            assertTrue(pueManual.get(columna(manuales, "Incidencias")).toString().contains("NO_CONFIRMA_REQUISITOS_FISCALES"));
            assertFalse(manuales.filas().stream().anyMatch(f -> f.get(columna(manuales, "Documento")) instanceof Number n && n.longValue() == FACTURA));

            // Cancelar un REP aplicado no elimina aplicaciones: excluye su evidencia de ambos titulares.
            jpa.enTransaccion(em -> {
                ejecutar(em, "INSERT INTO consulta_estado_sat(documento_id,estado,fecha_consulta) VALUES (2002,'CANCELADO','2026-08-01 00:00:00')");
                return null;
            });
            for (long titular : new long[] { a, b }) {
                var saldosCancelacion = resumen.consultar(actor, mes(titular, 7), Seccion.FINANCIERO, 0);
                var facturaCancelacion = documento(saldosCancelacion, "Documento", FACTURA);
                igual("580", facturaCancelacion.get(columna(saldosCancelacion, "Saldo inicial documentado")));
                igual("0", facturaCancelacion.get(columna(saldosCancelacion, "Pagos/cobros del periodo")));
                igual("580", facturaCancelacion.get(columna(saldosCancelacion, "Saldo al corte documentado")));
                igual("580", facturaCancelacion.get(columna(saldosCancelacion, "Pagos REP excluidos por estado / archivado")));
            }
            var ivaCancelacion = resumen.consultar(actor, julio, Seccion.IVA, 0);
            assertNull(documento(ivaCancelacion, "Factura documento", FACTURA).get(columna(ivaCancelacion, "IVA realizadoDR")));
            jpa.consultar(em -> {
                assertEquals(4L, ((Number) em.createNativeQuery("SELECT COUNT(*) FROM aplicacion_pago a JOIN obligacion o ON o.id=a.obligacion_id WHERE o.documento_id=1001").getSingleResult()).longValue());
                assertEquals(2L, ((Number) em.createNativeQuery("SELECT COUNT(*) FROM movimiento_pago_complemento WHERE pago_fiscal_documento_id=20021").getSingleResult()).longValue());
                igual("160", em.createNativeQuery("SELECT importe FROM cfdi_concepto_impuesto WHERE id=100111").getSingleResult());
                return null;
            });

            // Fixtures adicionales se crean después de las comprobaciones de conteos y montos previos.
            jpa.enTransaccion(em -> {
                comprobante(em, a, b, 1200, "I", 7, 24, "MXN", "4", "4.000002", "PPD", "VIGENTE", true);
                long residual = concepto(em, 1200, 1, "4", "02");
                impuesto(em, residual, "TRASLADO", 1, "002", "Tasa", "4", "0.16", "0.000002");
                comprobante(em, a, b, 1201, "I", 7, 25, "MXN", "100", "116", "PUE", "VIGENTE", true);
                ejecutar(em, "UPDATE documento_fiscal SET version_estandar='3.3' WHERE id=1201");
                long historico = concepto(em, 1201, 1, "100", null);
                impuesto(em, historico, "TRASLADO", 1, "002", "Tasa", "100", "0.16", "16");
                comprobante(em, a, b, 1202, "I", 7, 26, "MXN", "100", "116", "PUE", "VIGENTE", true);
                iva(em, 1202, "100", "16");
                ejecutar(em, """
                    INSERT INTO obligacion(contribuyente_id,documento_id,tercero_id,naturaleza,moneda,monto_original,fecha_origen)
                    VALUES (?1,1202,?2,'PAGAR','USD',100,'2026-07-26')
                    """, a, TERCERO_EMISOR);
                long dr = rep(em, a, b, 2100, ESPECIAL, 7, 28, "MXN", "MXN", "541.2", "541.2", "541.2", "0", 1,
                        "220", "35.2", "1", null, "VIGENTE", "2.0");
                ejecutar(em, """
                    INSERT INTO pago_fiscal_impuesto_documento(pago_documento_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
                    VALUES (?1,'RETENCION',1,'001','Tasa',200,0.05,10),
                           (?1,'RETENCION',2,'002','Tasa',200,0.02,4)
                    """, dr);
                ejecutar(em, """
                    INSERT INTO pago_fiscal_impuesto_pago(pago_fiscal_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
                    VALUES (21000,'RETENCION',1,'001',NULL,NULL,NULL,10),
                           (21000,'RETENCION',2,'002',NULL,NULL,NULL,4)
                    """);
                ejecutar(em, "INSERT INTO pago_fiscal_total_impuesto(documento_id,atributo,importe_mxn) VALUES (2100,'TotalRetencionesISR',10),(2100,'TotalRetencionesIVA',4)");
                ejecutar(em, """
                    INSERT INTO pago_fiscal_impuesto_documento(pago_documento_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
                    VALUES (?1,'TRASLADO',2,'003','Cuota',10,2,20)
                    """, dr);
                ejecutar(em, """
                    INSERT INTO pago_fiscal_impuesto_pago(pago_fiscal_id,movimiento,ordinal,impuesto,tipo_factor,base,tasa_cuota,importe)
                    VALUES (21000,'TRASLADO',2,'003','Cuota',10,2,20)
                    """);
                return null;
            });
            var iepsJulio = resumen.consultar(actor, julio, Seccion.IEPS, 0);
            var iepsPago = iepsJulio.filas().stream()
                    .filter(f -> "REP_FECHA_PAGO".equals(f.get(columna(iepsJulio, "Fuente temporal"))))
                    .findFirst().orElseThrow();
            igual("10", iepsPago.get(columna(iepsJulio, "Bases DR originales")));
            igual("20", iepsPago.get(columna(iepsJulio, "IEPS DR original vinculado")));
            assertEquals("DR_DOCUMENTAL_IEPS_PENDIENTE_REGLA", iepsPago.get(columna(iepsJulio, "Estado auxiliar")));
            for (int i = 1; i <= 4; i++) operaciones.crearCategoria(actor, a, "RESIDUO_" + i, "Residuo sintético " + i, Naturaleza.OTRA);
            var categoriasResiduo = operaciones.categorias(actor, a).stream().filter(c -> c.clave().startsWith("RESIDUO_"))
                    .map(c -> new Asignacion(c.id(), BigDecimal.ONE, "Parte sintética del neto")).toList();
            assertEquals(4, categoriasResiduo.size());
            detalle.guardar(actor, a, 1200, Tipo.CONCEPTO, 12001, 0, categoriasResiduo, "");
            var filtroResiduo = new FiltroFiscal(a, julio.desde(), julio.hasta(), "RECIBIDO", "", "", 0,
                    "MXN", "I", "VIGENTE", 1200, "002", "TRASLADO", "Tasa", decimal("0.16"));
            var residuo = resumen.consultar(actor, filtroResiduo, Seccion.ORIGENES, 0);
            assertEquals(4, residuo.filas().size());
            assertTrue(residuo.filas().stream().allMatch(f -> ((BigDecimal) f.get(columna(residuo, "Impuesto distribuido derivado"))).signum() >= 0));
            igual("0.000002", residuo.filas().stream().map(f -> (BigDecimal) f.get(columna(residuo, "Impuesto distribuido derivado"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            igual("4", residuo.filas().stream().map(f -> (BigDecimal) f.get(columna(residuo, "Base distribuida derivada"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            var tributosResiduo = resumen.consultar(actor, filtroResiduo, Seccion.TRIBUTOS_AGRUPADOS, 0);
            assertEquals(4, tributosResiduo.filas().size());
            igual("0.000002", tributosResiduo.filas().stream()
                    .map(f -> (BigDecimal) f.get(columna(tributosResiduo, "Impuesto distribuido DERIVADO")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            operaciones.clasificar(actor, a, List.of(new Seleccion(1201, 0)), gasto, "CFDI 3.3 sin ObjetoImp");
            var filtroHistorico = new FiltroFiscal(a, julio.desde(), julio.hasta(), "RECIBIDO", "", "", 0,
                    "MXN", "I", "VIGENTE", 1201, "", "", "", null);
            var egresoHistorico = resumen.consultar(actor, filtroHistorico, Seccion.EGRESOS, 0);
            assertEquals(1, egresoHistorico.filas().size());
            igual("100", egresoHistorico.filas().get(0).get(columna(egresoHistorico, "Base 16% distribuida")));
            igual("16", egresoHistorico.filas().get(0).get(columna(egresoHistorico, "IVA declarado distribuido")));
            var obligacionAjena = assertThrows(IllegalArgumentException.class, () -> financiera.registrar(actor, a, 1202,
                    pago(7, 27, "10", "Obligacion-incompatible", null)));
            assertTrue(obligacionAjena.getMessage().contains("no concilia"));
            jpa.consultar(em -> {
                assertEquals(0L, ((Number) em.createNativeQuery("SELECT COUNT(*) FROM aplicacion_pago a JOIN obligacion o ON o.id=a.obligacion_id WHERE o.documento_id=1202").getSingleResult()).longValue());
                assertEquals("USD", em.createNativeQuery("SELECT moneda FROM obligacion WHERE documento_id=1202").getSingleResult());
                return null;
            });
            var conciliacionRet = new ConciliacionPagosService(jpa).documentosContraPago(actor, julio, 0);
            var retenidos = conciliacionRet.filas().stream().filter(f -> ((Number) f.get(columna(conciliacionRet, "Documento REP"))).longValue() == 2100
                    && "RETENCION".equals(f.get(columna(conciliacionRet, "Movimiento")))).toList();
            assertEquals(2, retenidos.size());
            for (var retenido : retenidos) {
                assertNull(retenido.get(columna(conciliacionRet, "Factor")));
                assertNull(retenido.get(columna(conciliacionRet, "Tasa/cuota")));
                assertNull(retenido.get(columna(conciliacionRet, "Base DR convertida derivada")));
                igual("0", retenido.get(columna(conciliacionRet, "Diferencia impuesto")));
                String importe = "001".equals(retenido.get(columna(conciliacionRet, "Impuesto"))) ? "10" : "4";
                igual(importe, retenido.get(columna(conciliacionRet, "Impuesto DR convertido derivado")));
                igual(importe, retenido.get(columna(conciliacionRet, "ImpuestoP original")));
            }

            // Un pago administrativo anterior al REP se concilia sin crear otro movimiento ni aplicación.
            jpa.enTransaccion(em -> {
                comprobante(em, a, b, 1203, "I", 7, 27, "MXN", "100", "116", "PPD", "VIGENTE", true);
                iva(em, 1203, "100", "16");
                return null;
            });
            long movimientoManual = financiera.registrar(actor, a, 1203,
                    pago(7, 29, "58", "Banco-previo-REP", null));
            assertEquals(1, financiera.aplicacionesNoVinculadas(actor, a, 1203).size());
            jpa.enTransaccion(em -> {
                rep(em, a, b, 2200, 1203, 7, 29, "MXN", "MXN", "58", "58", "116", "58", 1,
                        "50", "8", "1", null, "VIGENTE", "2.0");
                return null;
            });
            assertThrows(SecurityException.class,
                    () -> financiera.vincular(actor, b, 1203, movimientoManual, 22001, "Titular ajeno"));
            assertEquals(movimientoManual,
                    financiera.vincular(actor, a, 1203, movimientoManual, 22001, "Conciliación bancaria y REP sintéticos"));
            assertTrue(financiera.aplicacionesNoVinculadas(actor, a, 1203).isEmpty());
            assertThrows(IllegalArgumentException.class,
                    () -> financiera.vincular(actor, a, 1203, movimientoManual, 22001, "Segundo vínculo rechazado"));
            jpa.consultar(em -> {
                assertEquals(1L, ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM aplicacion_pago WHERE movimiento_pago_id=:m")
                        .setParameter("m", movimientoManual).getSingleResult()).longValue());
                assertEquals(1L, ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM movimiento_pago_complemento WHERE movimiento_pago_id=:m")
                        .setParameter("m", movimientoManual).getSingleResult()).longValue());
                return null;
            });
            var julioConciliado = resumen.consultar(actor, julio, Seccion.IVA_MANUAL, 0);
            assertFalse(julioConciliado.filas().stream().anyMatch(f ->
                    f.get(columna(julioConciliado, "Documento")) instanceof Number n && n.longValue() == 1203));

            // Pago del ejercicio anterior: la emisión no limita el universo del REP en julio de 2026.
            jpa.enTransaccion(em -> {
                comprobante(em, a, b, 1204, "I", 6, 1, "MXN", "100", "116", "PPD", "VIGENTE", true);
                ejecutar(em, "UPDATE documento_fiscal SET fecha_emision='2025-12-18 12:00:00' WHERE id=1204");
                iva(em, 1204, "100", "16");
                rep(em, a, b, 2201, 1204, 7, 30, "MXN", "MXN", "116", "116", "116", "0", 1,
                        "100", "16", "1", null, "VIGENTE", "2.0");
                return null;
            });
            var julioEjercicioAnterior = resumen.consultar(actor, julio, Seccion.IVA, 0);
            igual("16", documento(julioEjercicioAnterior, "Factura documento", 1204)
                    .get(columna(julioEjercicioAnterior, "IVA realizadoDR")));

            // Medianoche civil: la zona local de la JVM no mueve una emisión o un pago al mes anterior.
            jpa.enTransaccion(em -> {
                comprobante(em, a, b, 1206, "I", 7, 1, "MXN", "100", "116", "PPD", "VIGENTE", true);
                ejecutar(em, "UPDATE documento_fiscal SET fecha_emision='2026-07-01 00:00:00' WHERE id=1206");
                iva(em, 1206, "100", "16");
                rep(em, a, b, 2206, 1206, 7, 1, "MXN", "MXN", "116", "116", "116", "0", 1,
                        "100", "16", "1", null, "VIGENTE", "2.0");
                ejecutar(em, "UPDATE pago_fiscal SET fecha_pago='2026-07-01 00:00:00' WHERE documento_id=2206");
                return null;
            });
            java.util.TimeZone zonaAnterior=java.util.TimeZone.getDefault();
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Mexico_City"));
                var origenesJunioFinal = resumen.consultar(actor, junio, Seccion.ORIGENES, 0);
                assertTrue(origenesJunioFinal.filas().stream().noneMatch(f ->
                        f.get(columna(origenesJunioFinal, "Documento")) instanceof Number n && n.longValue() == 1206));
                assertEquals(216, operaciones.resumir(actor,
                        new ClasificacionOperacionesService.Filtro(a, junio.desde(), junio.hasta(), "RECIBIDO", "", -1)).operaciones());
                var ivaMedianoche = resumen.consultar(actor, julio, Seccion.IVA, 0);
                igual("16", documento(ivaMedianoche, "Factura documento", 1206)
                        .get(columna(ivaMedianoche, "IVA realizadoDR")));
            } finally {
                java.util.TimeZone.setDefault(zonaAnterior);
            }
        }
    }
}
