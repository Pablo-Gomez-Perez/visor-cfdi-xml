package com.kathsoft.visor.app.service.visor;

import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * RF-015/016: visor de solo lectura. Una fila por UUID y titular activo.
 * Los importes originales no son determinaciones fiscales ni se convierten de moneda.
 */
public final class VisorCfdiService {
    public static final int TAMANO_PAGINA=100;
    private final JpaContext jpa;

    /** Contexto de persistencia compartido. */
    public VisorCfdiService(JpaContext jpa) { this.jpa=Objects.requireNonNull(jpa); }

    /** Titular autorizado. */
    public record Titular(long id,String rfc,String nombre) {
        @Override public String toString(){return rfc+" — "+nombre;}
    }

    /** Columnas habilitadas: SQL fijo, no texto de usuario. */
    public enum Columna {
        UUID("UUID","uuid"), TIPO("Tipo","tipo"), SENTIDO("Sentido","sentido"),
        RFC_EMISOR("RFC emisor","rfc_emisor"), EMISOR("Emisor","emisor"),
        RFC_RECEPTOR("RFC receptor","rfc_receptor"), RECEPTOR("Receptor","receptor"),
        SERIE("Serie","serie"), FOLIO("Folio","folio"), EMISION("Emisión","emision"),
        TIMBRADO("Timbrado","timbrado"), REGISTRO("Registro","registro"),
        SUBTOTAL("Subtotal","subtotal"), DESCUENTO("Descuento","descuento"),
        TRASLADOS("Traslados","traslados"), RETENCIONES("Retenciones","retenciones"),
        TOTAL("Total CFDI","total"), MONEDA("Moneda","moneda"), METODO("Método","metodo"),
        FORMA("Forma","forma"), SAT("Último estado SAT","estado_sat"),
        FECHA_SAT("Consulta SAT","fecha_sat"), PAGO("Estado pago registrado","estado_pago"),
        VERSION("Versión","version_estandar"), CLAVE_RETENCION("Clave retención","clave_retencion"),
        MONTO_RETENCION("Monto operación ret.","monto_retencion"),
        RETENIDO_RETENCION("Monto retenido ret.","retenido_retencion"),
        TOTAL_REP("Total Pagos 2.0 (MXN)","total_rep"), IVA16("IVA traslado 16%","iva16"), IEPS3("IEPS traslado 3%","ieps3");
        private final String etiqueta,sql;
        Columna(String etiqueta,String sql){this.etiqueta=etiqueta;this.sql=sql;}
        @Override public String toString(){return etiqueta;}
    }

    /** Fecha documental usada por el intervalo inclusivo. */
    public enum Fecha { EMISION("emision"), TIMBRADO("timbrado"), REGISTRO("registro");
        private final String sql;
        Fecha(String sql){this.sql=sql;}
    }

    /** Filtro inmutable; cadenas vacías representan todos. */
    public record Filtro(long titular,LocalDate desde,LocalDate hasta,Fecha fecha,
            String uuid,String tipo,String sentido,String rfc,String emisor,String receptor,
            BigDecimal minimo,BigDecimal maximo,String moneda,String sat,String pago,String metodo) {
        public Filtro {
            if(titular<=0) throw new IllegalArgumentException("Seleccione contribuyente");
            Objects.requireNonNull(desde);Objects.requireNonNull(hasta);Objects.requireNonNull(fecha);
            if(desde.isAfter(hasta)||desde.getYear()<1000||hasta.getYear()>9998)
                throw new IllegalArgumentException("Intervalo inválido");
            uuid=limpiar(uuid,36);rfc=limpiar(rfc,13);emisor=limpiar(emisor,300);receptor=limpiar(receptor,300);
            tipo=opcion(tipo,"I","E","T","N","P","RETENCION");
            sentido=opcion(sentido,"EMITIDO","RECIBIDO","AMBOS");
            sat=opcion(sat,"VIGENTE","CANCELADO","NO_ENCONTRADO","DESCONOCIDO","ERROR","SIN_CONSULTA");
            pago=opcion(pago,"REP_SIN_CONCILIAR","SIN_REGISTRO","LIQUIDADO","PENDIENTE","PARCIAL","SOBREAPLICADO","MIXTO");
            metodo=opcion(metodo,"PUE","PPD","PIP");
            moneda=limpiar(moneda,3).toUpperCase(Locale.ROOT);
            if(!moneda.isEmpty()&&!moneda.matches("[A-Z]{3}"))throw new IllegalArgumentException("Moneda inválida");
            if(!uuid.matches("[0-9a-fA-F-]*")||!rfc.matches("[a-zA-Z0-9&Ññ]*"))
                throw new IllegalArgumentException("UUID o RFC inválido");
            if((minimo!=null&&minimo.signum()<0)||(maximo!=null&&maximo.signum()<0)
                ||minimo!=null&&maximo!=null&&minimo.compareTo(maximo)>0)
                throw new IllegalArgumentException("Importes inválidos");
        }
        private static String limpiar(String s,int n){
            s=s==null?"":s.strip();
            if(s.length()>n)throw new IllegalArgumentException("Filtro demasiado largo");
            return s;
        }
        private static String opcion(String s,String... valores){
            s=limpiar(s,30);
            if(!s.isEmpty()&&!Arrays.asList(valores).contains(s))throw new IllegalArgumentException("Opción inválida");
            return s;
        }
    }

