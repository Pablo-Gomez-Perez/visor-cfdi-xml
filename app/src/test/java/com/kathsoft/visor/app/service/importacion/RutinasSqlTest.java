package com.kathsoft.visor.app.service.importacion;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Detecta terminadores truncados antes de iniciar Docker. */
class RutinasSqlTest {
    @Test void cadaRutinaTieneDelimitadorCompleto() throws Exception {
        Path base=Path.of(System.getProperty("user.dir"));
        Path ruta=base.resolve("database/procedures/procedures.sql");
        if(!Files.isRegularFile(ruta))ruta=base.resolve("app/database/procedures/procedures.sql");
        String sql=Files.readString(ruta);
        assertTrue(sql.contains("DELIMITER $$"));
        assertTrue(sql.stripTrailing().endsWith("DELIMITER ;"));
        assertEquals(4L,Pattern.compile("(?m)^CREATE DEFINER=").matcher(sql).results().count());
        assertEquals(4L,Pattern.compile("(?m)^END\\$\\$[ \\t]*$").matcher(sql).results().count());
        assertEquals(0L,Pattern.compile("(?m)^END\\$[ \\t]*$").matcher(sql).results().count());
    }
}
