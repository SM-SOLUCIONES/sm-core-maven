package com.blue.open.Core.Protocolo.Sso;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;
import java.net.URL;

/**
 * Registra el JwtDecoder contra el JWKS del IDP de Malvinas.
 * Es @ConditionalOnProperty: en Core 1.x este bean estaba en el SecurityConfig general, asi que
 * cualquier proyecto sin la propiedad auth.malvinas.url fallaba al arrancar con un MalformedURLException.
 * Dependencias externas: el endpoint JWKS de Malvinas (se lee al validar cada token).
 * Recibe: nada. Responde: el JwtDecoder que usa AuthMalvinasService.
 */
@Configuration
@ConditionalOnProperty(name = "auth.malvinas.url")
public class MalvinasJwtDecoderConfig {

    @Value("${auth.malvinas.url}")
    private String urlDeMalvinas;

    // JwtDecoder que valida firmas ES256 con las claves del JWKS de auth.malvinas.url (timeouts de 2 segundos)
    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        URL jwkSetURL = URI.create(urlDeMalvinas).toURL();

        // Timeout para leer la clave publica
        var resourceRetriever = new DefaultResourceRetriever(2000, 2000);
        var jwkSource = JWKSourceBuilder.<SecurityContext>create(jwkSetURL, resourceRetriever).build();

        var jwtProcessor = new DefaultJWTProcessor<SecurityContext>();
        jwtProcessor.setJWSTypeVerifier((type, context) -> {
        }); // ignora el "typ"
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, jwkSource);
        jwtProcessor.setJWSKeySelector(keySelector);

        return new NimbusJwtDecoder(jwtProcessor);
    }
}
