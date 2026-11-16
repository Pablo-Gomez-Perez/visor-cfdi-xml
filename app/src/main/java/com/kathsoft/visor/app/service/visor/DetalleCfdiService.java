package com.kathsoft.visor.app.service.visor;

import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.Permisos;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;

/**
 * RF-017: inspección de datos persistidos y XML original íntegro.
 * Las relaciones entrantes muestran únicamente documentos activos del mismo titular.
 */
public final class DetalleCfdiService {
    private static final int LIMITE=1000;
    private static final int MAX_XML=8*1024*1024;
    private final JpaContext jpa;

    /** Contexto compartido. */
    public DetalleCfdiService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}

    /** Sección tabular independiente; los valores nulos no se inventan. */
    public record Seccion(String nombre,List<String> columnas,List<List<Object>> filas,boolean limitada){
        public Seccion {
            columnas=List.copyOf(columnas);
            filas=filas.stream().map(f->Collections.unmodifiableList(new ArrayList<>(f))).toList();
        }
        @Override public String toString(){return nombre;}
    }

    /** Resultado de inspección; un original ausente no oculta datos persistidos. */
    public record Detalle(String uuid,List<Seccion> secciones,String xml,String avisoXml){
        public Detalle {secciones=List.copyOf(secciones);}
    }

    /** Consulta integral de un documento activo autorizado; sin mutaciones. */
    public Detalle consultar(Long actor,long titular,long documento){
        if(titular<=0||documento<=0)throw new IllegalArgumentException("Identificadores inválidos");
        return jpa.enTransaccion(em->{
            VisorCfdiService.autorizar(em,actor,titular,Permisos.CFDI_CONSULTAR_DETALLE);
            List<?> ids=em.createNativeQuery("""
                SELECT d.uuid FROM documento_fiscal d JOIN contribuyente_documento cd ON cd.documento_id=d.id
                WHERE d.id=:doc AND cd.contribuyente_id=:titular AND cd.archivado_en IS NULL
                """).setParameter("doc",documento).setParameter("titular",titular).getResultList();
            if(ids.size()!=1)throw new SecurityException("Documento inexistente, archivado o no autorizado");
            String uuid=ids.get(0).toString();
            List<Seccion> secciones=em.unwrap(Session.class).doReturningWork(c->{
                List<Seccion> s=new ArrayList<>();
                agregar(c,s,"Documento","SELECT d.*,cd.sentido FROM documento_fiscal d JOIN contribuyente_documento cd ON cd.documento_id=d.id WHERE d.id=? AND cd.contribuyente_id=?",documento,titular);
                agregar(c,s,"Encabezado CFDI","SELECT * FROM cfdi WHERE documento_id=?",documento);
                agregar(c,s,"Participantes","SELECT rol,rfc,nombre_razon_social,regimen_fiscal,domicilio_fiscal_cp,residencia_fiscal,num_registro_fiscal_extranjero,uso_cfdi FROM documento_participante WHERE documento_id=? ORDER BY rol",documento);
                agregar(c,s,"Timbrado","SELECT * FROM documento_timbre WHERE documento_id=?",documento);
                agregar(c,s,"Consultas SAT","SELECT estado,es_cancelable,estado_cancelacion,fecha_consulta,detalle_error FROM consulta_estado_sat WHERE documento_id=? ORDER BY fecha_consulta DESC,id DESC",documento);
                agregar(c,s,"Conceptos","SELECT * FROM cfdi_concepto WHERE documento_id=? ORDER BY ordinal",documento);
                agregar(c,s,"Impuestos de conceptos","SELECT c.ordinal AS concepto,i.movimiento,i.impuesto,i.tipo_factor,i.base,i.tasa_cuota,i.importe FROM cfdi_concepto_impuesto i JOIN cfdi_concepto c ON c.id=i.concepto_id WHERE c.documento_id=? ORDER BY c.ordinal,i.ordinal,i.id",documento);
                agregar(c,s,"Impuestos globales","SELECT movimiento,impuesto,tipo_factor,base,tasa_cuota,importe FROM cfdi_impuesto_global WHERE documento_id=? ORDER BY ordinal,id",documento);
                agregar(c,s,"Totales impuestos","SELECT * FROM cfdi_impuesto_totales WHERE documento_id=?",documento);
                agregar(c,s,"Información global","SELECT * FROM cfdi_informacion_global WHERE documento_id=?",documento);
                agregar(c,s,"Complementos","SELECT t.nombre,t.espacio_nombres,t.version FROM documento_complemento dc JOIN tipo_complemento t ON t.id=dc.tipo_complemento_id WHERE dc.documento_id=? ORDER BY t.nombre,t.id",documento);
                agregar(c,s,"Relaciones emitidas","SELECT g.tipo_relacion,u.uuid_relacionado FROM cfdi_relacion_grupo g JOIN cfdi_relacion_uuid u ON u.grupo_id=g.id WHERE g.documento_id=? ORDER BY g.ordinal,u.uuid_relacionado",documento);
                agregar(c,s,"Relaciones entrantes","SELECT d.uuid,g.tipo_relacion FROM cfdi_relacion_uuid u JOIN cfdi_relacion_grupo g ON g.id=u.grupo_id JOIN documento_fiscal d ON d.id=g.documento_id JOIN contribuyente_documento cd ON cd.documento_id=d.id AND cd.contribuyente_id=? AND cd.archivado_en IS NULL WHERE u.uuid_relacionado=? ORDER BY d.uuid,g.ordinal",titular,uuid);
                agregar(c,s,"Nodos Pago","SELECT * FROM pago_fiscal WHERE documento_id=? ORDER BY ordinal",documento);
                agregar(c,s,"Documentos pagados por este CFDI","SELECT p.ordinal AS pago,p.fecha_pago,pd.* FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE p.documento_id=? ORDER BY p.ordinal,pd.ordinal",documento);
                agregar(c,s,"REP que referencian este CFDI","SELECT d.uuid AS uuid_rep,p.fecha_pago,p.moneda AS moneda_pago,p.monto,pd.* FROM pago_fiscal_documento pd JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id JOIN documento_fiscal d ON d.id=p.documento_id JOIN contribuyente_documento cd ON cd.documento_id=d.id AND cd.contribuyente_id=? AND cd.archivado_en IS NULL WHERE pd.uuid_documento=? ORDER BY p.fecha_pago,p.id,pd.ordinal",titular,uuid);
                agregar(c,s,"Impuestos de pagos","SELECT p.ordinal AS pago,i.* FROM pago_fiscal_impuesto_pago i JOIN pago_fiscal p ON p.id=i.pago_fiscal_id WHERE p.documento_id=? ORDER BY p.ordinal,i.ordinal,i.id",documento);
                agregar(c,s,"Impuestos de documentos pagados","SELECT p.ordinal AS pago,pd.ordinal AS relacionado,i.* FROM pago_fiscal_impuesto_documento i JOIN pago_fiscal_documento pd ON pd.id=i.pago_documento_id JOIN pago_fiscal p ON p.id=pd.pago_fiscal_id WHERE p.documento_id=? ORDER BY p.ordinal,pd.ordinal,i.ordinal,i.id",documento);
                agregar(c,s,"Totales Pagos 2.0","SELECT * FROM pago_fiscal_totales WHERE documento_id=?",documento);
                agregar(c,s,"Totales impuestos Pagos 2.0","SELECT * FROM pago_fiscal_total_impuesto WHERE documento_id=? ORDER BY atributo",documento);
                agregar(c,s,"Versión Pagos","SELECT * FROM pago_fiscal_complemento WHERE documento_id=?",documento);
                agregar(c,s,"Nómina","SELECT * FROM nomina WHERE documento_id=?",documento);
                agregar(c,s,"Percepciones","SELECT * FROM nomina_percepcion WHERE documento_id=? ORDER BY ordinal",documento);
                agregar(c,s,"Deducciones","SELECT * FROM nomina_deduccion WHERE documento_id=? ORDER BY ordinal",documento);
                agregar(c,s,"Retenciones","SELECT * FROM retencion WHERE documento_id=?",documento);
                agregar(c,s,"Periodo retenciones","SELECT * FROM retencion_periodo WHERE documento_id=?",documento);
                agregar(c,s,"Totales retenciones","SELECT * FROM retencion_totales WHERE documento_id=?",documento);
                agregar(c,s,"Impuestos retenciones","SELECT * FROM retencion_impuesto WHERE documento_id=? ORDER BY ordinal",documento);
                agregar(c,s,"Relaciones retenciones","SELECT uuid_relacionado FROM retencion_relacion_uuid WHERE documento_id=? ORDER BY uuid_relacionado",documento);
                agregar(c,s,"Obligaciones registradas","SELECT naturaleza,moneda,monto_original,pagos_aplicados,ajustes_aplicados,saldo_calculado,estado_calculado FROM v_saldo_obligacion WHERE documento_id=? AND contribuyente_id=? ORDER BY obligacion_id",documento,titular);
                return s;
            });
            try{return new Detalle(uuid,secciones,original(em,documento),"XML original verificado por SHA-256; no equivale a validación SAT.");}
            catch(IllegalStateException ex){return new Detalle(uuid,secciones,"","Original no disponible o no íntegro: "+ex.getMessage());}
        });
    }

    private static void agregar(java.sql.Connection c,List<Seccion> destino,String nombre,String sql,Object... args)
            throws java.sql.SQLException {
        try(PreparedStatement p=c.prepareStatement(sql)){
            for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);
            p.setMaxRows(LIMITE+1);
            try(ResultSet r=p.executeQuery()){
                ResultSetMetaData m=r.getMetaData();
                List<String> cols=new ArrayList<>();
                for(int i=1;i<=m.getColumnCount();i++)cols.add(m.getColumnLabel(i));
                List<List<Object>> filas=new ArrayList<>();
                boolean limitada=false;
                while(r.next()){
                    if(filas.size()==LIMITE){limitada=true;break;}
                    List<Object> f=new ArrayList<>();
                    for(int i=1;i<=cols.size();i++)f.add(r.getObject(i));
                    filas.add(f);
                }
                if(!filas.isEmpty())destino.add(new Seccion(nombre,cols,filas,limitada));
            }
        }
    }

    /** Verifica límites, ruta canónica y hash de los bytes leídos antes de mostrarlos. */
    private static String original(EntityManager em,long documento){
        List<Object[]> rs=VisorCfdiService.filas(em.createNativeQuery("""
            SELECT rl.ruta_base,a.clave_objeto,HEX(a.sha256),a.tamano_bytes
            FROM documento_archivo da JOIN archivo_documental a ON a.id=da.archivo_id
            JOIN repositorio_documental rd ON rd.id=a.repositorio_id
            JOIN repositorio_local rl ON rl.repositorio_id=rd.id
            WHERE da.documento_id=:doc AND da.tipo_uso='ORIGINAL' AND rd.proveedor='LOCAL'
            """).setParameter("doc",documento));
        if(rs.size()!=1)throw new IllegalStateException("Se requiere un original local único");
        Object[] f=rs.get(0);
        String sha=f[2].toString().toLowerCase(Locale.ROOT);
        if(!sha.matches("[0-9a-f]{64}")||!f[1].equals("cfdi/"+sha.substring(0,2)+"/"+sha+".xml"))
            throw new IllegalStateException("Metadatos inconsistentes");
        long bytes=((Number)f[3]).longValue();
        if(bytes<=0||bytes>MAX_XML)throw new IllegalStateException("Tamaño fuera de límite");
        try{
            Path raiz=Path.of(f[0].toString()).toRealPath();
            Path ruta=raiz.resolve(f[1].toString()).normalize();
            if(!ruta.startsWith(raiz)||!ruta.toRealPath().equals(ruta)||!Files.isRegularFile(ruta,LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Ruta no segura");
            byte[] contenido;
            try(var in=Files.newInputStream(ruta,LinkOption.NOFOLLOW_LINKS)){
                contenido=in.readNBytes(MAX_XML+1);
            }
            if(contenido.length!=bytes||!sha.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido))))
                throw new IllegalStateException("SHA-256 o tamaño no coinciden");
            // Detectar codificación XML con StAX seguro; no resolver DTD ni entidades externas.
            var factory=javax.xml.stream.XMLInputFactory.newFactory();
            factory.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD,false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
            var lector=factory.createXMLStreamReader(new ByteArrayInputStream(contenido));
            String encoding;
            try{encoding=lector.getEncoding();}finally{lector.close();}
            return new String(contenido,encoding==null?java.nio.charset.StandardCharsets.UTF_8:java.nio.charset.Charset.forName(encoding));
        }catch(IOException|NoSuchAlgorithmException|javax.xml.stream.XMLStreamException|IllegalArgumentException ex){
            throw new IllegalStateException("Error de lectura o codificación del original",ex);
        }
    }
}
