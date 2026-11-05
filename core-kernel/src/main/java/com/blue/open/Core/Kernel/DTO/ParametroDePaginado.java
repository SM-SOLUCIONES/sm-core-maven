package com.blue.open.Core.Kernel.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parametro con nombre dentro de PedidoDePaginado (POST /{controller}/paginar), que interpreta
 * cada endpoint a su criterio.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ParametroDePaginado {
    @Descripcion("Nombre del parámetro")
    private String nombre;
    @Descripcion("Valor del parámetro como texto")
    private String valor;
}
