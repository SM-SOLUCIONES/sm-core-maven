package com.blue.open.Core.Multiempresa.DTO;

import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import com.blue.open.Core.Kernel.Proyeccion.Coleccion;
import com.blue.open.Core.Kernel.Proyeccion.Columna;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Relacion de un usuario con una empresa (entidad UsuarioEmpresa) y sus roles en ella. Viaja como
 * lista rolesPorEmpresa en el DTO de usuario de multiempresa.
 */
@Getter
@Setter
public class UsuarioEmpresaDTO extends EntidadBaseDTO {
    @Descripcion("Uuid de la empresa")
    @Columna("empresa.uuid")
    private String empresaUuid;
    @Descripcion("Nombre de la empresa (solo lectura)")
    @Columna("empresa.nombre")
    private String empresaNombre;
    @Descripcion("Roles del usuario en esa empresa, por id ascendente")
    @Coleccion("roles")
    private List<RolDTO> roles;
}
