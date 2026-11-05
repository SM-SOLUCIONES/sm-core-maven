package com.blue.open.Core.Identity.Auth;

import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Roles que trae el Core: SYSADMIN tiene todos los permisos y ADMIN todos salvo los de administrar
 * empresas y permisos. Ambos suman ademas los permisos que el proyecto registra en RegistroDePermisos.
 */
@AllArgsConstructor
public enum RolBaseEnum implements IRol {

    SYSADMIN(todos()),
    ADMIN(excluir(todos(),
            PermisoBaseEnum.EMPRESA_NO_REQUERIDA,
            PermisoBaseEnum.EMPRESA_CREAR,
            PermisoBaseEnum.EMPRESA_BORRAR,
            PermisoBaseEnum.EMPRESA_EDITAR,
            PermisoBaseEnum.PERMISO_CREAR,
            PermisoBaseEnum.PERMISO_EDITAR,
            PermisoBaseEnum.PERMISO_BORRAR));

    private final Set<IPermiso> permisos;

    @Override
    public Set<IPermiso> getPermisos() {
        Set<IPermiso> todosLosPermisos = new HashSet<>(permisos);
        if (this == SYSADMIN || this == ADMIN) {
            todosLosPermisos.addAll(RegistroDePermisos.getPermisosAdicionales());
        }
        return todosLosPermisos;
    }

    private static Set<IPermiso> todos() {
        return new HashSet<>(Arrays.asList(PermisoBaseEnum.values()));
    }

    private static Set<IPermiso> excluir(Set<IPermiso> base, IPermiso... exclusiones) {
        Set<IPermiso> resultado = new HashSet<>(base);
        for (IPermiso exclusion : exclusiones) {
            resultado.remove(exclusion);
        }
        return resultado;
    }
}
