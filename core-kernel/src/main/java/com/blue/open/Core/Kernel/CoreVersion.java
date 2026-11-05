package com.blue.open.Core.Kernel;

import java.io.InputStream;
import java.util.Properties;

/**
 * Version del Core. No es un literal escrito a mano: se lee de core-version.properties, que Maven
 * completa con la version real del artefacto (core-parent/pom.xml). Asi no puede quedar desincronizada.
 * Dependencias externas: ninguna. Recibe: nada. Responde: la version, ej "2.0.0".
 */
public final class CoreVersion {

    private static final String CORE_VERSION = leerVersion();

    private CoreVersion() {
    }

    // Responde: la version del Core, o "desconocida" si no se pudo leer
    public static String get() {
        return CORE_VERSION;
    }

    private static String leerVersion() {
        try (InputStream entrada = CoreVersion.class.getResourceAsStream("/core-version.properties")) {
            if (entrada == null) {
                return "desconocida";
            }
            Properties propiedades = new Properties();
            propiedades.load(entrada);
            return propiedades.getProperty("core.version", "desconocida");
        } catch (Exception e) {
            return "desconocida";
        }
    }
}
