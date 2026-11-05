package com.blue.open.Core.Kernel.Excepciones;

import java.io.PrintWriter;
import java.io.StringWriter;

// Utilidades para armar el mensajeParaDesarrollador de una ExcepcionDeApi a partir de una excepcion inesperada
public final class ExcepcionUtils {

    private ExcepcionUtils() {
    }

    // Arma el mensaje para el log: contexto, clase y mensaje de la excepcion y su stack trace. No depende de servicios externos
    public static String construirMensajeParaDesarrollador(String contexto, Exception ex) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        ex.printStackTrace(printWriter);
        printWriter.flush();
        return contexto + " | " + ex.getClass().getName() + ": " + ex.getMessage() + System.lineSeparator()
                + stringWriter;
    }
}
