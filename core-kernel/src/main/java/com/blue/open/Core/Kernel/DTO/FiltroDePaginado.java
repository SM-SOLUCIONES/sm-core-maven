package com.blue.open.Core.Kernel.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un filtro del paginado. campo es la ruta JPA del atributo, relativa a la entidad paginada:
 * "nombre", o navegando relaciones "tipoVehiculo.nombre" (ver ConsultaDePaginado).
 * operacion: =, !=, <>, >, <, >=, <=, like, not like, in, not in, is null, is not null, group.
 * valor: siempre texto; se convierte al tipo del atributo. En in/not in es una lista JSON.
 */
@Getter
@Setter
@NoArgsConstructor
public class FiltroDePaginado {
    @Descripcion("Ruta del atributo a filtrar, relativa a la entidad paginada (ej. nombre, tipoVehiculo.nombre)")
    private String campo = "";
    @Descripcion("Operador: =, !=, <>, >, <, >=, <=, like, not like, in, not in, is null, is not null, group")
    private String operacion = "=";
    @Descripcion("Valor como texto; se convierte al tipo del atributo. En in/not in es una lista JSON")
    private String valor = "";

    // Filtro por igualdad sobre el campo
    public FiltroDePaginado(String campo, String valor) {
        this.campo = campo;
        this.valor = valor;
    }

    // Filtro con la operacion indicada
    public FiltroDePaginado(String campo, String operacion, String valor) {
        this.campo = campo;
        this.operacion = operacion;
        this.valor = valor;
    }
}
