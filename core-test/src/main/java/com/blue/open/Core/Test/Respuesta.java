package com.blue.open.Core.Test;

import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Respuesta de una operacion ejecutada por una prueba de contrato ({@link Peticion#ejecutar()}). Expone el status y
 * el body (JSON o texto) y las aserciones de status y codigo de error que usan todas las pruebas.
 */
public final class Respuesta {

    private final MvcResult resultado;

    private final ObjectMapper mapper;

    Respuesta(MvcResult resultado, ObjectMapper mapper) {
        this.resultado = resultado;
        this.mapper = mapper;
    }

    // Status HTTP de la respuesta
    public int status() {
        return resultado.getResponse().getStatus();
    }

    // Body como texto UTF-8 (vacio si no hay body)
    public String texto() {
        return new String(resultado.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    // Body como arbol JSON. Lanza AssertionError si el body no es JSON
    public JsonNode json() {
        try {
            return mapper.readTree(texto());
        } catch (RuntimeException e) {
            return fail("La respuesta no es JSON: " + texto());
        }
    }

    // Body deserializado a la clase recibida con el ObjectMapper de la aplicacion
    public <T> T como(Class<T> clase) {
        return mapper.readValue(texto(), clase);
    }

    // Resultado crudo de MockMvc (headers, request, handler)
    public MvcResult resultado() {
        return resultado;
    }

    // Exige el status recibido. Responde la misma respuesta para encadenar
    public Respuesta esperar(int status) {
        assertEquals(status, status(), () -> "Status inesperado. Body: " + texto());
        return this;
    }

    // Exige el status y el codigo de error (campo "codigo" de ErrorResponseDTO) recibidos
    public Respuesta esperarError(int status, String codigo) {
        esperar(status);
        assertEquals(codigo, json().path("codigo").asString(null), () -> "Codigo de error inesperado. Body: " + texto());
        return this;
    }
}
