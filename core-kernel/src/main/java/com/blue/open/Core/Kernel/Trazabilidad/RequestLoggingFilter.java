package com.blue.open.Core.Kernel.Trazabilidad;

import com.blue.open.Core.Kernel.Puertos.ProveedorDeConfiguracion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import org.slf4j.MDC;

/**
 * Filtro que asigna el traceId a cada request (MDC y header de respuesta) y loguea su inicio y fin.
 * Si la configuracion DEBUG vale 1, loguea tambien el cuerpo del request y de la respuesta, sanitizados.
 * Dependencias externas: ninguna (DEBUG se lee del ProveedorDeConfiguracion, si existe).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final ThreadLocal<Boolean> RESOLVIENDO_DEBUG =
            ThreadLocal.withInitial(() -> false);

    private static final int LIMITE_DE_CACHE_DEL_REQUEST = 100_000;

    // ObjectProvider: la dependencia es opcional (el proyecto puede no publicar un ProveedorDeConfiguracion)
    private final ObjectProvider<ProveedorDeConfiguracion> proveedorDeConfiguracion;

    private final SanitizadorDeLogs sanitizadorDeLogs;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceIdAnterior = MDC.get(TraceContext.TRACE_ID_CLAVE);
        String traceId = TraceContext.resolver(request.getHeader(TraceContext.TRACE_ID_ENCABEZADO));
        MDC.put(TraceContext.TRACE_ID_CLAVE, traceId);
        response.setHeader(TraceContext.TRACE_ID_ENCABEZADO, traceId);
        long inicioNanos = System.nanoTime();

        try {
            boolean conDebug = debugHabilitado();

            ContentCachingRequestWrapper requestCacheado = new ContentCachingRequestWrapper(request, LIMITE_DE_CACHE_DEL_REQUEST);

            String metodo = requestCacheado.getMethod();
            String uri = requestCacheado.getRequestURI();

            log.info("REQUEST_START - Method: {} | URI: {} | Content-Type: {} | Content-Length: {}",
                    metodo, uri, requestCacheado.getContentType(), requestCacheado.getContentLengthLong());

            if (conDebug && debeCachearResponse(requestCacheado)) {
                ContentCachingResponseWrapper responseCacheado = new ContentCachingResponseWrapper(response);
                try {
                    filterChain.doFilter(requestCacheado, responseCacheado);
                } finally {
                    registrarCuerpoDelRequest(requestCacheado, metodo);
                    registrarResponse(responseCacheado);
                    responseCacheado.copyBodyToResponse();
                    registrarFinDelRequest(response, inicioNanos);
                }
            } else {
                try {
                    filterChain.doFilter(requestCacheado, response);
                } finally {
                    if (conDebug) {
                        registrarCuerpoDelRequest(requestCacheado, metodo);
                    }
                    registrarFinDelRequest(response, inicioNanos);
                }
            }
        } finally {
            if (traceIdAnterior == null) {
                MDC.remove(TraceContext.TRACE_ID_CLAVE);
            } else {
                MDC.put(TraceContext.TRACE_ID_CLAVE, traceIdAnterior);
            }
        }
    }

    private boolean debeCachearResponse(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.endsWith("/export/excel");
    }

    private boolean debugHabilitado() {
        ProveedorDeConfiguracion proveedor = proveedorDeConfiguracion.getIfAvailable();
        if (proveedor == null) {
            return false;
        }

        // Evita que getValor vuelva a entrar recursivamente al aspecto.
        if (Boolean.TRUE.equals(RESOLVIENDO_DEBUG.get())) {
            return false;
        }

        try {
            RESOLVIENDO_DEBUG.set(true);

            String valorDeDebug = proveedor.getValor("DEBUG", "0");
            return "1".equals(valorDeDebug);
        } catch (Exception e) {
            return false;
        } finally {
            RESOLVIENDO_DEBUG.remove();
        }
    }

    private void registrarCuerpoDelRequest(ContentCachingRequestWrapper request, String metodo) {
        if ("POST".equalsIgnoreCase(metodo)
                || "PUT".equalsIgnoreCase(metodo)
                || "PATCH".equalsIgnoreCase(metodo)) {
            try {
                byte[] cuerpo = request.getContentAsByteArray();
                if (cuerpo.length > 0) {
                    String contenido = new String(cuerpo, request.getCharacterEncoding() != null
                            ? request.getCharacterEncoding() : "UTF-8");
                    contenido = sanitizadorDeLogs.sanitizarCuerpo(contenido, request.getContentType());
                    log.info("REQUEST - Payload: {}", contenido);
                }
            } catch (Exception e) {
                log.warn("Could not read request body", e);
            }
        }
    }

    private void registrarResponse(ContentCachingResponseWrapper response) {
        try {
            log.info("RESPONSE - Status Code: {}", response.getStatus());
            byte[] cuerpo = response.getContentAsByteArray();
            if (cuerpo.length > 0) {
                String cuerpoDelResponse = new String(cuerpo, response.getCharacterEncoding() != null
                        ? response.getCharacterEncoding() : "UTF-8");
                cuerpoDelResponse = sanitizadorDeLogs.sanitizarCuerpo(cuerpoDelResponse, response.getContentType());
                log.info("RESPONSE - Body: {}", cuerpoDelResponse);
            }
        } catch (Exception e) {
            log.warn("Could not read response", e);
        }
    }

    private void registrarFinDelRequest(HttpServletResponse response, long inicioNanos) {
        long duracionMs = (System.nanoTime() - inicioNanos) / 1_000_000;
        log.info("REQUEST_END - Status Code: {} | DurationMs: {}", response.getStatus(), duracionMs);
    }
}
