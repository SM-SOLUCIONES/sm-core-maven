package com.blue.open.Core.Test.Dobles;

import com.blue.open.Core.Integracion.Crypter.CrypterService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Registra los dobles en memoria de los servicios externos, solo con el profile "test" (el que activa
 * TestConBaseDeDatos). Son @Primary: reemplazan a la implementacion real sin que el codigo de produccion cambie.
 * Cada doble se registra solo si el proyecto usa el modulo del Core que reemplaza.
 * Es @AutoConfiguration (META-INF/spring/...AutoConfiguration.imports de core-test): ningun proyecto la declara.
 */
@AutoConfiguration
@Profile("test")
public class DoblesDeTestConfiguracion {

    // Mails acumulados en memoria en vez de SMTP
    @Bean
    @Primary
    public EnviadorDeMailEnMemoria enviadorDeMailEnMemoria() {
        return new EnviadorDeMailEnMemoria();
    }

    // Doble de Crypter: solo si el proyecto declara core-crypter
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.blue.open.Core.Integracion.Crypter.CrypterService")
    static class CrypterDeTest {

        // Archivos en un mapa en vez del webservice Crypter
        @Bean
        @Primary
        public CrypterService crypterEnMemoria() {
            return new CrypterEnMemoria();
        }
    }
}
