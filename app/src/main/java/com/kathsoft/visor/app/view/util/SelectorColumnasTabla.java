package com.kathsoft.visor.app.view.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * Selector reutilizable de columnas para JTable. Es una opción de presentación:
 * nunca elimina campos del TableModel ni altera consultas, permisos o exportación.
 *
 * <p>Las columnas se identifican por encabezado y por sección; las preferencias
 * de un usuario no afectan a otro. Las instancias TableColumn se conservan
 * mientras el modelo siga vigente, preservando ancho, editor y renderer.
 * Un nuevo setModel() requiere llamar nuevamente a actualizar().</p>
 */
public final class SelectorColumnasTabla {
    private final Preferences preferencias;
    private final String prefijo;
    private final Map<JTable, Estado> estados = new WeakHashMap<>();

    private static final class Estado {
        private final TableModel modelo;
        private final Map<Integer, TableColumn> columnas = new LinkedHashMap<>();
        private final String clave;
        private Set<String> ocultas;
        Estado(TableModel modelo, String clave, Set<String> ocultas) {
            this.modelo = modelo;
            this.clave = clave;
            this.ocultas = ocultas;
        }
    }

    /**
     * @param usuario clave local sin información sensible; por ejemplo id de sesión
     * @implNote Inicializar tras la inyección de sesión; no desde constructor WindowBuilder.
     */
    public SelectorColumnasTabla(String usuario) {
        if(usuario==null || !usuario.matches("[A-Za-z0-9_-]{1,36}"))
            throw new IllegalArgumentException("Identificador de preferencias inválido");
        this.prefijo = usuario + "-";
        this.preferencias = Preferences.userNodeForPackage(SelectorColumnasTabla.class).node("columnas");
    }

    /**
     * Registra un modelo recién cargado y aplica las columnas ocultas almacenadas.
     * Conserva todas las columnas en TableModel para selección por ID y CSV.
     */
    public void actualizar(JTable tabla, String seccion) {
        Objects.requireNonNull(tabla);
        String clave = clave(seccion);
        TableModel modelo = tabla.getModel();
        Set<String> ocultas = leer(clave);
        Estado estado = new Estado(modelo, clave, ocultas);
        TableColumnModel cm=tabla.getColumnModel();
        for(int i=0;i<cm.getColumnCount();i++){
            TableColumn col=cm.getColumn(i);
            estado.columnas.put(col.getModelIndex(),col);
        }
        for(int i=0;i<modelo.getColumnCount();i++){
            if(!estado.columnas.containsKey(i)){
                TableColumn col=new TableColumn(i);
                col.setHeaderValue(modelo.getColumnName(i));
                col.setPreferredWidth(135);
                estado.columnas.put(i,col);
            }
        }
        estados.put(tabla,estado);
        aplicar(tabla,estado);
    }

    /**
     * Construye un menú emergente ligero de casillas para la tabla seleccionada.
     * No añade JFrame/JDialog ni bloquea el diseño de Eclipse WindowBuilder.
     */
    public JPopupMenu menu(JTable tabla,String seccion){
        Objects.requireNonNull(tabla);
        Estado previo=estados.get(tabla);
        if(previo==null || previo.modelo!=tabla.getModel() || !previo.clave.equals(clave(seccion))){
            actualizar(tabla,seccion);
            previo=estados.get(tabla);
        }
        final Estado estado=previo;
        JPopupMenu menu=new JPopupMenu("Columnas visibles");
        if(tabla.getModel().getColumnCount()==0){
            JButton sinDatos=new JButton("Realice una consulta primero");
            sinDatos.setEnabled(false);
            menu.add(sinDatos);
            return menu;
        }
        // Casillas estándar dentro de un scroll: permite seleccionar muchas sin
        // cerrar el emergente, incluso con 25+ columnas y pantalla pequeña.
        JPanel opciones=new JPanel();
        opciones.setLayout(new BoxLayout(opciones,BoxLayout.Y_AXIS));
        for(int i=0;i<tabla.getModel().getColumnCount();i++){
            String titulo=tabla.getModel().getColumnName(i);
            JCheckBox item=new JCheckBox(titulo,!estado.ocultas.contains(titulo));
            item.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            item.addActionListener(e->{
                LinkedHashSet<String> nuevas=new LinkedHashSet<>(estado.ocultas);
                if(item.isSelected())nuevas.remove(titulo);
                else nuevas.add(titulo);
                if(nuevas.size()>=tabla.getModel().getColumnCount()){
                    item.setSelected(true);
                    return;
                }
                estado.ocultas=nuevas;
                preferencias.put(estado.clave,serializar(nuevas));
                aplicar(tabla,estado);
            });
            opciones.add(item);
        }
        JScrollPane lista=new JScrollPane(opciones);
        lista.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        lista.setPreferredSize(new java.awt.Dimension(310,
            Math.min(340,Math.max(60,30*tabla.getModel().getColumnCount()))));
        menu.add(lista);
        JButton todas=new JButton("Mostrar todas");
        todas.addActionListener(e->{
            estado.ocultas=new LinkedHashSet<>();
            preferencias.remove(estado.clave);
            aplicar(tabla,estado);
            for(java.awt.Component componente:opciones.getComponents())
                if(componente instanceof JCheckBox check)check.setSelected(true);
        });
        menu.add(todas);
        return menu;
    }

    /** Información visible exclusivamente del JTable, sin alterar el modelo fiscal. */
    public List<String> visibles(JTable tabla){
        List<String> r=new ArrayList<>();
        TableColumnModel cm=tabla.getColumnModel();
        for(int i=0;i<cm.getColumnCount();i++)r.add(tabla.getModel().getColumnName(cm.getColumn(i).getModelIndex()));
        return List.copyOf(r);
    }

    private void aplicar(JTable tabla,Estado estado){
        TableColumnModel cm=tabla.getColumnModel();
        while(cm.getColumnCount()>0)cm.removeColumn(cm.getColumn(0));
        int presentes=0;
        for(int i=0;i<estado.modelo.getColumnCount();i++){
            if(estado.ocultas.contains(estado.modelo.getColumnName(i)))continue;
            TableColumn c=estado.columnas.get(i);
            if(c!=null){cm.addColumn(c);presentes++;}
        }
        if(presentes==0 && estado.modelo.getColumnCount()>0){
            estado.ocultas=new LinkedHashSet<>();
            preferencias.remove(estado.clave);
            for(int i=0;i<estado.modelo.getColumnCount();i++)cm.addColumn(estado.columnas.get(i));
        }
    }

    private static String serializar(Set<String> ocultas){
        return ocultas.stream().map(x->java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(x.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .collect(java.util.stream.Collectors.joining(","));
    }

    private Set<String> leer(String clave){
        String valor=preferencias.get(clave,"");
        Set<String> ocultas=new LinkedHashSet<>();
        if(valor.isEmpty())return ocultas;
        for(String token:valor.split(",")){
            try{
                ocultas.add(new String(java.util.Base64.getUrlDecoder().decode(token),
                    java.nio.charset.StandardCharsets.UTF_8));
            }catch(IllegalArgumentException ex){
                // Una preferencia local corrupta no debe impedir consultar CFDI.
                preferencias.remove(clave);
                return new LinkedHashSet<>();
            }
        }
        return ocultas;
    }

    private String clave(String seccion){
        if(seccion==null || !seccion.matches("[A-Z_]{1,32}"))
            throw new IllegalArgumentException("Sección de tabla inválida");
        return prefijo+seccion;
    }
}
