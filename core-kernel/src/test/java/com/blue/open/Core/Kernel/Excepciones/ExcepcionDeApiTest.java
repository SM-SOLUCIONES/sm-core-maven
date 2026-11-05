package com.blue.open.Core.Kernel.Excepciones;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcepcionDeApiTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private enum CodigoDePrueba implements CodigoDeError {
        ERROR_DE_PRUEBA(HttpStatus.BAD_REQUEST, "Mensaje por defecto");

        private final HttpStatus status;
        private final String mensajePorDefecto;

        CodigoDePrueba(HttpStatus status, String mensajePorDefecto) {
            this.status = status;
            this.mensajePorDefecto = mensajePorDefecto;
        }

        @Override
        public String codigo() {
            return name();
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @Override
        public String mensajePorDefecto() {
            return mensajePorDefecto;
        }
    }

    @Test
    void desde_tomaStatusYMensajeParaUsuarioPorDefectoDelCodigo() {
        ExcepcionDeApi excepcion = new ExcepcionDeApi(CodigoDePrueba.ERROR_DE_PRUEBA);

        ErrorResponseDTO response = ErrorResponseDTO.desde(excepcion);

        assertEquals(CodigoDePrueba.ERROR_DE_PRUEBA, response.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertEquals("Mensaje por defecto", response.getMensajeParaUsuario());
        assertTrue(response.getDatos().isEmpty());
        assertTrue(response.getErrores().isEmpty());
    }

    @Test
    void conDato_agregaUnDatoNoSensible() {
        ExcepcionDeApi excepcion = new ExcepcionDeApi(CodigoDePrueba.ERROR_DE_PRUEBA)
                .conDato("uuid", "0f1e2d3c");

        assertEquals("0f1e2d3c", ErrorResponseDTO.desde(excepcion).getDatos().get("uuid"));
    }

    @Test
    void conDato_ignoraValorNull() {
        ExcepcionDeApi excepcion = new ExcepcionDeApi(CodigoDePrueba.ERROR_DE_PRUEBA)
                .conDato("uuid", null);

        assertTrue(ErrorResponseDTO.desde(excepcion).getDatos().isEmpty());
    }

    @Test
    void conDato_descartaClaveSensible() {
        ExcepcionDeApi excepcion = new ExcepcionDeApi(CodigoDePrueba.ERROR_DE_PRUEBA)
                .conDato("password", "1234");

        assertTrue(ErrorResponseDTO.desde(excepcion).getDatos().isEmpty());
    }

    @Test
    void errorResponseDTO_noExponeMensajeParaDesarrolladorNiCampoDeThrowable() {
        ExcepcionDeApi excepcion = new ExcepcionDeApi(
                CodigoDePrueba.ERROR_DE_PRUEBA, "detalle interno", new RuntimeException("causa interna"));

        String json = objectMapper.writeValueAsString(ErrorResponseDTO.desde(excepcion));

        assertFalse(json.contains("mensajeParaDesarrollador"));
        assertFalse(json.contains("causa interna"));
        assertFalse(json.contains("stackTrace"));
        assertFalse(json.contains("suppressed"));
        assertFalse(json.contains("localizedMessage"));
        assertTrue(json.contains("\"codigo\":\"ERROR_DE_PRUEBA\""));
    }

    @Test
    void subError_noExponeElValor() {
        ExcepcionDeApi excepcion = new ExcepcionDeApi(CodigoDePrueba.ERROR_DE_PRUEBA);
        excepcion.agregarSubError("campo", "mensaje de validacion");

        String json = objectMapper.writeValueAsString(ErrorResponseDTO.desde(excepcion));

        assertFalse(json.contains("\"value\""));
        assertTrue(json.contains("\"nombre\":\"campo\""));
        assertTrue(json.contains("\"mensaje\":\"mensaje de validacion\""));
    }
}
