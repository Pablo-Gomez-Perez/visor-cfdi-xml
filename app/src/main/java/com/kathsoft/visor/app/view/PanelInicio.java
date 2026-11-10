package com.kathsoft.visor.app.view;

import java.awt.Color;
import java.awt.Font;

import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Panel de bienvenida incrustado en Fr_Principal.
 *
 * <p>No muestra métricas fiscales inventadas. El tablero real se
 * implementará cuando existan datos de CFDI importados.</p>
 */
public class PanelInicio extends JPanel {

    private static final long serialVersionUID = 1L;

    private JPanel panelTitulo;
    private JPanel panelContenido;
    private JLabel lblTitulo;
    private JLabel lblBienvenida;
    private JLabel lblDescripcion;
    private JLabel lblModuloPendiente;

    /**
     * Constructor sin dependencias para Eclipse WindowBuilder.
     */
    public PanelInicio() {
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        panelTitulo = new JPanel();
        panelTitulo.setBackground(new Color(25, 25, 112));
        lblTitulo = new JLabel("Inicio");
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 16f));
        lblTitulo.setForeground(Color.WHITE);
        panelTitulo.add(lblTitulo);

        panelContenido = new JPanel();
        panelContenido.setBorder(new EmptyBorder(18, 18, 18, 18));
        lblBienvenida = new JLabel("Visor CFDI XML");
        lblBienvenida.setFont(lblBienvenida.getFont().deriveFont(Font.BOLD, 20f));
        lblDescripcion = new JLabel("Utilice el menú para navegar entre los módulos disponibles.");
        lblModuloPendiente = new JLabel("La consulta, importación y el tablero de CFDI se implementarán por etapas.");

        GroupLayout gl_panelContenido = new GroupLayout(panelContenido);
        panelContenido.setLayout(gl_panelContenido);
        gl_panelContenido.setAutoCreateGaps(true);
        gl_panelContenido.setHorizontalGroup(
            gl_panelContenido.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblBienvenida)
                .addComponent(lblDescripcion)
                .addComponent(lblModuloPendiente));
        gl_panelContenido.setVerticalGroup(
            gl_panelContenido.createSequentialGroup()
                .addComponent(lblBienvenida)
                .addComponent(lblDescripcion)
                .addComponent(lblModuloPendiente)
                .addGap(0, 0, Short.MAX_VALUE));

        GroupLayout groupLayout = new GroupLayout(this);
        setLayout(groupLayout);
        groupLayout.setAutoCreateGaps(true);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(panelTitulo, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(panelContenido, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addComponent(panelTitulo, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(panelContenido, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
    }
}
