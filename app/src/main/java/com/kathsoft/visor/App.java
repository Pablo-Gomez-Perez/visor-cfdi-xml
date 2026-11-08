package com.kathsoft.visor;

import java.awt.EventQueue;
import java.io.IOException;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;
import com.kathsoft.visor.app.model.configuracion.ConfiguracionSistema;
import com.kathsoft.visor.app.service.configuracion.ConfiguracionLocalService;
import com.kathsoft.visor.app.view.configuracion.Fr_ConfiguracionInicial;

/**
 * Punto de entrada de Visor CFDI XML.
 *
 * <p>Durante RF-001 se abre el formulario de configuración; el inicio de
 * sesión y el tablero principal se incorporarán en módulos posteriores.</p>
 */
public final class App {

    private App() {
    }

    /**
     * Inicia la aplicación Swing en el Event Dispatch Thread.
     *
     * @param args argumentos de ejecución, actualmente sin uso
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            FlatLightLaf.setup();
            UIManager.put("Component.arc", 10);

            ConfiguracionSistema guardada = null;
            try {
                guardada = new ConfiguracionLocalService().cargar().orElse(null);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null,
                        "No fue posible leer la configuración local.\n"
                                + "Revise el archivo antes de guardar una nueva configuración.\n"
                                + ex.getMessage(),
                        "Configuración local", JOptionPane.ERROR_MESSAGE);
            }

            Fr_ConfiguracionInicial formulario = new Fr_ConfiguracionInicial(guardada);
            formulario.setVisible(true);
        });
    }
}
