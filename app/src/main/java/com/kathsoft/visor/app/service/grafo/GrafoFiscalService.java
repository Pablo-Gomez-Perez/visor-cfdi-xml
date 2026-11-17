package com.kathsoft.visor.app.service.grafo;

import com.kathsoft.visor.app.model.dto.grafo.*;
import com.kathsoft.visor.app.persistence.JpaContext;
import com.kathsoft.visor.app.security.AutorizacionService;
import com.kathsoft.visor.app.security.Permisos;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import java.sql.*;
import java.util.*;
import java.util.function.Function;

/** RF-018-A: lectura de relaciones explícitas y documentos activos de un titular. */
public final class GrafoFiscalService {
    private final JpaContext jpa;
    private static final int LIMITE=ConstructorGrafoFiscal.MAX_ARISTAS+1;
    private static final String NODOS="""
        SELECT d.id,d.uuid,CASE WHEN d.clase='RETENCION' THEN 'RETENCION' ELSE c.tipo END,
        c.serie,COALESCE(c.folio,r.folio_interno),e.rfc,rec.rfc,d.fecha_emision,
        c.total,c.moneda,c.metodo_pago,COALESCE(s.estado,'SIN_CONSULTA')
        FROM contribuyente_documento cd JOIN documento_fiscal d ON d.id=cd.documento_id
        LEFT JOIN cfdi c ON c.documento_id=d.id
        LEFT JOIN retencion r ON r.documento_id=d.id
        LEFT JOIN documento_participante e ON e.documento_id=d.id AND e.rol='E'
        LEFT JOIN documento_participante rec ON rec.documento_id=d.id AND rec.rol='R'
        LEFT JOIN consulta_estado_sat s ON s.id=(SELECT ss.id FROM consulta_estado_sat ss
            WHERE ss.documento_id=d.id ORDER BY ss.fecha_consulta DESC,ss.id DESC LIMIT 1)
        WHERE cd.contribuyente_id=? AND cd.archivado_en IS NULL AND
        """;

    /** Usa el contexto compartido, no crea conexiones adicionales. */
    public GrafoFiscalService(JpaContext jpa){this.jpa=Objects.requireNonNull(jpa);}

    /** Consulta autorizada en una única transacción; el focal debe estar activo. */
    public GrafoFiscal consultar(Long actor,long titular,long documento){
        if(titular<=0||documento<=0)throw new IllegalArgumentException("Identificadores inválidos");
        return jpa.enTransaccion(em->{
            autorizar(em,actor,titular);
            return em.unwrap(Session.class).doReturningWork(c->{
                List<NodoCfdi> focal=nodos(c,titular,"d.id=?",List.of(documento));
                if(focal.size()!=1)throw new SecurityException("Documento no disponible");
                return new ConstructorGrafoFiscal().construir(focal.get(0),
                    f->sql(()->relaciones(c,titular,f)),
                    u->sql(()->nodos(c,titular,"d.uuid IN ("+marcas(u.size())+")",u)));
            });
        });
    }

    /** Permite decidir si habilitar la pestaña sin conceder permiso por la UI. */
    public boolean permitido(Long actor,long titular){
        return jpa.consultar(em->{
            try{autorizar(em,actor,titular);return true;}
            catch(SecurityException ex){return false;}
        });
    }

    private static void autorizar(EntityManager em,Long actor,long titular){
        AutorizacionService.exigir(em,actor,Permisos.CFDI_CONSULTAR);
        AutorizacionService.exigir(em,actor,Permisos.CFDI_CONSULTAR_DETALLE);
        AutorizacionService.exigir(em,actor,Permisos.CFDI_CONSULTAR_GRAFO);
        Number n=(Number)em.createNativeQuery("SELECT COUNT(*) FROM usuario_contribuyente WHERE usuario_id=:actor AND contribuyente_id=:titular")
            .setParameter("actor",actor).setParameter("titular",titular).getSingleResult();
        if(n.longValue()==0)throw new SecurityException("Contribuyente no autorizado");
    }

