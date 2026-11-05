package com.blue.open.Core.Kernel.Trazabilidad;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Define las reglas comunes para crear y validar el identificador de trazabilidad HTTP.
 */
public final class TraceContext {

    public static final String TRACE_ID_ENCABEZADO = "X-Trace-Id";
    public static final String TRACE_ID_CLAVE = "traceId";

    private static final Pattern TRACE_ID_SEGURO = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private TraceContext() {
    }

    /**
     * Devuelve el traceId recibido si es seguro; de lo contrario, genera uno nuevo.
     * No depende de servicios externos y responde siempre con un valor apto para logs.
     */
    public static String resolver(String traceIdPedido) {
        if (traceIdPedido != null && TRACE_ID_SEGURO.matcher(traceIdPedido).matches()) {
            return traceIdPedido;
        }
        return generar();
    }

    /**
     * Genera un identificador unico para una nueva operacion de trazabilidad.
     */
    public static String generar() {
        return UUID.randomUUID().toString();
    }
}
