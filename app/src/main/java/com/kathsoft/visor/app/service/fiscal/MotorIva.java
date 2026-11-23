package com.kathsoft.visor.app.service.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Auxiliar conservador de realización, no declaración definitiva. LIVA 1-B, 5 y 7.
 * Usa impuestoDR declarado; no vuelve a sumar impuestos globales, P ni Totales.
 */
public final class MotorIva {
    private MotorIva() { }
    /** Datos de evidencia original y del tratamiento agregado de la misma firma tributaria. */
    public record Evidencia(BigDecimal impuestoDR, String monedaDR, String monedaP, BigDecimal equivalencia,
            BigDecimal tipoCambioP, String satFactura, String satRep, boolean facturaDisponible,
            boolean consistente, boolean sustitucion, boolean historico, BigDecimal impuestoOriginal,
            BigDecimal tratamientoAcreditable,BigDecimal tratamientoNoAcreditable,boolean ivaRetenido) { }
    /** null significa no confirmado; cero sólo se utiliza cuando el dato permite afirmarlo. */
    public record Resultado(BigDecimal realizadoDR,BigDecimal convertidoMXN,BigDecimal potencialDR,
            BigDecimal acreditableDR,BigDecimal noAcreditableDR,BigDecimal pendienteTratamientoDR,
            String estado,List<String> incidencias) {
        public Resultado { incidencias=List.copyOf(incidencias); }
    }

    /** Evalúa evidencia y clasificaciones sin inferir pago desde método PUE. */
    public static Resultado evaluar(Evidencia e) { return evaluar(e,true); }

    /** Sólo un IVA trasladado recibido utiliza columnas de acreditamiento; cobros y retenciones se separan. */
    public static Resultado evaluar(Evidencia e,boolean aplicaAcreditamiento) {
        List<String> avisos=new ArrayList<>();
        if(!e.facturaDisponible())avisos.add("FACTURA_NO_DISPONIBLE");
        if(!"VIGENTE".equals(e.satFactura()))avisos.add("SAT_FACTURA_"+e.satFactura());
        if(!"VIGENTE".equals(e.satRep()))avisos.add("SAT_REP_"+e.satRep());
        if(!e.consistente())avisos.add("PAGO_O_IMPUESTO_INCONSISTENTE");
        if(e.sustitucion())avisos.add("SUSTITUCION_REQUIERE_REVISION");
        if(e.historico())avisos.add("HISTORICO_SIN_DESGLOSE_DR");
        if(e.impuestoDR()==null)avisos.add("IMPORTE_DR_NO_DECLARADO");
        else if(e.impuestoDR().signum()<0)avisos.add("IMPORTE_DR_NEGATIVO");
        if(!avisos.isEmpty())return new Resultado(null,null,null,null,null,null,"PENDIENTE_EVIDENCIA",avisos);
        BigDecimal original=e.impuestoOriginal();
        if(original==null||original.signum()<0||e.impuestoDR().compareTo(original)>0)
            return new Resultado(null,null,null,null,null,null,"REVISAR_CONCILIACION",List.of("DR_SUPERA_O_NO_CONCILIA_IMPUESTO_ORIGINAL"));
        BigDecimal mxn=convertir(e.impuestoDR(),e.monedaDR(),e.monedaP(),e.equivalencia(),e.tipoCambioP());
        if(mxn==null)avisos.add("CONVERSION_MXN_SIN_FACTORES_VERIFICABLES");
        if(!aplicaAcreditamiento)return new Resultado(e.impuestoDR(),mxn,null,null,null,null,
            avisos.isEmpty()?"EVIDENCIA_FINANCIERA_REGISTRADA":"REALIZADO_CON_PENDIENTES",avisos);
        BigDecimal a=cero(e.tratamientoAcreditable()),n=cero(e.tratamientoNoAcreditable());
        if(a.signum()<0||n.signum()<0||a.add(n).compareTo(original)>0)
            return new Resultado(e.impuestoDR(),mxn,null,null,null,null,"REVISAR_TRATAMIENTO",List.of("TRATAMIENTOS_EXCEDEN_ORIGINAL"));
        BigDecimal potencial=prorrata(e.impuestoDR(),a,original);
        BigDecimal no=prorrata(e.impuestoDR(),n,original);
        BigDecimal pendiente=e.impuestoDR().subtract(potencial).subtract(no);
        BigDecimal acreditable=potencial;
        if(e.ivaRetenido()&&potencial.signum()>0){
            avisos.add("LIVA_5_IV_ENTERO_Y_MES_POSTERIOR_PENDIENTES");pendiente=pendiente.add(potencial);acreditable=null;
        }
        if(pendiente.signum()>0)avisos.add("TRATAMIENTO_O_REQUISITOS_PENDIENTES");
        return new Resultado(e.impuestoDR(),mxn,potencial,acreditable,no,pendiente,
                avisos.isEmpty()?"EVIDENCIA_Y_TRATAMIENTO_REGISTRADOS":"REALIZADO_CON_PENDIENTES",avisos);
    }

    /**
     * DR/unidadesDRporP × MXNporP. Ausencia de factor nunca se sustituye por uno entre monedas distintas.
     * Diez decimales de trabajo, HALF_UP, identificados como conversión derivada; no redondeo de declaración.
     */
    public static BigDecimal convertir(BigDecimal importe,String dr,String pago,BigDecimal equivalencia,BigDecimal tipoCambio) {
        if(importe==null||dr==null||pago==null||"XXX".equals(dr)||"XXX".equals(pago))return null;
        if(dr.equals("MXN"))return importe;
        BigDecimal eq=dr.equals(pago)?BigDecimal.ONE:equivalencia;
        BigDecimal cambio=pago.equals("MXN")?BigDecimal.ONE:tipoCambio;
        if(eq==null||cambio==null||eq.signum()<=0||cambio.signum()<=0)return null;
        return importe.divide(eq,16,RoundingMode.HALF_UP).multiply(cambio).setScale(10,RoundingMode.HALF_UP);
    }
    private static BigDecimal cero(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
    private static BigDecimal prorrata(BigDecimal importe,BigDecimal tratamiento,BigDecimal original){
        if(original.signum()==0)return BigDecimal.ZERO;
        return importe.multiply(tratamiento).divide(original,10,RoundingMode.DOWN);
    }
}
