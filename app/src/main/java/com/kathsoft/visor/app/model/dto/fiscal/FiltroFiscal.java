package com.kathsoft.visor.app.model.dto.fiscal;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Filtros explícitos del papel de trabajo; fechas inclusivas, sin inferir realización por emisión. */
public record FiltroFiscal(long titular,LocalDate desde,LocalDate hasta,String sentido,String rfc,
        String naturaleza,long categoria,String moneda,String tipo,String estadoSat) {
    /** Valida intervalos e identificadores sin construir SQL desde entradas del usuario. */
    public FiltroFiscal {
        Objects.requireNonNull(desde);Objects.requireNonNull(hasta);
        if(titular<=0||desde.isAfter(hasta)||desde.getYear()<1000||hasta.getYear()>9998||categoria<0)throw new IllegalArgumentException("Filtro fiscal inválido");
        sentido=limpiar(sentido);rfc=limpiar(rfc);naturaleza=limpiar(naturaleza);moneda=limpiar(moneda);tipo=limpiar(tipo);estadoSat=limpiar(estadoSat);
        if(!Set.of("","EMITIDO","RECIBIDO","AMBOS").contains(sentido)||!rfc.matches("[A-Z0-9Ñ&]{0,13}")||!moneda.matches("(?:[A-Z]{3})?"))throw new IllegalArgumentException("Sentido, RFC o moneda inválidos");
        if(!Set.of("","GASTO","COSTO","GASTO_FINANCIERO","INVERSION","INGRESO","OTRA","SIN_CLASIFICAR").contains(naturaleza)||!Set.of("","I","E","N","P","T").contains(tipo))throw new IllegalArgumentException("Naturaleza o tipo inválidos");
        if(!Set.of("","VIGENTE","CANCELADO","NO_ENCONTRADO","DESCONOCIDO","ERROR","SIN_CONSULTA").contains(estadoSat))throw new IllegalArgumentException("Estado SAT inválido");
    }
    /** Filtro mensual sin restricciones adicionales. */
    public FiltroFiscal(long titular,LocalDate desde,LocalDate hasta){this(titular,desde,hasta,"","","",0,"","","");}
    private static String limpiar(String s){return s==null?"":s.strip().toUpperCase(Locale.ROOT);}
}
