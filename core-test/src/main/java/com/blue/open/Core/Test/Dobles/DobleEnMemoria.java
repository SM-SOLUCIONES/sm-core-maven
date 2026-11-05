package com.blue.open.Core.Test.Dobles;

/**
 * Doble en memoria de un servicio externo, registrado solo con el profile "test". PruebaDeContrato llama a
 * {@link #limpiar()} antes de cada test para que ningun test vea lo que dejo otro.
 */
public interface DobleEnMemoria {

    // Descarta todo lo acumulado (archivos, mails)
    void limpiar();
}
