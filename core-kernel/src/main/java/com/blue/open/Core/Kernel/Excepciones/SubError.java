package com.blue.open.Core.Kernel.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;

// Detalle de un error puntual dentro de errores (ej: un campo que no paso una validacion @Valid)
public class SubError {
    @Descripcion("Campo (o dato) que no pasó la validación")
    private String nombre;
    @Descripcion("Mensaje para el usuario final")
    private String mensaje;

    // Constructor vacio para Jackson
    public SubError() {
    }

    // Error de un campo con su mensaje para el usuario final
    public SubError(String nombre, String mensaje) {
        this.nombre = nombre;
        this.mensaje = mensaje;
    }

    // Campo que no paso la validacion
    public String getNombre() {
        return nombre;
    }

    // Asigna el campo que no paso la validacion
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    // Mensaje para el usuario final
    public String getMensaje() {
        return mensaje;
    }

    // Asigna el mensaje para el usuario final
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
