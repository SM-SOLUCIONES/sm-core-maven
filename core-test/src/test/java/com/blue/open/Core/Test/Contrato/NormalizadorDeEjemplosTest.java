package com.blue.open.Core.Test.Contrato;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que los ejemplos publicados no cambian entre corridas sobre el mismo codigo.
 */
class NormalizadorDeEjemplosTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NormalizadorDeEjemplos normalizador = new NormalizadorDeEjemplos();

    @Test
    void losUuidGeneradosSeNumeranYLosDeSeedSeConservan() {
        JsonNode json = MAPPER.readTree("""
                {"uuid": "3f2a9c1e-5b7d-4e8f-9a1b-2c3d4e5f6a7b", "padre": "d0000000-0000-4000-8000-000000000002",
                 "otro": "3f2a9c1e-5b7d-4e8f-9a1b-2c3d4e5f6a7b"}""");

        JsonNode normalizado = normalizador.normalizar(json);

        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-000000000001", normalizado.get("uuid").asString());
        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-000000000001", normalizado.get("otro").asString());
        assertEquals("d0000000-0000-4000-8000-000000000002", normalizado.get("padre").asString());
    }

    @Test
    void lasFechasDeHoyYLosDatosDeLaTrazaSeFijan() {
        String hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).toString();
        JsonNode json = MAPPER.readTree("{\"alta\": \"" + hoy + "T10:46:00.123-03:00\", \"dia\": \"" + hoy
                + "\", \"traceId\": \"x1\", \"vigencia\": \"2026-02-01T00:00:00.000-03:00\", \"desdeHoy\": \"" + hoy
                + "T00:00:00.000-03:00\"}");

        JsonNode normalizado = normalizador.normalizar(json);

        assertEquals("2026-01-01T12:00:00.000-03:00", normalizado.get("alta").asString());
        assertEquals("2026-01-01T00:00:00.000-03:00", normalizado.get("desdeHoy").asString());
        assertEquals("2026-01-01", normalizado.get("dia").asString());
        assertEquals("0000000000000000", normalizado.get("traceId").asString());
        assertEquals("2026-02-01T00:00:00.000-03:00", normalizado.get("vigencia").asString());
    }

    @Test
    void losArraysSeRecortanYLasPropiedadesSeOrdenan() {
        JsonNode json = MAPPER.readTree("""
                {"z": 1, "a": [1, 2, 3, 4, 5]}""");

        JsonNode normalizado = normalizador.normalizar(json);

        assertEquals("{\"a\":[1,2,3],\"z\":1}", normalizado.toString());
    }
}
