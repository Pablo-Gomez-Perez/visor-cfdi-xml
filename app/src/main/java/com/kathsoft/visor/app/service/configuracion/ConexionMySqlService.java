package com.kathsoft.visor.app.service.configuracion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;

/**
 * Comprobación aislada de conectividad MySQL 8.
 *
 * <p>Solo ejecuta SELECT 1. No crea esquemas, usuarios ni tablas. Para
 * conexiones remotas exige TLS con comprobación de identidad del servidor.</p>
 */
public class ConexionMySqlService {

    /**
     * Abre y verifica una conexión con el esquema MySQL indicado.
     *
     * @param datos parámetros de conexión
     * @param clave contraseña temporal, nunca persistida por el servicio
     * @throws SQLException cuando falla la conexión o la consulta
     */
    public void probarConexion(ConfiguracionSistema datos, char[] clave) throws SQLException {
        Objects.requireNonNull(datos, "La configuración es obligatoria");
        Objects.requireNonNull(clave, "La clave no puede ser nula");

        Properties credenciales = new Properties();
        credenciales.setProperty("user", datos.usuario());
        credenciales.setProperty("password", new String(clave));
        credenciales.setProperty("connectTimeout", "5000");
        credenciales.setProperty("socketTimeout", "5000");

        try (Connection conexion = DriverManager.getConnection(construirUrl(datos), credenciales);
                PreparedStatement consulta = conexion.prepareStatement("SELECT 1");
                ResultSet resultado = consulta.executeQuery()) {
            if (!resultado.next() || resultado.getInt(1) != 1) {
                throw new SQLException("El servidor no respondió a la consulta de verificación");
            }
        } finally {
            credenciales.remove("password");
        }
    }

    /**
     * Construye una URL sin parámetros de origen arbitrario.
     *
     * <p>Solo para loopback se permite recuperar la clave pública RSA del
     * servidor y autenticar caching_sha2_password sin TLS. En conexiones
     * remotas se prohíbe RSA retrieval y se exige TLS VERIFY_IDENTITY,
     * con CA confiable y nombre del host verificado.</p>
     *
     * @param datos configuración validada
     * @return URL JDBC lista para el driver MySQL
     */
    public String construirUrl(ConfiguracionSistema datos) {
        String host = datos.host();
        boolean local = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
        String ssl = local ? "DISABLED" : "VERIFY_IDENTITY";
        return "jdbc:mysql://" + host + ":" + datos.puerto() + "/" + datos.baseDatos()
                + "?sslMode=" + ssl
                + "&allowPublicKeyRetrieval=" + local
                + "&connectionTimeZone=UTC";
    }
}
