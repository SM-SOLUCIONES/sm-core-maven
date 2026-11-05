package com.blue.open.Core.Kernel.Trazabilidad;

import com.blue.open.Core.Kernel.Puertos.ProveedorDeConfiguracion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Registra el trace de ejecucion de cada llamada entrante: por que clase y metodo paso,
 * con nivel de anidamiento (sangria), resultado y tiempo. Reutiliza el traceId que ya
 * imprime logback (%X{traceId}), asi todas las lineas de una misma request quedan agrupadas.
 * Depende de: ProveedorDeConfiguracion (lee la clave DEBUG).
 * Espera: invocaciones a metodos de Controllers y Services del paquete com.blue.open.
 * Responde: logs INFO con formato "TRACE -> Clase.metodo()" y su cierre.
 * Solo actua cuando la configuracion DEBUG es "1" (misma condicion que RequestLoggingFilter).
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class TraceDeEjecucionAspect {

    // ObjectProvider: la dependencia es opcional (el proyecto puede no publicar un ProveedorDeConfiguracion)
    private final ObjectProvider<ProveedorDeConfiguracion> proveedorDeConfiguracion;

    private static final ThreadLocal<Integer> PROFUNDIDAD = ThreadLocal.withInitial(() -> 0);

    /*
     * Corta la recursion: resolver la clave DEBUG puede pasar por un Service del proyecto, que a su
     * vez entra en este mismo aspecto. Sin este guard el arranque termina en StackOverflowError.
     * Es el mismo mecanismo que ya usaba RequestLoggingFilter.
     */
    private static final ThreadLocal<Boolean> RESOLVIENDO_DEBUG = ThreadLocal.withInitial(() -> false);

    // Envuelve cada metodo de un Controller o Service: si DEBUG vale 1 loguea la entrada y la salida
    // (OK o la excepcion) con sangria por nivel y la duracion; si no, solo lo ejecuta.
    // Recibe: la invocacion interceptada. Responde: el resultado del metodo (o relanza su excepcion).
    @Around("execution(* com.blue.open..*Controller.*(..)) || execution(* com.blue.open..*Service.*(..))")
    public Object trazar(ProceedingJoinPoint pjp) throws Throwable {
        if (!debugHabilitado()) {
            return pjp.proceed();
        }

        String clase = pjp.getSignature().getDeclaringType().getSimpleName();
        String metodo = pjp.getSignature().getName();
        int nivel = PROFUNDIDAD.get();
        String sangria = "  ".repeat(nivel);

        log.info("TRACE {}-> {}.{}()", sangria, clase, metodo);
        PROFUNDIDAD.set(nivel + 1);
        long inicio = System.currentTimeMillis();
        try {
            Object resultado = pjp.proceed();
            log.info("TRACE {}<- {}.{}() OK ({} ms)", sangria, clase, metodo, System.currentTimeMillis() - inicio);
            return resultado;
        } catch (Throwable t) {
            log.info("TRACE {}<- {}.{}() EX {} ({} ms)", sangria, clase, metodo,
                    t.getClass().getSimpleName(), System.currentTimeMillis() - inicio);
            throw t;
        } finally {
            if (nivel == 0) {
                PROFUNDIDAD.remove();
            } else {
                PROFUNDIDAD.set(nivel);
            }
        }
    }

    private boolean debugHabilitado() {
        ProveedorDeConfiguracion proveedor = proveedorDeConfiguracion.getIfAvailable();
        if (proveedor == null || RESOLVIENDO_DEBUG.get()) {
            return false;
        }
        RESOLVIENDO_DEBUG.set(true);
        try {
            String valorDeDebug = proveedor.getValor("DEBUG", "0");
            return "1".equals(valorDeDebug);
        } catch (Exception e) {
            return false;
        } finally {
            RESOLVIENDO_DEBUG.set(false);
        }
    }
}
