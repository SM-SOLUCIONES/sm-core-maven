package com.blue.open.Core.Identity.Entidad;

import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Permiso individual del sistema RBAC (ver {@link com.blue.open.Core.Identity.Auth.IPermiso}).
 * Relacion N-a-N con {@link Rol}: un permiso puede pertenecer a varios roles.
 */
@Setter
@Getter
@Table(name = "auth_permisos")
@Entity()
public class Permiso extends EntidadConBorradoLogico {
    protected String nombre;
    protected String descripcion;
}
