package com.kathsoft.visor.app.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;

import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.service.configuracion.ConexionMySqlService;

/**
 * Ciclo de vida de JPA en Java SE: una factory por proceso, un EntityManager
 * por unidad de trabajo y transacciones RESOURCE_LOCAL controladas por Java.
 *
 * <p>Para procedimientos almacenados dentro de la misma transacción use
 * entityManager.unwrap(org.hibernate.Session.class).doWork(connection -> ...)
 * y nunca abra otra conexión JDBC independiente para esa unidad de trabajo.</p>
 */
public final class JpaContext implements AutoCloseable {

    private final EntityManagerFactory factory;

    /**
     * Construye el pool de conexiones de esta sesión de la aplicación.
     *
     * @param config datos locales sin contraseña
     * @param password clave MySQL temporal; se obtiene del almacén cifrado
     *                 y se limpia del arreglo del llamador después de usarla
     */
    public JpaContext(ConfiguracionSistema config, char[] password) {
        Objects.requireNonNull(config, "Configuración requerida");
        Objects.requireNonNull(password, "Contraseña requerida");
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");
        props.put("jakarta.persistence.jdbc.url", new ConexionMySqlService().construirUrl(config));
        props.put("jakarta.persistence.jdbc.user", config.usuario());
        props.put("jakarta.persistence.jdbc.password", new String(password));
        props.put("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        props.put("hibernate.hikari.maximumPoolSize", "5");
        props.put("hibernate.hikari.minimumIdle", "1");
        props.put("hibernate.hikari.connectionTimeout", "5000");
        props.put("hibernate.hikari.poolName", "VisorCFDI");
        props.put("hibernate.hbm2ddl.auto", "validate");
        try {
            factory = Persistence.createEntityManagerFactory("visor-cfdi", props);
        } finally {
            props.remove("jakarta.persistence.jdbc.password");
        }
    }

    /**
     * Ejecuta una unidad de trabajo dentro de una transacción indivisible.
     *
     * @param trabajo función transaccional
     * @param <T> tipo del resultado
     * @return resultado de la operación confirmada
     */
    public <T> T enTransaccion(TrabajoJpa<T> trabajo) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                T resultado = trabajo.ejecutar(em);
                tx.commit();
                return resultado;
            } catch (RuntimeException | Error ex) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw ex;
            }
        }
    }

    /**
     * Operación de solo lectura sin contexto persistente compartido.
     *
     * @param trabajo operación de consulta
     * @param <T> tipo de salida
     * @return resultado consultado
     */
    public <T> T consultar(TrabajoJpa<T> trabajo) {
        try (EntityManager em = factory.createEntityManager()) {
            return trabajo.ejecutar(em);
        }
    }

    /**
     * Devuelve si el pool Hibernate permanece disponible.
     *
     * @return true si EntityManagerFactory sigue abierta
     */
    public boolean estaAbierto() {
        return factory.isOpen();
    }

    @Override
    public void close() {
        if (factory.isOpen()) {
            factory.close();
        }
    }

    /** Unidad de trabajo tipada ejecutada sobre un EntityManager acotado. */
    @FunctionalInterface
    public interface TrabajoJpa<T> {
        T ejecutar(EntityManager em);
    }
}
