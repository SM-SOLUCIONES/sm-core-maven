package com.blue.open.Core.Identity.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Permiso del sistema (entidad Permiso). Lo responden los endpoints de lectura de permisos y
 * viaja como lista dentro de RolDTO.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PermisoDTO extends EntidadBaseDTO {
    @Descripcion("Nombre del permiso (ej. permission.view). Los permisos se sincronizan desde código al arrancar")
    private String nombre;
}
