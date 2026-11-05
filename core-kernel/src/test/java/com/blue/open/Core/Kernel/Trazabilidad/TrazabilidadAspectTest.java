package com.blue.open.Core.Kernel.Trazabilidad;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrazabilidadAspectTest {

    @Test
    void trazar_propagaElTraceIdYRestauraElMdcAlTerminarBien() {
        ServicioPrueba objetivo = new ServicioPrueba();
        ServicioPrueba proxy = proxy(objetivo);
        MDC.put(TraceContext.TRACE_ID_CLAVE, "trace-test");

        try {
            Map<String, String> resultado = proxy.ejecutar(Map.of(
                    "visible", "valor",
                    "imagenBase64", "secreto"));

            assertEquals("ok", resultado.get("estado"));
            assertEquals("trace-test", MDC.get(TraceContext.TRACE_ID_CLAVE));
            assertNull(MDC.get("spanId"));
            assertNull(MDC.get("spanDepth"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void trazar_restauraElMdcYRelanzaLaExcepcionOriginal() {
        ServicioPrueba proxy = proxy(new ServicioPrueba());
        MDC.put(TraceContext.TRACE_ID_CLAVE, "trace-test");

        try {
            assertThrows(IllegalStateException.class, proxy::fallar);
            assertEquals("trace-test", MDC.get(TraceContext.TRACE_ID_CLAVE));
            assertNull(MDC.get("spanId"));
            assertNull(MDC.get("spanDepth"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void trazarInterceptaLosServicesMientrasHayUnaTrazaActiva() {
        ServicioAutomatico proxy = proxy(new ServicioAutomatico());
        MDC.put(TraceContext.TRACE_ID_CLAVE, "trace-test");
        MDC.put("traceEnabled", "true");

        try {
            assertNotNull(proxy.ejecutar());
            assertEquals("trace-test", MDC.get(TraceContext.TRACE_ID_CLAVE));
            assertEquals("true", MDC.get("traceEnabled"));
            assertNull(MDC.get("spanId"));
        } finally {
            MDC.clear();
        }
    }

    private <T> T proxy(T objetivo) {
        AspectJProxyFactory factory = new AspectJProxyFactory(objetivo);
        factory.addAspect(new TrazabilidadAspect(new SanitizadorDeLogs(new ObjectMapper())));
        T proxy = factory.getProxy();
        assertTrue(AopUtils.isAopProxy(proxy));
        return proxy;
    }

    static class ServicioPrueba {

        @Trazar(excluir = {"imagenBase64"})
        public Map<String, String> ejecutar(Map<String, String> cuerpo) {
            return Map.of("estado", "ok");
        }

        @Trazar
        public void fallar() {
            throw new IllegalStateException("fallo controlado");
        }
    }

    @Service
    static class ServicioAutomatico {

        public String ejecutar() {
            return MDC.get("spanId");
        }
    }
}
