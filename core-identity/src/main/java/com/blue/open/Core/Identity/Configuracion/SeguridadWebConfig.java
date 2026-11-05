package com.blue.open.Core.Identity.Configuracion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Cadena de seguridad web por defecto: deja pasar todas las rutas y habilita CORS abierto, porque
 * la autorizacion real la resuelve AuthService dentro de cada endpoint, no Spring Security.
 * Es @ConditionalOnMissingBean: un proyecto que necesite otra politica publica su propio
 * SecurityFilterChain y este no se registra, sin tocar el Core.
 * En Core 1.x esta configuracion vivia en el Core junto al JwtDecoder de Malvinas, lo que hacia
 * fallar el arranque de cualquier proyecto que no usara esa integracion.
 */
@Configuration
@EnableWebSecurity
public class SeguridadWebConfig {

    // Cadena de seguridad por defecto: permite todas las rutas, CORS abierto, sin CSRF y frames del mismo origen
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    // CORS por defecto: cualquier origen, metodo y header, con credenciales, para todas las rutas.
    // Expone Content-Disposition (nombre de archivo de las descargas), X-Trace-Id y API-Version para que
    // el JavaScript de un front en otro origen los pueda leer
    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition", "X-Trace-Id", "API-Version"));
        configuration.setAllowCredentials(true);
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
