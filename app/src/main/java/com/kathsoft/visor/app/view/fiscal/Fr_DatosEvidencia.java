package com.kathsoft.visor.app.view.fiscal;

import com.kathsoft.visor.app.service.fiscal.EvidenciaFinancieraService;
import com.kathsoft.visor.app.service.fiscal.EvidenciaFinancieraService.ReferenciaRep;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.*;
import javax.swing.text.MaskFormatter;

/** Formulario de una factura: registra evidencia o ajuste sobre el modelo financiero existente. */
public class Fr_DatosEvidencia extends JDialog {
    private static final long serialVersionUID=1L;
    private JPanel contenido, panelPago, panelAjuste;
    private JTabbedPane pestanas;
    private JComboBox<ReferenciaRep> rep;
    private JComboBox<String> tipoAjuste;
    private JFormattedTextField fechaPago,fechaAjuste;
    private JTextField moneda,importePago,importeFactura,referencia,motivoPago,soporte,importeAjuste,motivoAjuste;
    private JLabel lblFechaPago,lblMoneda,lblImportePago,lblImporteFactura,lblReferencia,lblMotivoPago,lblRep;
    private JLabel lblFechaAjuste,lblTipoAjuste,lblSoporte,lblImporteAjuste,lblMotivoAjuste,estado;
    private JButton guardar,cerrar;
    private EvidenciaFinancieraService servicio;
    private Long actor;private long titular,documento;private boolean ocupado;

