package com.blue.open.Core.Identity.Configuracion;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifica el CORS por defecto de SeguridadWebConfig: que exponga a un front en otro origen los encabezados
 * que el backend manda y el JavaScript necesita leer (nombre de archivo de las descargas, traceId y version).
 */
class SeguridadWebConfigTest {

    // El CORS por defecto expone Content-Disposition, X-Trace-Id y API-Version
    @Test
    void corsExponeLosEncabezadosQueLeeElFront() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/documentos/uuid/descargar-pdf");
        CorsConfiguration configuracion = new SeguridadWebConfig().corsConfigurationSource().getCorsConfiguration(request);

        assertNotNull(configuracion);
        assertEquals(List.of("Content-Disposition", "X-Trace-Id", "API-Version"), configuracion.getExposedHeaders());
    }
}
