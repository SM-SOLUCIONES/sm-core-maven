package com.blue.open.Core.Identity.Auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Permisos que trae el Core para administrar permisos, empresas, usuarios y roles.
 * nombre es el valor persistido en la tabla de permisos.
 */
@Getter
@AllArgsConstructor
public enum PermisoBaseEnum implements IPermiso {
    PERMISO_VER("permission.view"),
    PERMISO_CREAR("permission.create"),
    PERMISO_EDITAR("permission.edit"),
    PERMISO_BORRAR("permission.delete"),
    EMPRESA_NO_REQUERIDA("company.notneed"),
    EMPRESA_VER("company.view"),
    EMPRESA_CREAR("company.create"),
    EMPRESA_EDITAR("company.edit"),
    EMPRESA_BORRAR("company.delete"),
    USUARIO_VER("user.view"),
    USUARIO_CREAR("user.create"),
    USUARIO_EDITAR("user.edit"),
    USUARIO_BORRAR("user.delete"),
    ROL_VER("role.view"),
    ROL_CREAR("role.create"),
    ROL_EDITAR("role.edit"),
    ROL_BORRAR("role.delete");

    private final String nombre;
}
