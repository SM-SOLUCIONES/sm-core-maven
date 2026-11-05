package com.blue.open.Core.Kernel.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
// Respuesta generica de una operacion que no devuelve un registro (ej. cambiar la contrasena)
public class RespuestaApiDTO {
    @Descripcion("Status HTTP de la operación")
    private int status;
    @Descripcion("Mensaje para el usuario final")
    private String mensaje;
    @Descripcion("Datos adicionales de la operación (null si no hay)")
    private Object datos;
}
