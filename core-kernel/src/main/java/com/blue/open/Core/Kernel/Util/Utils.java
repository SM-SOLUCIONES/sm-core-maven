package com.blue.open.Core.Kernel.Util;

// Funciones utilitarias estaticas de proposito general
public class Utils {
    // true si el texto no es null ni esta vacio o en blanco
    public static boolean esTextoValido(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }
}
