package com.kathsoft.visor.app.service.operacion;

import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * RF-020: clasificación administrativa general por titular y documento.
 * Cada cambio añade un evento; no altera XML, conceptos, impuestos ni periodos.
 */
public final class ClasificacionOperacionesService {
    public static final int PAGINA=100;
    private final JpaContext jpa;

    /** Contexto de persistencia de la aplicación. */
    public ClasificacionOperacionesService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}
    /** @return clasificación por concepto sobre el mismo contexto transaccional */
    public com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService detalleConceptos() { return new com.kathsoft.visor.app.service.fiscal.ClasificacionDetalleService(jpa); }
    public enum Naturaleza { GASTO,COSTO,GASTO_FINANCIERO,INVERSION,INGRESO,OTRA }
    public record Titular(long id,String rfc,String nombre){
        @Override public String toString(){return rfc+" — "+nombre;}
    }
    public record Categoria(long id,String clave,String nombre,Naturaleza naturaleza,boolean activa){
        @Override public String toString(){return clave+" — "+nombre+(activa?"":" (inactiva)");}
    }
    /** Categoria -1: todas, 0: sin asignar, >0: categoría concreta. */
    public record Filtro(long titular,LocalDate desde,LocalDate hasta,String sentido,String rfc,long categoria){
        public Filtro{
            Objects.requireNonNull(desde);Objects.requireNonNull(hasta);
            if(titular<=0||desde.isAfter(hasta)||desde.getYear()<1000||hasta.getYear()>9998||categoria< -1)
                throw new IllegalArgumentException("Filtro inválido");
            sentido=sentido==null?"":sentido;
            if(!Set.of("","EMITIDO","RECIBIDO","AMBOS").contains(sentido))throw new IllegalArgumentException("Sentido inválido");
            rfc=rfc==null?"":rfc.strip().toUpperCase(Locale.ROOT);
            if(!rfc.matches("[A-Z0-9Ñ&]{0,13}"))throw new IllegalArgumentException("RFC inválido");
        }
    }
    /** Una fila por documento; revisión 0 significa que nunca fue clasificado. */
    public record Operacion(long id,String uuid,String tipo,String sentido,LocalDateTime emision,String rfcEmisor,
        String emisor,String rfcReceptor,String receptor,BigDecimal subtotal,BigDecimal descuento,BigDecimal total,
        String moneda,String estadoSat,long revision,Long categoriaId,String categoria,String naturaleza){}
    public record Pagina<T>(List<T> filas,boolean siguiente){
        public Pagina{filas=List.copyOf(filas);}
    }
    /** Revision visible al usuario; evita sobrescribir el trabajo de otro operador. */
    public record Seleccion(long documento,long revision){
        public Seleccion{if(documento<=0||revision<0)throw new IllegalArgumentException("Selección inválida");}
    }
    public record Evento(long id,LocalDateTime fecha,String usuario,String categoria,String naturaleza,String motivo){}

    /** Lista contribuyentes asignados; no existe excepción para ADMIN. */
    public List<Titular> titulares(Long actor){
        return jpa.consultar(em->{
            AutorizacionService.exigir(em,actor,Permisos.OPERACIONES_CONSULTAR);
            return filas(em.createNativeQuery("""
                SELECT c.id,c.rfc,c.nombre_razon_social FROM contribuyente c
                JOIN usuario_contribuyente uc ON uc.contribuyente_id=c.id
                WHERE uc.usuario_id=:actor ORDER BY c.nombre_razon_social,c.id
                """).setParameter("actor",actor)).stream()
                .map(f->new Titular(numero(f[0]),texto(f[1]),texto(f[2]))).toList();
        });
    }

    /** Catálogo del titular, incluidas categorías inactivas para consulta histórica. */
    public List<Categoria> categorias(Long actor,long titular){
        return jpa.consultar(em->{
            autorizar(em,actor,titular,Permisos.OPERACIONES_CONSULTAR);
            return filas(em.createNativeQuery("""
                SELECT id,clave,nombre,naturaleza,activo FROM categoria_operacion
                WHERE contribuyente_id=:t ORDER BY naturaleza,clave,id
                """).setParameter("t",titular)).stream()
                .map(f->new Categoria(numero(f[0]),texto(f[1]),texto(f[2]),Naturaleza.valueOf(texto(f[3])),activo(f[4]))).toList();
        });
    }

    /** Alta con clave estable e identidad propia del contribuyente. */
    public void crearCategoria(Long actor,long titular,String clave,String nombre,Naturaleza naturaleza){
        String k=validarClave(clave),n=obligatorio(nombre,150);
        Objects.requireNonNull(naturaleza);
        jpa.enTransaccion(em->{
            autorizar(em,actor,titular,Permisos.OPERACIONES_GESTIONAR_CATEGORIAS);
            bloquearTitular(em,titular);
            insertarCategoria(em,actor,titular,k,n,naturaleza,false);
            return null;
        });
    }

    /** Carga explícita e idempotente de las categorías iniciales, sin reemplazar las existentes. */
    public int crearPredeterminadas(Long actor,long titular){
        return jpa.enTransaccion(em->{
            autorizar(em,actor,titular,Permisos.OPERACIONES_GESTIONAR_CATEGORIAS);
            bloquearTitular(em,titular);
            int creadas=0;
            for(Naturaleza n:Naturaleza.values()){
                String nombre=switch(n){
                    case GASTO->"Gasto";case COSTO->"Costo";case GASTO_FINANCIERO->"Gasto financiero";
                    case INVERSION->"Inversión";case INGRESO->"Ingreso";case OTRA->"Otra operación";
                };
                if(insertarCategoria(em,actor,titular,n.name(),nombre,n,true))creadas++;
            }
            return creadas;
        });
    }

    /** Desactiva o reactiva; no borra categorías ni cambia su significado histórico. */
    public boolean cambiarEstadoCategoria(Long actor,long titular,long categoria,boolean nueva){
        return jpa.enTransaccion(em->{
            autorizar(em,actor,titular,Permisos.OPERACIONES_GESTIONAR_CATEGORIAS);
            boolean anterior=estadoCategoria(em,titular,categoria);
            if(anterior==nueva)return false;
            em.createNativeQuery("UPDATE categoria_operacion SET activo=:a WHERE id=:id AND contribuyente_id=:t")
                .setParameter("a",nueva?1:0).setParameter("id",categoria).setParameter("t",titular).executeUpdate();
            auditarCategoria(em,actor,titular,categoria,nueva?"REACTIVAR":"DESACTIVAR");
            return true;
        });
    }

    private static void bloquearTitular(EntityManager em,long titular){
        em.createNativeQuery("SELECT id FROM contribuyente WHERE id=:t FOR UPDATE").setParameter("t",titular).getSingleResult();
    }
    private static boolean insertarCategoria(EntityManager em,Long actor,long titular,String clave,String nombre,
        Naturaleza naturaleza,boolean omitirExistente){
        List<?> existentes=em.createNativeQuery("SELECT id FROM categoria_operacion WHERE contribuyente_id=:t AND clave=:k FOR UPDATE")
            .setParameter("t",titular).setParameter("k",clave).getResultList();
        if(!existentes.isEmpty()){
            if(omitirExistente)return false;
            throw new IllegalArgumentException("La clave de categoría ya existe");
        }
        em.createNativeQuery("INSERT INTO categoria_operacion(contribuyente_id,clave,nombre,naturaleza) VALUES (:t,:k,:n,:nat)")
            .setParameter("t",titular).setParameter("k",clave).setParameter("n",nombre).setParameter("nat",naturaleza.name()).executeUpdate();
        long id=numero(em.createNativeQuery("SELECT id FROM categoria_operacion WHERE contribuyente_id=:t AND clave=:k")
            .setParameter("t",titular).setParameter("k",clave).getSingleResult());
        auditarCategoria(em,actor,titular,id,"CREAR");
        return true;
    }
    private static void auditarCategoria(EntityManager em,Long actor,long titular,long id,String accion){
        em.createNativeQuery("""
            INSERT INTO bitacora_evento(usuario_id,contribuyente_id,entidad,entidad_id,accion,motivo)
            VALUES (:u,:t,'CATEGORIA_OPERACION',:id,:accion,'RF-020')
            """).setParameter("u",actor).setParameter("t",titular).setParameter("id",id).setParameter("accion",accion).executeUpdate();
    }
    private static boolean estadoCategoria(EntityManager em,long titular,long id){
        List<?> r=em.createNativeQuery("SELECT activo FROM categoria_operacion WHERE contribuyente_id=:t AND id=:id FOR UPDATE")
            .setParameter("t",titular).setParameter("id",id).getResultList();
        if(r.size()!=1)throw new IllegalArgumentException("Categoría no disponible para este contribuyente");
        return activo(r.get(0));
    }

    /** Consulta histórica por emisión, sin vincular la clasificación a un mes. */
    public Pagina<Operacion> consultar(Long actor,Filtro f,int pagina){
        Objects.requireNonNull(f);
        return jpa.consultar(em->{
            autorizar(em,actor,f.titular(),Permisos.OPERACIONES_CONSULTAR);
            Query q=parametrosFiltro(em,"""
                SELECT d.id,d.uuid,c.tipo,cd.sentido,d.fecha_emision,e.rfc,e.nombre_razon_social,r.rfc,r.nombre_razon_social,
                    c.subtotal,c.descuento,c.total,c.moneda,COALESCE(s.estado,'SIN_CONSULTA'),
                    COALESCE(h.id,0),h.categoria_id,cat.nombre,cat.naturaleza
                """+universo(f)+" ORDER BY d.fecha_emision DESC,d.id DESC",f);
            List<Operacion> datos=filas(q.setFirstResult(offset(pagina)).setMaxResults(PAGINA+1)).stream()
                .map(x->new Operacion(numero(x[0]),texto(x[1]),texto(x[2]),texto(x[3]),fecha(x[4]),
                    texto(x[5]),texto(x[6]),texto(x[7]),texto(x[8]),(BigDecimal)x[9],(BigDecimal)x[10],(BigDecimal)x[11],
                    texto(x[12]),texto(x[13]),numero(x[14]),x[15]==null?null:numero(x[15]),texto(x[16]),texto(x[17]))).toList();
            return pagina(datos);
        });
    }

    /**
     * Universo compartido por listado y resumen: mismos filtros, titular,
     * estado de archivado y última revisión. Sin paginación aquí.
     */
    private static String universo(Filtro f){
        String sql="""
                FROM contribuyente_documento cd JOIN documento_fiscal d ON d.id=cd.documento_id
                JOIN cfdi c ON c.documento_id=d.id
                LEFT JOIN documento_participante e ON e.documento_id=d.id AND e.rol='E'
                LEFT JOIN documento_participante r ON r.documento_id=d.id AND r.rol='R'
                LEFT JOIN consulta_estado_sat s ON s.id=(SELECT ss.id FROM consulta_estado_sat ss
                    WHERE ss.documento_id=d.id ORDER BY ss.fecha_consulta DESC,ss.id DESC LIMIT 1)
                LEFT JOIN clasificacion_operacion_evento h ON h.id=(SELECT hh.id FROM clasificacion_operacion_evento hh
                    WHERE hh.contribuyente_id=cd.contribuyente_id AND hh.documento_id=d.id ORDER BY hh.id DESC LIMIT 1)
                LEFT JOIN categoria_operacion cat ON cat.id=h.categoria_id AND cat.contribuyente_id=cd.contribuyente_id
                WHERE cd.contribuyente_id=:t AND cd.archivado_en IS NULL AND c.tipo IN ('I','E','N')
                    AND d.fecha_emision>=CAST(:desde AS DATETIME) AND d.fecha_emision<CAST(:hasta AS DATETIME)

                """;
        if(!f.sentido().isEmpty())sql+=" AND (cd.sentido=:sentido OR cd.sentido='AMBOS')";
        if(!f.rfc().isEmpty())sql+=" AND (e.rfc LIKE :rfc OR r.rfc LIKE :rfc)";
        if(f.categoria()==0)sql+=" AND h.categoria_id IS NULL";
        if(f.categoria()>0)sql+=" AND h.categoria_id=:categoria";
        return sql;
    }

    private static Query parametrosFiltro(EntityManager em,String sql,Filtro f){
        Query q=em.createNativeQuery(sql)
            .setParameter("t",f.titular()).setParameter("desde",f.desde().atStartOfDay().toString().replace('T',' '))
            .setParameter("hasta",f.hasta().plusDays(1).atStartOfDay().toString().replace('T',' '));
        if(sql.contains(":documento"))q.setParameter("documento",0L);
        if(!f.sentido().isEmpty())q.setParameter("sentido",f.sentido());
        if(!f.rfc().isEmpty())q.setParameter("rfc",f.rfc()+"%");
        if(f.categoria()>0)q.setParameter("categoria",f.categoria());
        return q;
    }

    /**
     * Totales documentales por naturaleza, divisa, sentido, tipo CFDI y
     * último estado SAT registrado. No constituyen determinación tributaria.
     * Las bases declaradas no se fuerzan a coincidir con subtotal menos descuento.
     */
    public record GrupoResumen(String naturaleza,String moneda,String sentido,String tipo,String estadoSat,
        long documentos,BigDecimal subtotalNeto,BigDecimal exento,BigDecimal tasa0,BigDecimal tasa16,
        BigDecimal otrasTasas,BigDecimal noObjeto,BigDecimal sinIvaDesglosado,BigDecimal revisar,
        BigDecimal sinConceptos,BigDecimal ivaTrasladado,BigDecimal diferencia){}

    /** Conteos del universo completo, incluidos los documentos sin categoría. */
    public record Resumen(long operaciones,long clasificadas,List<GrupoResumen> grupos){
        public Resumen{grupos=List.copyOf(grupos);}
        public long pendientes(){return operaciones-clasificadas;}
    }

    /**
     * Agrupa sobre todo el universo de búsqueda, no las 100 filas visibles.
     * Primero reduce impuestos a un registro por concepto y luego a uno por
     * documento; evita multiplicar el subtotal por las líneas de impuestos.
     * Conserva el IVA declarado incluso si la base requiere revisión.
     */
    public Resumen resumir(Long actor,Filtro f){
        Objects.requireNonNull(f);
        return jpa.enTransaccion(em->{
            autorizar(em,actor,f.titular(),Permisos.OPERACIONES_CONSULTAR);
            String seleccion="SELECT d.id documento_id,h.categoria_id "+universo(f);
            return com.kathsoft.visor.app.service.fiscal.ResumenClasificacionService.resumir(
                em,seleccion,sql->parametrosFiltro(em,sql,f));
        });
    }

    /**
     * Clasifica hasta 100 documentos en una transacción. Categoria null retira la
     * asignación. Requiere motivo y revisión de cada selección. No registra no-ops.
     */
    public int clasificar(Long actor,long titular,List<Seleccion> seleccion,Long categoria,String motivo){
        String m=obligatorio(motivo,600);
        List<Seleccion> orden=List.copyOf(seleccion).stream().sorted(Comparator.comparingLong(Seleccion::documento)).toList();
        if(orden.isEmpty()||orden.size()>PAGINA||orden.stream().map(Seleccion::documento).distinct().count()!=orden.size())
            throw new IllegalArgumentException("Seleccione entre 1 y 100 documentos distintos");
        return jpa.enTransaccion(em->{
            autorizar(em,actor,titular,Permisos.OPERACIONES_CLASIFICAR);
            if(categoria!=null&&!estadoCategoria(em,titular,categoria))throw new IllegalArgumentException("Categoría inactiva");
            int cambios=0;
            for(Seleccion s:orden){
                exigirDocumento(em,titular,s.documento(),true);
                List<Object[]> anterior=filas(em.createNativeQuery("""
                    SELECT id,categoria_id FROM clasificacion_operacion_evento
                    WHERE contribuyente_id=:t AND documento_id=:d ORDER BY id DESC LIMIT 1 FOR UPDATE
                    """).setParameter("t",titular).setParameter("d",s.documento()));
                long rev=anterior.isEmpty()?0:numero(anterior.get(0)[0]);
                if(rev!=s.revision())throw new IllegalStateException("La clasificación cambió. Actualice el listado y vuelva a seleccionar.");
                Long actual=anterior.isEmpty()||anterior.get(0)[1]==null?null:numero(anterior.get(0)[1]);
                if(Objects.equals(actual,categoria))continue;
                em.createNativeQuery("""
                    INSERT INTO clasificacion_operacion_evento(contribuyente_id,documento_id,categoria_id,usuario_id,motivo)
                    VALUES (:t,:d,:c,:u,:m)
                    """).setParameter("t",titular).setParameter("d",s.documento()).setParameter("c",categoria)
                    .setParameter("u",actor).setParameter("m",m).executeUpdate();
                cambios++;
            }
            return cambios;
        });
    }

    /** Historial paginado, incluidos retiros; no permite acceso por UUID ajeno. */
    public Pagina<Evento> historial(Long actor,long titular,long documento,int pagina){
        return jpa.consultar(em->{
            autorizar(em,actor,titular,Permisos.OPERACIONES_CONSULTAR);
            exigirDocumento(em,titular,documento,false);
            List<Evento> datos=filas(em.createNativeQuery("""
                SELECT h.id,h.creado_en,u.login,cat.nombre,cat.naturaleza,h.motivo
                FROM clasificacion_operacion_evento h JOIN usuario u ON u.id=h.usuario_id
                LEFT JOIN categoria_operacion cat ON cat.id=h.categoria_id AND cat.contribuyente_id=h.contribuyente_id
                WHERE h.contribuyente_id=:t AND h.documento_id=:d ORDER BY h.id DESC
                """).setParameter("t",titular).setParameter("d",documento)
                .setFirstResult(offset(pagina)).setMaxResults(PAGINA+1)).stream()
                .map(x->new Evento(numero(x[0]),fecha(x[1]),texto(x[2]),texto(x[3]),texto(x[4]),texto(x[5]))).toList();
            return pagina(datos);
        });
    }

    private static void exigirDocumento(EntityManager em,long titular,long documento,boolean bloquear){
        List<?> r=em.createNativeQuery("""
            SELECT cd.documento_id FROM contribuyente_documento cd JOIN cfdi c ON c.documento_id=cd.documento_id
            WHERE cd.contribuyente_id=:t AND cd.documento_id=:d AND cd.archivado_en IS NULL AND c.tipo IN ('I','E','N')
            """+(bloquear?" FOR UPDATE":"")).setParameter("t",titular).setParameter("d",documento).getResultList();
        if(r.size()!=1)throw new SecurityException("Operación no disponible para este contribuyente");
    }
    private static void autorizar(EntityManager em,Long actor,long titular,String permiso){
        AutorizacionService.exigir(em,actor,Permisos.OPERACIONES_CONSULTAR);
        if(!permiso.equals(Permisos.OPERACIONES_CONSULTAR))AutorizacionService.exigir(em,actor,permiso);
        Number n=(Number)em.createNativeQuery("SELECT COUNT(*) FROM usuario_contribuyente WHERE usuario_id=:u AND contribuyente_id=:t")
            .setParameter("u",actor).setParameter("t",titular).getSingleResult();
        if(n.longValue()==0)throw new SecurityException("Contribuyente no autorizado");
    }
    static String validarClave(String clave){
        String k=obligatorio(clave,35).toUpperCase(Locale.ROOT);
        if(!k.matches("[A-Z0-9_\\-]+")||k.length()>35)throw new IllegalArgumentException("Clave: letras, números, guion o guion bajo");
        return k;
    }
    static String obligatorio(String s,int max){
        String v=s==null?"":s.strip();
        if(v.isEmpty()||v.length()>max)throw new IllegalArgumentException("Valor obligatorio de hasta "+max+" caracteres");
        return v;
    }
    private static boolean activo(Object o){return o instanceof Boolean b?b:((Number)o).intValue()!=0;}
    private static long numero(Object n){return ((Number)n).longValue();}
    private static String texto(Object o){return o==null?null:o.toString();}
    private static LocalDateTime fecha(Object o){return o instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)o;}
    @SuppressWarnings("unchecked") private static List<Object[]> filas(Query q){return q.getResultList();}
    private static int offset(int p){
        if(p<0||p>Integer.MAX_VALUE/PAGINA)throw new IllegalArgumentException("Página inválida");
        return p*PAGINA;
    }
    private static <T> Pagina<T> pagina(List<T> datos){
        return new Pagina<>(datos.subList(0,Math.min(PAGINA,datos.size())),datos.size()>PAGINA);
    }
}
