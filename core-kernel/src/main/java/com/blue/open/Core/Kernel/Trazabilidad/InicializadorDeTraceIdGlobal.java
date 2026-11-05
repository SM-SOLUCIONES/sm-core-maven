package com.blue.open.Core.Kernel.Trazabilidad;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.core.task.TaskDecorator;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import java.util.Map;

// Limpia el traceId del MDC al iniciar y cerrar el contexto, y lo propaga a los hilos asincronicos (TaskDecorator)
@Component
public class InicializadorDeTraceIdGlobal implements ServletContextListener, TaskDecorator {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        MDC.remove(TraceContext.TRACE_ID_CLAVE);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        MDC.remove(TraceContext.TRACE_ID_CLAVE);
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextoPadre = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> contextoAnterior = MDC.getCopyOfContextMap();
            if (contextoPadre == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(contextoPadre);
            }
            if (MDC.get(TraceContext.TRACE_ID_CLAVE) == null) {
                MDC.put(TraceContext.TRACE_ID_CLAVE, TraceContext.generar());
            }
            try {
                runnable.run();
            } finally {
                if (contextoAnterior == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(contextoAnterior);
                }
            }
        };
    }
}
