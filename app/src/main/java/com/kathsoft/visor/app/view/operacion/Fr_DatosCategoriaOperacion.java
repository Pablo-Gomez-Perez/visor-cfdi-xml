package com.kathsoft.visor.app.view.operacion;

import com.kathsoft.visor.app.service.operacion.ClasificacionOperacionesService.Naturaleza;
import java.awt.Frame;
import javax.swing.*;

/** Captura de una categoría nueva; no consulta BD ni cambia categorías históricas. */
public class Fr_DatosCategoriaOperacion extends JDialog {
    private static final long serialVersionUID=1L;
    private JPanel contenido;
    private JLabel lblClave,lblNombre,lblNaturaleza;
    private JTextField txfClave,txfNombre;
    private JComboBox<Naturaleza> cmbNaturaleza;
    private JButton btnGuardar,btnCancelar;
    private Solicitud solicitud;
    public record Solicitud(String clave,String nombre,Naturaleza naturaleza){}

    /** Constructor para WindowBuilder. */
    public Fr_DatosCategoriaOperacion(){super((Frame)null,true);inicializarComponentes();}
    /** Diálogo modal de alta. */
    public Fr_DatosCategoriaOperacion(Frame owner){super(owner,true);inicializarComponentes();}
    /** Devuelve null si se canceló. */
    public Solicitud solicitud(){return solicitud;}

    private void inicializarComponentes(){
        setTitle("Nueva categoría de operación");setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        contenido=new JPanel();setContentPane(contenido);
        lblClave=new JLabel("Clave");lblNombre=new JLabel("Nombre");lblNaturaleza=new JLabel("Naturaleza");
        txfClave=new JTextField(20);txfNombre=new JTextField(24);
        cmbNaturaleza=new JComboBox<>(Naturaleza.values());
        btnGuardar=new JButton("Guardar");btnCancelar=new JButton("Cancelar");
        GroupLayout g=new GroupLayout(contenido);contenido.setLayout(g);g.setAutoCreateGaps(true);g.setAutoCreateContainerGaps(true);
        g.setHorizontalGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(g.createSequentialGroup().addGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(lblClave).addComponent(lblNombre).addComponent(lblNaturaleza))
                .addGroup(g.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(txfClave).addComponent(txfNombre).addComponent(cmbNaturaleza)))
            .addGroup(GroupLayout.Alignment.TRAILING,g.createSequentialGroup().addComponent(btnGuardar).addComponent(btnCancelar)));
        g.setVerticalGroup(g.createSequentialGroup()
            .addGroup(g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblClave).addComponent(txfClave))
            .addGroup(g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblNombre).addComponent(txfNombre))
            .addGroup(g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblNaturaleza).addComponent(cmbNaturaleza))
            .addGroup(g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(btnGuardar).addComponent(btnCancelar)));
        btnGuardar.addActionListener(e->{
            if(txfClave.getText().isBlank()||txfNombre.getText().isBlank()){
                JOptionPane.showMessageDialog(this,"Capture clave y nombre.");return;
            }
            solicitud=new Solicitud(txfClave.getText(),txfNombre.getText(),(Naturaleza)cmbNaturaleza.getSelectedItem());dispose();
        });
        btnCancelar.addActionListener(e->dispose());getRootPane().setDefaultButton(btnGuardar);pack();
    }
}