    /** Constructor visual público sin persistencia, archivos ni red. */
    public Fr_DatosEvidencia(){
        setTitle("Evidencia de cobro, pago o ajuste");setModal(true);setSize(650,410);setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        contenido=new JPanel();setContentPane(contenido);panelPago=new JPanel();panelAjuste=new JPanel();pestanas=new JTabbedPane();
        lblRep=new JLabel("REP disponible");rep=new JComboBox<>();rep.setToolTipText("Sin REP: evidencia administrativa. Con REP: se vincula sin duplicarlo.");
        lblFechaPago=new JLabel("Fecha y hora del pago");fechaPago=new JFormattedTextField();
        lblMoneda=new JLabel("Moneda del pago");moneda=new JTextField("MXN");
        lblImportePago=new JLabel("Importe en moneda del pago");importePago=new JTextField();
        lblImporteFactura=new JLabel("Importe aplicado en moneda de factura");importeFactura=new JTextField();
        lblReferencia=new JLabel("Referencia de evidencia");referencia=new JTextField();
        lblMotivoPago=new JLabel("Justificación");motivoPago=new JTextField();
        lblFechaAjuste=new JLabel("Fecha del ajuste");fechaAjuste=new JFormattedTextField();
        lblTipoAjuste=new JLabel("Tipo");tipoAjuste=new JComboBox<>(new String[]{"NOTA_CREDITO","DESCUENTO","DEVOLUCION","ANTICIPO","OTRO"});
        lblSoporte=new JLabel("UUID del comprobante soporte");soporte=new JTextField();
        lblImporteAjuste=new JLabel("Importe en moneda de factura");importeAjuste=new JTextField();
        lblMotivoAjuste=new JLabel("Justificación");motivoAjuste=new JTextField();
        try{
            MaskFormatter pago=new MaskFormatter("####-##-## ##:##:##");pago.setPlaceholderCharacter('_');pago.install(fechaPago);
            MaskFormatter ajuste=new MaskFormatter("####-##-##");ajuste.setPlaceholderCharacter('_');ajuste.install(fechaAjuste);
        }catch(java.text.ParseException e){throw new IllegalStateException(e);}
        fechaPago.setText(LocalDate.now()+" 12:00:00");fechaAjuste.setText(LocalDate.now().toString());
        GroupLayout p=new GroupLayout(panelPago);panelPago.setLayout(p);p.setAutoCreateGaps(true);p.setAutoCreateContainerGaps(true);
        p.setHorizontalGroup(p.createSequentialGroup().addGroup(p.createParallelGroup().addComponent(lblRep).addComponent(lblFechaPago).addComponent(lblMoneda).addComponent(lblImportePago).addComponent(lblImporteFactura).addComponent(lblReferencia).addComponent(lblMotivoPago)).addGroup(p.createParallelGroup().addComponent(rep).addComponent(fechaPago).addComponent(moneda).addComponent(importePago).addComponent(importeFactura).addComponent(referencia).addComponent(motivoPago)));
        p.setVerticalGroup(p.createSequentialGroup().addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblRep).addComponent(rep)).addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblFechaPago).addComponent(fechaPago)).addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblMoneda).addComponent(moneda)).addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblImportePago).addComponent(importePago)).addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblImporteFactura).addComponent(importeFactura)).addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblReferencia).addComponent(referencia)).addGroup(p.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblMotivoPago).addComponent(motivoPago)));
        GroupLayout a=new GroupLayout(panelAjuste);panelAjuste.setLayout(a);a.setAutoCreateGaps(true);a.setAutoCreateContainerGaps(true);
        a.setHorizontalGroup(a.createSequentialGroup().addGroup(a.createParallelGroup().addComponent(lblFechaAjuste).addComponent(lblTipoAjuste).addComponent(lblSoporte).addComponent(lblImporteAjuste).addComponent(lblMotivoAjuste)).addGroup(a.createParallelGroup().addComponent(fechaAjuste).addComponent(tipoAjuste).addComponent(soporte).addComponent(importeAjuste).addComponent(motivoAjuste)));
        a.setVerticalGroup(a.createSequentialGroup().addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblFechaAjuste).addComponent(fechaAjuste)).addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblTipoAjuste).addComponent(tipoAjuste)).addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblSoporte).addComponent(soporte)).addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblImporteAjuste).addComponent(importeAjuste)).addGroup(a.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(lblMotivoAjuste).addComponent(motivoAjuste)));
        pestanas.addTab("Pago / cobro",panelPago);pestanas.addTab("Ajuste de saldo",panelAjuste);
        estado=new JLabel("Evidencia administrativa no garantiza acreditamiento. Una nota de crédito no prueba restitución de dinero.");
        guardar=new JButton("Guardar");guardar.setEnabled(false);cerrar=new JButton("Cerrar");
        GroupLayout g=new GroupLayout(contenido);contenido.setLayout(g);g.setAutoCreateGaps(true);g.setAutoCreateContainerGaps(true);
        g.setHorizontalGroup(g.createParallelGroup().addComponent(pestanas).addComponent(estado).addGroup(g.createSequentialGroup().addComponent(guardar).addComponent(cerrar)));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(pestanas).addComponent(estado).addGroup(g.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(guardar).addComponent(cerrar)));
        rep.addActionListener(e->{ReferenciaRep r=(ReferenciaRep)rep.getSelectedItem();if(r!=null){fechaPago.setText(r.fecha().toString().replace('T',' '));moneda.setText(r.monedaPago());importePago.setText(r.importePago()==null?"":r.importePago().toPlainString());importeFactura.setText(r.importeFactura()==null?"":r.importeFactura().toPlainString());referencia.setText(r.uuid());}});
        guardar.addActionListener(e->guardar());cerrar.addActionListener(e->dispose());
    }
    /** Inyecta factura y obtiene opciones REP fuera del EDT. */
    public void configurar(EvidenciaFinancieraService servicio,Long actor,long titular,long documento){
        this.servicio=servicio;this.actor=actor;this.titular=titular;this.documento=documento;ocupado=true;
        new SwingWorker<List<ReferenciaRep>,Void>(){
            @Override protected List<ReferenciaRep> doInBackground(){return servicio.referencias(actor,titular,documento);}
            @Override protected void done(){ocupado=false;try{rep.addItem(null);for(ReferenciaRep r:get())rep.addItem(r);guardar.setEnabled(true);}catch(Exception e){estado.setText(causa(e));}}
        }.execute();
    }
    private void guardar(){
        if(ocupado||servicio==null)return;
        try{
            boolean pago=pestanas.getSelectedIndex()==0;ReferenciaRep r=(ReferenciaRep)rep.getSelectedItem();
            EvidenciaFinancieraService.Pago solicitud=pago?new EvidenciaFinancieraService.Pago(LocalDateTime.parse(fechaPago.getText().replace(' ','T')),moneda.getText().strip().toUpperCase(java.util.Locale.ROOT),new BigDecimal(importePago.getText().strip()),new BigDecimal(importeFactura.getText().strip()),referencia.getText(),motivoPago.getText(),r==null?null:r.id()):null;
            LocalDate fecha=pago?null:LocalDate.parse(fechaAjuste.getText());BigDecimal importe=pago?null:new BigDecimal(importeAjuste.getText().strip());
            String uuid=soporte.getText().strip(),tipo=(String)tipoAjuste.getSelectedItem(),motivo=motivoAjuste.getText();
            ocupado=true;guardar.setEnabled(false);pestanas.setEnabled(false);
            new SwingWorker<Long,Void>(){
                @Override protected Long doInBackground(){return pago?servicio.registrar(actor,titular,documento,solicitud):servicio.ajustarPorUuid(actor,titular,documento,uuid,fecha,tipo,importe,motivo);}
                @Override protected void done(){ocupado=false;pestanas.setEnabled(true);guardar.setEnabled(true);try{get();dispose();}catch(Exception e){estado.setText(causa(e));}}
            }.execute();
        }catch(RuntimeException e){estado.setText(e.getMessage());}
    }
    private static String causa(Exception e){return (e.getCause()==null?e:e.getCause()).getMessage();}
}
