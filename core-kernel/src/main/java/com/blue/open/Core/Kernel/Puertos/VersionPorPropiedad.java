package com.blue.open.Core.Kernel.Puertos;

import org.springframework.beans.factory.annotation.Value;

/**
 * Implementacion por defecto de {@link ProveedorDeVersion}: lee la propiedad app.version.
 * Dependencias externas: ninguna. Recibe: nada. Responde: el valor de app.version o "desconocida".
 */
public class VersionPorPropiedad implements ProveedorDeVersion {

    @Value("${app.version:desconocida}")
    private String version;

    @Override
    public String getVersion() {
        return version;
    }
}