    /** Orden global (antes de paginar). */
    public record Orden(Columna columna,boolean ascendente) {
        public Orden {Objects.requireNonNull(columna);}
    }

    /** Valores JDBC en el orden del catálogo Columna; null conserva ausencia fiscal. */
    public record Fila(long id,List<Object> valores) {
        public Fila {valores=Collections.unmodifiableList(new ArrayList<>(valores));}
        public String uuid(){return String.valueOf(valores.get(Columna.UUID.ordinal()));}
    }

    /** Página acotada; no se consulta COUNT global solo para navegar. */
    public record Pagina<T>(List<T> filas,boolean siguiente) {
        public Pagina {filas=List.copyOf(filas);}
    }

    /** Resumen por RFC emisor, moneda y tipo, sin mezclar familias ni divisas. */
    public record Grupo(String rfc,String moneda,String tipo,long documentos,
            BigDecimal subtotal,BigDecimal descuento,BigDecimal traslados,BigDecimal retenciones,
            BigDecimal total,BigDecimal operacionRetencion,BigDecimal retenidoRetencion,BigDecimal pagosMxn) {}

    private static final String BASE="""
        SELECT d.id,d.uuid,CASE WHEN d.clase='RETENCION' THEN 'RETENCION' ELSE c.tipo END AS tipo,
            cd.sentido,emi.rfc AS rfc_emisor,emi.nombre_razon_social AS emisor,
            rec.rfc AS rfc_receptor,rec.nombre_razon_social AS receptor,
            c.serie,COALESCE(c.folio,r.folio_interno) AS folio,
            d.fecha_emision AS emision,t.fecha_timbrado AS timbrado,d.registrado_en AS registro,
            c.subtotal,c.descuento,it.total_trasladados AS traslados,it.total_retenidos AS retenciones,
            c.total,c.moneda,c.metodo_pago AS metodo,c.forma_pago AS forma,
            COALESCE(s.estado,'SIN_CONSULTA') AS estado_sat,s.fecha_consulta AS fecha_sat,
            (SELECT CASE WHEN COUNT(*)=0 THEN CASE WHEN EXISTS (SELECT 1 FROM pago_fiscal_documento pd
                    JOIN pago_fiscal pf ON pf.id=pd.pago_fiscal_id
                    JOIN contribuyente_documento cp ON cp.documento_id=pf.documento_id
                        AND cp.contribuyente_id=cd.contribuyente_id AND cp.archivado_en IS NULL
                    WHERE pd.uuid_documento=d.uuid) THEN 'REP_SIN_CONCILIAR' ELSE 'SIN_REGISTRO' END
                 WHEN COUNT(DISTINCT v.estado_calculado)=1 THEN MIN(v.estado_calculado)
                 ELSE 'MIXTO' END FROM v_saldo_obligacion v
                 WHERE v.contribuyente_id=cd.contribuyente_id AND v.documento_id=d.id) AS estado_pago,
            d.version_estandar,r.clave_retencion,rt.monto_total_operacion AS monto_retencion,
            rt.monto_total_retenido AS retenido_retencion,pt.monto_total_pagos AS total_rep,
            (SELECT SUM(ig.importe) FROM cfdi_impuesto_global ig WHERE ig.documento_id=d.id AND ig.movimiento='TRASLADO' AND ig.impuesto='002' AND ig.tipo_factor='Tasa' AND ig.tasa_cuota=0.160000) AS iva16,
            (SELECT SUM(ig.importe) FROM cfdi_impuesto_global ig WHERE ig.documento_id=d.id AND ig.movimiento='TRASLADO' AND ig.impuesto='003' AND ig.tipo_factor='Tasa' AND ig.tasa_cuota=0.030000) AS ieps3
        FROM contribuyente_documento cd JOIN documento_fiscal d ON d.id=cd.documento_id
        JOIN usuario_contribuyente uc ON uc.contribuyente_id=cd.contribuyente_id
        LEFT JOIN cfdi c ON c.documento_id=d.id
        LEFT JOIN retencion r ON r.documento_id=d.id
        LEFT JOIN retencion_totales rt ON rt.documento_id=d.id
        LEFT JOIN cfdi_impuesto_totales it ON it.documento_id=d.id
        LEFT JOIN pago_fiscal_totales pt ON pt.documento_id=d.id
        LEFT JOIN documento_timbre t ON t.documento_id=d.id
        LEFT JOIN documento_participante emi ON emi.documento_id=d.id AND emi.rol='E'
        LEFT JOIN documento_participante rec ON rec.documento_id=d.id AND rec.rol='R'
        LEFT JOIN consulta_estado_sat s ON s.id=(SELECT ss.id FROM consulta_estado_sat ss
          WHERE ss.documento_id=d.id ORDER BY ss.fecha_consulta DESC,ss.id DESC LIMIT 1)
        WHERE cd.contribuyente_id=:titular AND uc.usuario_id=:actor AND cd.archivado_en IS NULL
        """;

