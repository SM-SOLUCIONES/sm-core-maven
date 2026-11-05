package com.blue.open.Core.Identity.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import com.blue.open.Core.Kernel.Proyeccion.Coleccion;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Rol con sus permisos (entidad Rol). Lo usan los endpoints del ABM de roles (/rol).
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RolDTO extends EntidadBaseDTO {
    @Descripcion("Nombre del rol. Los roles declarados en código (RolBaseEnum y los del proyecto) usan el nombre de su constante")
    @NotBlank(message = "El nombre del rol es obligatorio")
    private String nombre;
    @Descripcion("1 si es el rol que se asigna por defecto a los usuarios nuevos (solo puede haber uno), 0 si no")
    private Integer porDefecto = 0;
    @Descripcion("Permisos que otorga el rol, por id ascendente. Al guardar se asocian por uuid los permisos existentes y se ignoran los demás campos; null no modifica los permisos")
    @Coleccion("permisos")
    private List<PermisoDTO> permisos;
}
