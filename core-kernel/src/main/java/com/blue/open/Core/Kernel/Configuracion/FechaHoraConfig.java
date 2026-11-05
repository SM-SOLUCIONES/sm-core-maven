package com.blue.open.Core.Kernel.Configuracion;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

// Zona horaria de la aplicacion (GMT-3): la fuerza al arrancar y da la fecha y hora actual en esa zona
public final class FechaHoraConfig {
    public static final String ID_DE_ZONA = "America/Argentina/Buenos_Aires";
    public static final TimeZone ZONA_HORARIA = TimeZone.getTimeZone(ID_DE_ZONA);
    public static final ZoneId ZONA = ZONA_HORARIA.toZoneId();

    private FechaHoraConfig() {
    }

    // Fija GMT-3 como zona horaria por defecto de la JVM. Se llama al arrancar
    public static void forzarZonaHorariaPorDefecto() {
        TimeZone.setDefault(ZONA_HORARIA);
        System.setProperty("user.timezone", ID_DE_ZONA);
    }

    // Responde: la fecha y hora actual en GMT-3
    public static LocalDateTime ahoraLocalDateTime() {
        return LocalDateTime.now(ZONA);
    }

    // Responde: la fecha y hora actual en GMT-3 como Timestamp
    public static Timestamp ahoraTimestamp() {
        return Timestamp.valueOf(ahoraLocalDateTime());
    }

    // Responde: la fecha y hora actual en GMT-3 como Date
    public static Date ahoraDate() {
        return GregorianCalendar.from(ahoraLocalDateTime().atZone(ZONA)).getTime();
    }
}