    /** Titulares con asignación explícita, incluido ADMIN. */
    public List<Titular> titulares(Long actor){
        return jpa.consultar(em->{
            AutorizacionService.exigir(em,actor,Permisos.CFDI_CONSULTAR);
            return filas(em.createNativeQuery("""
                SELECT c.id,c.rfc,c.nombre_razon_social FROM contribuyente c
                JOIN usuario_contribuyente uc ON uc.contribuyente_id=c.id
                WHERE uc.usuario_id=:actor ORDER BY c.nombre_razon_social,c.id
                """).setParameter("actor",actor)).stream()
                .map(f->new Titular(((Number)f[0]).longValue(),texto(f[1]),texto(f[2]))).toList();
        });
    }

    /** Consulta avanzada con orden SQL estable y páginas de 100 documentos. */
    public Pagina<Fila> consultar(Long actor,Filtro filtro,Orden orden,int pagina){
        Objects.requireNonNull(filtro);Objects.requireNonNull(orden);
        int offset=offset(pagina);
        return jpa.consultar(em->{
            autorizar(em,actor,filtro.titular(),Permisos.CFDI_CONSULTAR);
            String sql="SELECT x.* FROM ("+BASE+") x "+condiciones(filtro)
                +" ORDER BY x."+orden.columna().sql+(orden.ascendente()?" ASC":" DESC")+",x.id ASC";
            Query q=parametros(em.createNativeQuery(sql),actor,filtro).setFirstResult(offset).setMaxResults(TAMANO_PAGINA+1);
            List<Fila> datos=filas(q).stream().map(VisorCfdiService::mapear).toList();
            return pagina(datos);
        });
    }

    /** Agrupa el universo filtrado, no exclusivamente la página visible. */
    public Pagina<Grupo> agrupar(Long actor,Filtro filtro,int pagina){
        Objects.requireNonNull(filtro);
        int offset=offset(pagina);
        return jpa.consultar(em->{
            autorizar(em,actor,filtro.titular(),Permisos.CFDI_CONSULTAR);
            String sql="""
                SELECT x.rfc_emisor,x.moneda,x.tipo,COUNT(*),SUM(x.subtotal),SUM(x.descuento),
                    SUM(x.traslados),SUM(x.retenciones),SUM(x.total),CASE WHEN x.moneda IS NOT NULL THEN SUM(x.monto_retencion) END,
                    CASE WHEN x.moneda IS NOT NULL THEN SUM(x.retenido_retencion) END,SUM(x.total_rep)
                FROM (
                """+BASE+") x "+condiciones(filtro)
                +" GROUP BY x.rfc_emisor,x.moneda,x.tipo ORDER BY x.rfc_emisor,x.moneda,x.tipo";
            List<Grupo> datos=filas(parametros(em.createNativeQuery(sql),actor,filtro)
                .setFirstResult(offset).setMaxResults(TAMANO_PAGINA+1)).stream()
                .map(f->new Grupo(texto(f[0]),texto(f[1]),texto(f[2]),((Number)f[3]).longValue(),
                    decimal(f[4]),decimal(f[5]),decimal(f[6]),decimal(f[7]),decimal(f[8]),
                    decimal(f[9]),decimal(f[10]),decimal(f[11]))).toList();
            return pagina(datos);
        });
    }

