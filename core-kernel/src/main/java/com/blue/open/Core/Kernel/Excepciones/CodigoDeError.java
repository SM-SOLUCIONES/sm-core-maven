package com.blue.open.Core.Kernel.Excepciones;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.HttpStatus;

// Codigo estable que identifica que error ocurrio, independiente del texto de los mensajes.
// Lo implementa un enum por modulo del Core y uno del proyecto.
public interface CodigoDeError {

    // Identificador que viaja en el body. Estable: renombrarlo rompe a los clientes
    @JsonValue
    String codigo();

    // Status HTTP con el que responde este error
    HttpStatus status();

    // Mensaje para el usuario final cuando el call site no pasa uno propio
    String mensajePorDefecto();
}
