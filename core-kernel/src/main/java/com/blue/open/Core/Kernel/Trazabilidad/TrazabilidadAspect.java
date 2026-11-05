package com.blue.open.Core.Kernel.Trazabilidad;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Intercepta operaciones marcadas con {@link Trazar} y genera spans anidados
 * dentro del traceId actual. No depende de servicios externos.
 */
@Aspect
@Component
@Order(0)
@Slf4j
public class TrazabilidadAspect {

    private static final String SPAN_ID_CLAVE = "spanId";
    private static final String SPAN_PROFUNDIDAD_CLAVE = "spanDepth";
    private static final String TRACE_HABILITADO_CLAVE = "traceEnabled";
    private static final String TRACE_CAMPOS_EXCLUIDOS_CLAVE = "traceExcludedFields";

    private final SanitizadorDeLogs sanitizadorDeLogs;

    // Recibe el SanitizadorDeLogs con el que se tapan los argumentos y resultados sensibles
    public TrazabilidadAspect(SanitizadorDeLogs sanitizadorDeLogs) {
        this.sanitizadorDeLogs = sanitizadorDeLogs;
    }

    /**
     * Inicia una trazabilidad explicita desde un controller anotado.
     */
    @Around("@annotation(trazar)")
    public Object trazar(ProceedingJoinPoint joinPoint, Trazar trazar) throws Throwable {
        return trazarOperacion(joinPoint, trazar, true);
    }

    /**
     * Intercepta automaticamente todos los metodos publicos de services mientras
     * existe una trazabilidad activa iniciada por un controller.
     */
    @Around("@target(org.springframework.stereotype.Service) && execution(public * com.blue.open..*(..))")
    public Object trazarService(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!"true".equals(MDC.get(TRACE_HABILITADO_CLAVE))) {
            return joinPoint.proceed();
        }
        return trazarOperacion(joinPoint, null, false);
    }

    private Object trazarOperacion(ProceedingJoinPoint joinPoint, Trazar trazar,
            boolean activarTrace) throws Throwable {
        Map<String, String> contextoAnterior = MDC.getCopyOfContextMap();
        String traceId = MDC.get(TraceContext.TRACE_ID_CLAVE);
        if (traceId == null) {
            traceId = TraceContext.generar();
            MDC.put(TraceContext.TRACE_ID_CLAVE, traceId);
        }
        if (activarTrace) {
            MDC.put(TRACE_HABILITADO_CLAVE, "true");
            MDC.put(TRACE_CAMPOS_EXCLUIDOS_CLAVE, String.join(",", camposExcluidos(trazar)));
        }

        String spanIdPadre = MDC.get(SPAN_ID_CLAVE);
        String spanId = generarSpanId();
        int profundidad = leerProfundidad(MDC.get(SPAN_PROFUNDIDAD_CLAVE)) + 1;
        String operacion = resolverOperacion(joinPoint, trazar);
        long inicioNanos = System.nanoTime();
        boolean registrarArgumentos = trazar == null || trazar.registrarArgumentos();
        boolean registrarResultado = trazar == null || trazar.registrarResultado();

        MDC.put(SPAN_ID_CLAVE, spanId);
        MDC.put(SPAN_PROFUNDIDAD_CLAVE, String.valueOf(profundidad));

        if (registrarArgumentos) {
            log.info("TRACE_START - spanId={} | parentSpanId={} | depth={} | operation={} | arguments={}",
                    spanId, valorOGuion(spanIdPadre), profundidad, operacion,
                    sanitizarArgumentos(joinPoint, trazar));
        } else {
            log.info("TRACE_START - spanId={} | parentSpanId={} | depth={} | operation={}",
                    spanId, valorOGuion(spanIdPadre), profundidad, operacion);
        }

        try {
            Object resultado = joinPoint.proceed();
            long duracionMs = milisTranscurridos(inicioNanos);
            if (registrarResultado) {
                log.info("TRACE_END - spanId={} | depth={} | operation={} | status=SUCCESS | durationMs={} | result={}",
                        spanId, profundidad, operacion, duracionMs,
                        sanitizarResultado(resultado, trazar));
            } else {
                log.info("TRACE_END - spanId={} | depth={} | operation={} | status=SUCCESS | durationMs={}",
                        spanId, profundidad, operacion, duracionMs);
            }
            return resultado;
        } catch (Throwable throwable) {
            log.error(
                    "TRACE_END - spanId={} | depth={} | operation={} | status=ERROR | durationMs={} | exceptionType={}",
                    spanId, profundidad, operacion, milisTranscurridos(inicioNanos), throwable.getClass().getName(), throwable);
            throw throwable;
        } finally {
            restaurarContexto(contextoAnterior);
        }
    }

    private String resolverOperacion(ProceedingJoinPoint joinPoint, Trazar trazar) {
        if (trazar != null && !trazar.nombre().isBlank()) {
            return trazar.nombre();
        }

        String nombreDeClase = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass().getSimpleName()
                : joinPoint.getSignature().getDeclaringType().getSimpleName();
        return nombreDeClase + "." + joinPoint.getSignature().getName();
    }

    private String sanitizarArgumentos(ProceedingJoinPoint joinPoint, Trazar trazar) {
        if (!(joinPoint.getSignature() instanceof MethodSignature methodSignature)) {
            return "[argumentos no disponibles]";
        }

        String[] nombresDeParametros = methodSignature.getParameterNames();
        Map<String, Object> argumentos = new LinkedHashMap<>();
        Object[] valores = joinPoint.getArgs();
        for (int indice = 0; indice < valores.length; indice++) {
            String nombreDeParametro = nombresDeParametros != null && indice < nombresDeParametros.length
                    ? nombresDeParametros[indice]
                    : "arg" + indice;
            argumentos.put(nombreDeParametro, valores[indice]);
        }
        return sanitizadorDeLogs.sanitizarValor(argumentos, camposExcluidos(trazar));
    }

    private String sanitizarResultado(Object resultado, Trazar trazar) {
        if (resultado == null) {
            return "null";
        }
        if (resultado instanceof ServletRequest || resultado instanceof ServletResponse) {
            return "[respuesta servlet omitida]";
        }

        Object valor = resultado;
        if (resultado instanceof ResponseEntity<?> responseEntity) {
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status", responseEntity.getStatusCode().value());
            respuesta.put("body", responseEntity.getBody());
            valor = respuesta;
        }
        return sanitizadorDeLogs.sanitizarValor(valor, camposExcluidos(trazar));
    }

    private String[] camposExcluidos(Trazar trazar) {
        if (trazar != null) {
            return trazar.excluir();
        }
        String camposExcluidos = MDC.get(TRACE_CAMPOS_EXCLUIDOS_CLAVE);
        if (camposExcluidos == null || camposExcluidos.isBlank()) {
            return new String[0];
        }
        return camposExcluidos.split(",");
    }

    private void restaurarContexto(Map<String, String> contextoAnterior) {
        if (contextoAnterior == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(contextoAnterior);
        }
    }

    private String generarSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private int leerProfundidad(String profundidad) {
        try {
            return profundidad == null ? 0 : Integer.parseInt(profundidad);
        } catch (NumberFormatException excepcion) {
            return 0;
        }
    }

    private long milisTranscurridos(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    private String valorOGuion(String valor) {
        return valor == null ? "-" : valor;
    }
}
