package com.blue.open.Core.Kernel.DTO;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
// Pedido de borrado de un archivo publico de SM3 (lo consume SM3Service.borrar). url es el campo que espera la API de SM3
public class BorrarArchivoRequest {
    private String url;
}
