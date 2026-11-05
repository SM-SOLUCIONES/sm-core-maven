package com.blue.open.Core.Kernel.Contrato;

import com.blue.open.Core.Kernel.Puertos.AutorizadorDeEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra InterceptorDeContrato en Spring MVC: todo request a un controller de com.blue.open pasa por el permiso
 * de su @Contrato. Dependencias externas: ninguna. Recibe: nada. Responde: el interceptor registrado.
 */
@Configuration
@RequiredArgsConstructor
public class AutorizacionPorContratoConfig implements WebMvcConfigurer {

    // ObjectProvider: el autorizador (core-identity) depende de JPA; se resuelve recien en el primer request
    private final ObjectProvider<AutorizadorDeEndpoint> autorizador;

    // Agrega el interceptor a todas las rutas
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InterceptorDeContrato(autorizador));
    }
}