    private static String condiciones(Filtro f){
        StringBuilder s=new StringBuilder("WHERE x."+f.fecha().sql+">=:desde AND x."+f.fecha().sql+"<:hasta");
        if(!f.uuid().isEmpty())s.append(" AND x.uuid LIKE :uuid");
        if(!f.tipo().isEmpty())s.append(" AND x.tipo=:tipo");
        if(!f.sentido().isEmpty())s.append(" AND x.sentido IN (:sentido,'AMBOS')");
        if("AMBOS".equals(f.sentido()))s.append(" AND x.sentido='AMBOS'");
        if(!f.rfc().isEmpty())s.append(" AND (x.rfc_emisor LIKE :rfc ESCAPE '!' OR x.rfc_receptor LIKE :rfc ESCAPE '!')");
        if(!f.emisor().isEmpty())s.append(" AND (x.emisor LIKE :emisor ESCAPE '!' OR x.rfc_emisor LIKE :emisor ESCAPE '!')");
        if(!f.receptor().isEmpty())s.append(" AND (x.receptor LIKE :receptor ESCAPE '!' OR x.rfc_receptor LIKE :receptor ESCAPE '!')");
        if(f.minimo()!=null)s.append(" AND COALESCE(x.total,x.monto_retencion)>=:minimo");
        if(f.maximo()!=null)s.append(" AND COALESCE(x.total,x.monto_retencion)<=:maximo");
        if(!f.moneda().isEmpty())s.append(" AND x.moneda=:moneda");
        if(!f.sat().isEmpty())s.append(" AND x.estado_sat=:sat");
        if(!f.pago().isEmpty())s.append(" AND x.estado_pago=:pago");
        if(!f.metodo().isEmpty())s.append(" AND x.metodo=:metodo");
        return s.toString();
    }

    private static Query parametros(Query q,Long actor,Filtro f){
        q.setParameter("actor",actor).setParameter("titular",f.titular())
            .setParameter("desde",Timestamp.valueOf(f.desde().atStartOfDay()))
            .setParameter("hasta",Timestamp.valueOf(f.hasta().plusDays(1).atStartOfDay()));
        if(!f.uuid().isEmpty())q.setParameter("uuid",f.uuid().toUpperCase(Locale.ROOT)+"%");
        if(!f.tipo().isEmpty())q.setParameter("tipo",f.tipo());
        if(!f.sentido().isEmpty())q.setParameter("sentido",f.sentido());
        if(!f.rfc().isEmpty())q.setParameter("rfc",escapar(f.rfc())+"%");
        if(!f.emisor().isEmpty())q.setParameter("emisor","%"+escapar(f.emisor())+"%");
        if(!f.receptor().isEmpty())q.setParameter("receptor","%"+escapar(f.receptor())+"%");
        if(f.minimo()!=null)q.setParameter("minimo",f.minimo());
        if(f.maximo()!=null)q.setParameter("maximo",f.maximo());
        if(!f.moneda().isEmpty())q.setParameter("moneda",f.moneda());
        if(!f.sat().isEmpty())q.setParameter("sat",f.sat());
        if(!f.pago().isEmpty())q.setParameter("pago",f.pago());
        if(!f.metodo().isEmpty())q.setParameter("metodo",f.metodo());
        return q;
    }

    static String escapar(String s){return s.replace("!","!!").replace("%","!%").replace("_","!_");}

    static void autorizar(EntityManager em,Long actor,long titular,String permiso){
        AutorizacionService.exigir(em,actor,Permisos.CFDI_CONSULTAR);
        if(!Permisos.CFDI_CONSULTAR.equals(permiso))AutorizacionService.exigir(em,actor,permiso);
        Number n=(Number)em.createNativeQuery("SELECT COUNT(*) FROM usuario_contribuyente WHERE usuario_id=:actor AND contribuyente_id=:titular")
            .setParameter("actor",actor).setParameter("titular",titular).getSingleResult();
        if(n.longValue()==0)throw new SecurityException("Contribuyente no autorizado");
    }

    static Fila mapear(Object[] fila){
        if(fila.length!=Columna.values().length+1)throw new IllegalStateException("Columnas inesperadas");
        List<Object> valores=new ArrayList<>();
        for(int i=1;i<fila.length;i++){
            Object v=fila[i];
            if(v instanceof Timestamp t)v=t.toLocalDateTime();
            if(v instanceof Character c)v=c.toString();
            valores.add(v);
        }
        return new Fila(((Number)fila[0]).longValue(),valores);
    }

    @SuppressWarnings("unchecked")
    static List<Object[]> filas(Query q){return q.getResultList();}
    static String texto(Object o){return o==null?null:o.toString();}
    private static BigDecimal decimal(Object o){return o==null?null:(BigDecimal)o;}
    static int offset(int pagina){
        if(pagina<0||pagina>Integer.MAX_VALUE/TAMANO_PAGINA)throw new IllegalArgumentException("Página inválida");
        return pagina*TAMANO_PAGINA;
    }
    private static <T> Pagina<T> pagina(List<T> datos){
        return new Pagina<>(datos.subList(0,Math.min(TAMANO_PAGINA,datos.size())),datos.size()>TAMANO_PAGINA);
    }
}
