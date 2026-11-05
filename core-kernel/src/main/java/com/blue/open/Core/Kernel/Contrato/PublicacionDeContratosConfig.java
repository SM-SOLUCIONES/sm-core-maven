package com.blue.open.Core.Kernel.Contrato;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve el Contrato de endpoint empaquetado en el jar (classpath:/docs-api/: openapi.yaml e index.html con Redoc, los
 * copia el build del proyecto en prepare-package) en /docs, sin autenticacion: cada ambiente documenta lo que corre.
 * Se controla con contratos.publicar (default true); en produccion va en false para no exponer el mapa de la API, y
 * entonces /docs no existe (404). Ver core/docs/arquitectura/testing/README.md.
 * Dependencias externas: ninguna. Recibe: nada. Responde: los recursos estaticos del contrato.
 */
@Configuration
@ConditionalOnProperty(name = "contratos.publicar", havingValue = "true", matchIfMissing = true)
public class PublicacionDeContratosConfig implements WebMvcConfigurer {

    // /docs/** -> archivos del contrato del jar
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/docs/**").addResourceLocations("classpath:/docs-api/");
    }

    // /docs -> /docs/index.html
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/docs", "/docs/index.html");
    }
}
