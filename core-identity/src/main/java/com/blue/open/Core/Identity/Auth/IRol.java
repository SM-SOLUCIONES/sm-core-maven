package com.blue.open.Core.Identity.Auth;

import java.util.Set;

/**
 * Rol declarado en codigo, con los permisos que otorga. Lo implementan RolBaseEnum (Core) y el
 * RolEnum del proyecto; al arrancar se sincronizan con la tabla de roles (entidad Rol).
 */
public interface IRol {
    // Nombre de la constante del enum: es el nombre persistido del rol
    String name();
    // Permisos que otorga el rol
    Set<? extends IPermiso> getPermisos();
}
