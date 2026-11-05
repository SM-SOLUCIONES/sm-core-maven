package com.blue.open.Core.Multiempresa.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Campos comunes de la empresa (entidad EmpresaBase). Lo extiende el EmpresaDTO del proyecto, que usa
 * el ABM de empresas, el contexto de empresa y el registro publico de usuario y empresa.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class EmpresaBaseDTO extends EntidadBaseDTO {
    @Descripcion("Nombre de la empresa")
    @NotBlank(message = "El nombre de la empresa es obligatorio")
    private String nombre;
    @Descripcion("Descripción libre de la empresa")
    private String descripcion;
    @Descripcion("Identificador de la empresa para URLs (slug)")
    private String ruta;
    @Descripcion("Código corto de la empresa")
    private String codigo;
}
