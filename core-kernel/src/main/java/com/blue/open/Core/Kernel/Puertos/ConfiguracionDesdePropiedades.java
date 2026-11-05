package com.blue.open.Core.Kernel.Puertos;

import org.springframework.core.env.Environment;

/**
 * Implementacion por defecto de {@link ProveedorDeConfiguracion}: lee del application.properties.
 * Dependencias externas: el Environment de Spring.
 * Recibe: clave y valor por defecto. Responde: el valor de la propiedad o el default.
 */
public class ConfiguracionDesdePropiedades implements ProveedorDeConfiguracion {

    private final Environment environment;

    // Recibe el Environment de Spring del que se leen las propiedades
    public ConfiguracionDesdePropiedades(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getValor(String clave, String porDefecto) {
        return environment.getProperty(clave, porDefecto);
    }
}
