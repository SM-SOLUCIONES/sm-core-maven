package com.blue.open.Core.Test.Contrato;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que el ensamblado une en una sola operacion de OpenAPI las variantes de un endpoint que comparten path y
 * verbo y se distinguen por el content type del body (OpenAPI admite una operacion por path y verbo).
 */
class EnsambladorDeContratoTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void lasVariantesDeUnMismoPathYVerboSeUnenPorContentType() {
        ObjectNode json = parcial("Controlador.alta", "Alta con JSON", "application/json",
                """
                {"nombre": "Con JSON", "actor": "SYSADMIN", "status": 201, "request": {"nombre": "x"},
                 "response": {"uuid": "u1"}}""");
        ObjectNode multipart = parcial("Controlador.altaConArchivos", "Alta con archivos", "multipart/form-data",
                """
                {"nombre": "Con archivos", "actor": "SYSADMIN", "status": 201, "partes": {"archivo": "Archivo a.jpg"},
                 "response": {"uuid": "u2"}}""");

        JsonNode paths = EnsambladorDeContrato.openapi(List.of(json, multipart)).get("paths");

        JsonNode operacion = paths.get("/alta").get("post");
        assertEquals("Controlador.alta", operacion.get("operationId").asString());
        assertTrue(operacion.get("description").asString().contains("`multipart/form-data`: Alta con archivos"));
        JsonNode contenidos = operacion.get("requestBody").get("content");
        assertEquals(2, contenidos.size());
        assertTrue(contenidos.get("application/json").get("examples").has("con-json"));
        assertEquals(1, contenidos.get("application/json").get("examples").size());
        assertTrue(contenidos.get("multipart/form-data").get("examples").has("con-archivos"));
        assertEquals(1, contenidos.get("multipart/form-data").get("examples").size());
        assertEquals(2, operacion.get("responses").get("201").get("content").get("application/json").get("examples").size());
    }

    // Contrato parcial minimo de POST /alta con un body del content type recibido y un ejemplo
    private static ObjectNode parcial(String operationId, String resumen, String contentType, String ejemplo) {
        ObjectNode parcial = (ObjectNode) MAPPER.readTree("""
                {"operationId": "%s", "metodo": "post", "path": "/alta", "tag": "Controlador", "casoDeUso": "CU-01",
                 "resumen": "%s", "permiso": "PERMISO", "heredado": false, "schemas": {}, "parametros": [],
                 "requestBody": {"contentType": "%s", "required": true, "schema": {"type": "object"}},
                 "respuestas": {"201": {"contentType": "application/json", "codigos": [], "schema": {"type": "object"}}},
                 "ejemplos": [%s]}""".formatted(operationId, resumen, contentType, ejemplo));
        return parcial;
    }
}
