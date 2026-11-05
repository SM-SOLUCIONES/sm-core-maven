package com.blue.open.Core.Kernel.Configuracion;

import com.blue.open.Core.Kernel.Puertos.AutorizadorDeEndpoint;
import com.blue.open.Core.Kernel.Puertos.AutorizadorDenegarTodo;
import com.blue.open.Core.Kernel.Puertos.ConfiguracionDesdePropiedades;
import com.blue.open.Core.Kernel.Puertos.ProveedorDeConfiguracion;
import com.blue.open.Core.Kernel.Puertos.ProveedorDeUsuarioActual;
import com.blue.open.Core.Kernel.Puertos.ProveedorDeVersion;
import com.blue.open.Core.Kernel.Puertos.UsuarioActualAnonimo;
import com.blue.open.Core.Kernel.Puertos.VersionPorPropiedad;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Registra las implementaciones por defecto de los puertos del kernel.
 * Todas son @ConditionalOnMissingBean: alcanza con que el proyecto (o un modulo del Core,
 * como core-identity) publique su propio bean para reemplazarlas sin tocar el Core.
 * Es @AutoConfiguration (registrada en META-INF/spring/...AutoConfiguration.imports) y no @Configuration:
 * asi se procesa despues de los beans del proyecto (ej. el ProveedorDeVersion de Application) y el
 * @ConditionalOnMissingBean los ve. Como @Configuration escaneada se evaluaba antes y el default ganaba.
 * Dependencias externas: ninguna. Recibe: nada. Responde: los beans base del kernel.
 */
@AutoConfiguration
public class KernelConfiguracion {

    // Sin core-identity no hay usuario logeado: auditoria en null y sin permisos
    @Bean
    @ConditionalOnMissingBean(ProveedorDeUsuarioActual.class)
    public ProveedorDeUsuarioActual proveedorDeUsuarioActual() {
        return new UsuarioActualAnonimo();
    }

    // Sin core-identity solo se pueden usar los endpoints PUBLICO: el resto responde 403
    @Bean
    @ConditionalOnMissingBean(AutorizadorDeEndpoint.class)
    public AutorizadorDeEndpoint autorizadorDeEndpoint() {
        return new AutorizadorDenegarTodo();
    }

    // Sin un ProveedorDeConfiguracion propio, la configuracion se lee de las properties de Spring
    @Bean
    @ConditionalOnMissingBean(ProveedorDeConfiguracion.class)
    public ProveedorDeConfiguracion proveedorDeConfiguracion(Environment environment) {
        return new ConfiguracionDesdePropiedades(environment);
    }

    // Sin un ProveedorDeVersion propio, la version de la aplicacion se lee de una propiedad
    @Bean
    @ConditionalOnMissingBean(ProveedorDeVersion.class)
    public ProveedorDeVersion proveedorDeVersion() {
        return new VersionPorPropiedad();
    }
}
