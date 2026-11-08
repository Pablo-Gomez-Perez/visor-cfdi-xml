package com.kathsoft.visor;

import java.awt.EventQueue;
import java.io.IOException;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;
import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.service.configuracion.ConfiguracionLocalService;
import com.kathsoft.visor.app.view.configuracion.Fr_ConfiguracionInicial;
import com.kathsoft.visor.app.view.usuario.Fr_ConexionMySql;

/**
 * Punto de acceso Swing para configuración, conexión MySQL y RF-002.
 * Nunca inicia conexiones ni inicializa Hibernate en el diseñador visual.
 */
public final class App {

    private App() {
    }

    /**
     * Abre la ventana apropiada según la configuración local existente.
     *
     * @param args argumentos de consola sin uso
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            FlatLightLaf.setup();
            UIManager.put("Component.arc", 10);
            try {
                ConfiguracionSistema config = new ConfiguracionLocalService().cargar().orElse(null);
                if (config == null) {
                    new Fr_ConfiguracionInicial().setVisible(true);
                } else {
                    abrirConexion(config);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null,
                        "No se pudo leer la configuración. Compruebe el archivo local.\n"
                                + ex.getMessage(), "Error de configuración", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Continúa el arranque después de guardar la configuración inicial.
     *
     * @param config datos de conexión sin contraseña
     */
    public static void abrirConexion(ConfiguracionSistema config) {
        new Fr_ConexionMySql(config).setVisible(true);
    }
}
