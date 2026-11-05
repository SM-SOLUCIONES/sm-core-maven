package com.blue.open.Core.Identity.Auth;

/**
 * Permiso declarado en codigo. Lo implementan PermisoBaseEnum (Core) y el PermisoEnum del proyecto;
 * al arrancar se sincronizan con la tabla de permisos (entidad Permiso).
 */
public interface IPermiso {
    // Nombre persistido del permiso (ej. permission.view)
    String getNombre();
    // Nombre de la constante del enum, el que se usa en @Contrato
    String name();
}
