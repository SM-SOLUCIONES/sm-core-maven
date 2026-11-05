package com.blue.open.Core.Kernel.Proyeccion;

import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import lombok.Getter;
import lombok.Setter;

// DTO de guardado de prueba de PruebaEliminable: trae el status que aplicarCamposBase valida
@Getter
@Setter
public class PruebaEliminableDTO extends EntidadBaseDTO {

    private String nombre;
}
