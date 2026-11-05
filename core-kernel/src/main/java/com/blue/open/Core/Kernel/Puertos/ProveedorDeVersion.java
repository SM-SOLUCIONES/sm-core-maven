package com.blue.open.Core.Kernel.Puertos;

/**
 * Puerto que expone la version de la aplicacion (no la del Core).
 * Dependencias externas: ninguna. Recibe: nada. Responde: la version, ej "1.4.3".
 * La consumen el header API-Version y el endpoint /info del actuator.
 * Por defecto lee la propiedad app.version; el proyecto puede publicar un bean propio.
 */
public interface ProveedorDeVersion {
    String getVersion();
}
