package com.blue.open.Core.Kernel.Configuracion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuracion de Jackson de la aplicacion: zona horaria GMT-3 y fechas como texto.
 */
@Configuration
public class JacksonConfig {
    // ObjectMapper de la aplicacion: zona horaria de FechaHoraConfig, fechas como texto ISO (no
    // timestamps numericos) y sin fallar al serializar objetos sin propiedades
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .defaultTimeZone(FechaHoraConfig.ZONA_HORARIA)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }
}
