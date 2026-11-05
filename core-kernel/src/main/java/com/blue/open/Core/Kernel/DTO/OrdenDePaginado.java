package com.blue.open.Core.Kernel.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un criterio de orden del paginado. campo es la ruta JPA del atributo (ej: "nombre", "tipoVehiculo.nombre").
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrdenDePaginado {
    @Descripcion("Ruta del atributo por el que se ordena (ej. nombre, tipoVehiculo.nombre)")
    private String campo;
    @Descripcion("asc o desc")
    private String direccion; // asc o desc
}
