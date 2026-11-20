package com.kathsoft.visor.app.service.fiscal;

import com.kathsoft.visor.app.model.dto.fiscal.FiltroFiscal;
import com.kathsoft.visor.app.model.dto.fiscal.TablaFiscal;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Exportación paginada UTF-8 sin dependencias y con neutralización de fórmulas de hoja de cálculo. */
public final class ExportacionFiscalService {
    private ExportacionFiscalService(){ }
    /** Exporta todas las páginas revalidando permisos en cada lectura; publica el archivo sólo al terminar. */
    public static long exportar(ResumenFiscalService servicio,Long actor,FiltroFiscal filtro,ResumenFiscalService.Seccion seccion,Path destino)throws IOException {
        Path absoluta=destino.toAbsolutePath();Path temporal=Files.createTempFile(absoluta.getParent(),"visor-fiscal-",".csv.tmp");
        long cantidad=0;
        try {
            try(Writer writer=Files.newBufferedWriter(temporal,StandardCharsets.UTF_8)){
                int pagina=0;TablaFiscal datos;
                do {
                    if(Thread.currentThread().isInterrupted())throw new IOException("Exportación cancelada");
                    datos=servicio.consultar(actor,filtro,seccion,pagina++);
                    if(pagina==1)fila(writer,datos.columnas());
                    for(List<Object> valores:datos.filas()){fila(writer,valores);cantidad++;}
                }while(datos.siguiente());
            }
            Files.move(temporal,absoluta,StandardCopyOption.REPLACE_EXISTING);
            return cantidad;
        }finally{Files.deleteIfExists(temporal);}
    }
    /** Escribe una fila CSV; null queda vacío y un decimal conserva representación exacta. */
    public static void fila(Writer writer,List<?> valores)throws IOException {
        for(int i=0;i<valores.size();i++){
            if(i>0)writer.write(',');Object valor=valores.get(i);
            String texto=valor==null?"":valor instanceof BigDecimal b?b.toPlainString():valor.toString();
            if(valor instanceof String && !texto.stripLeading().isEmpty() && "=+@-".indexOf(texto.stripLeading().charAt(0))>=0)texto="'"+texto;
            writer.write('"');writer.write(texto.replace("\"","\"\""));writer.write('"');
        }
        writer.write("\r\n");
    }
}
