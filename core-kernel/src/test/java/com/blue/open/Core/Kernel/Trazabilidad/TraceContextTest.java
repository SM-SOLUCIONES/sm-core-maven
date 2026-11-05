package com.blue.open.Core.Kernel.Trazabilidad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceContextTest {

    @Test
    void resolver_aceptaUnTraceIdEntranteSeguro() {
        assertEquals("trace-123", TraceContext.resolver("trace-123"));
    }

    @Test
    void resolver_reemplazaUnTraceIdEntranteInseguro() {
        String resuelto = TraceContext.resolver("trace\nforjado");

        assertNotEquals("trace\nforjado", resuelto);
        assertTrue(resuelto.matches("[0-9a-f-]{36}"));
    }
}