    private static List<NodoCfdi> nodos(Connection c,long titular,String condicion,List<?> valores)throws SQLException{
        List<NodoCfdi> salida=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement(NODOS+condicion)){
            p.setLong(1,titular);parametros(p,2,valores);
            try(ResultSet r=p.executeQuery()){
                while(r.next())salida.add(new NodoCfdi(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),
                    r.getString(5),r.getString(6),r.getString(7),r.getTimestamp(8).toLocalDateTime(),
                    r.getBigDecimal(9),r.getString(10),r.getString(11),r.getString(12)));
            }
        }
        return salida;
    }

    private record Fuente(TipoRelacionDocumental tipo,String columnas,String joins,String destino){}
    private static final List<Fuente> FUENTES=List.of(
        new Fuente(TipoRelacionDocumental.COMPLEMENTO_PAGO,
            "CONCAT('P:',pd.id),d.uuid,pd.uuid_documento,NULL,pd.num_parcialidad,pd.importe_pagado,pd.moneda",
            "pago_fiscal_documento pd JOIN pago_fiscal pf ON pf.id=pd.pago_fiscal_id JOIN documento_fiscal d ON d.id=pf.documento_id","pd.uuid_documento"),
        new Fuente(TipoRelacionDocumental.CFDI_RELACIONADO,
            "CONCAT('C:',g.id,':',u.uuid_relacionado),d.uuid,u.uuid_relacionado,g.tipo_relacion,NULL,NULL,NULL",
            "cfdi_relacion_uuid u JOIN cfdi_relacion_grupo g ON g.id=u.grupo_id JOIN documento_fiscal d ON d.id=g.documento_id","u.uuid_relacionado"),
        new Fuente(TipoRelacionDocumental.RETENCION_RELACIONADA,
            "CONCAT('R:',d.id,':',u.uuid_relacionado),d.uuid,u.uuid_relacionado,NULL,NULL,NULL,NULL",
            "retencion_relacion_uuid u JOIN documento_fiscal d ON d.id=u.documento_id","u.uuid_relacionado")
    );

    private static ConstructorGrafoFiscal.Relaciones relaciones(Connection c,long titular,List<NodoCfdi> frontera)throws SQLException{
        List<Long> ids=frontera.stream().map(NodoCfdi::documentoId).toList();
        List<String> uuids=frontera.stream().map(NodoCfdi::uuid).toList();
        Map<String,AristaFiscal> salida=new LinkedHashMap<>();
        boolean limitada=false;
        for(Fuente f:FUENTES){
            for(boolean entrante:List.of(false,true)){
                String clave=entrante?f.destino():"d.id";
                String consulta="SELECT "+f.columnas()+" FROM "+f.joins()
                    +" JOIN contribuyente_documento cd ON cd.documento_id=d.id"
                    +" WHERE cd.contribuyente_id=? AND cd.archivado_en IS NULL AND "+f.destino()+" IS NOT NULL AND "
                    +clave+" IN ("+marcas(ids.size())+") ORDER BY 1 LIMIT "+LIMITE;
                try(PreparedStatement p=c.prepareStatement(consulta)){
                    p.setLong(1,titular);parametros(p,2,entrante?uuids:ids);
                    try(ResultSet r=p.executeQuery()){
                        int n=0;
                        while(r.next()){
                            if(++n>=LIMITE){limitada=true;break;}
                            long parcialidad=r.getLong(5);Long parcial=r.wasNull()?null:parcialidad;
                            var a=new AristaFiscal(r.getString(1),r.getString(2),r.getString(3),f.tipo(),
                                r.getString(4),parcial,r.getBigDecimal(6),r.getString(7));
                            salida.putIfAbsent(a.id(),a);
                        }
                    }
                }
            }
        }
        return new ConstructorGrafoFiscal.Relaciones(List.copyOf(salida.values()),limitada);
    }

    private static String marcas(int n){return String.join(",",Collections.nCopies(n,"?"));}
    private static void parametros(PreparedStatement p,int inicio,List<?> valores)throws SQLException{
        for(int i=0;i<valores.size();i++)p.setObject(inicio+i,valores.get(i));
    }
    @FunctionalInterface private interface Consulta<T>{T ejecutar()throws SQLException;}
    private static <T>T sql(Consulta<T> consulta){
        try{return consulta.ejecutar();}catch(SQLException e){throw new IllegalStateException("Error al consultar relaciones fiscales",e);}
    }
}
