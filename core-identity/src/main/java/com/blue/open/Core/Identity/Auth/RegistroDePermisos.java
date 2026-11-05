package com.blue.open.Core.Identity.Auth;

import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;

/**
 * Registro estatico de los permisos que declara el proyecto (PermisoEnum), para que RolBaseEnum
 * se los otorgue a SYSADMIN y ADMIN sin que el Core conozca las clases del proyecto.
 */
public class RegistroDePermisos {
    private static final Set<IPermiso> permisosAdicionales = Collections.synchronizedSet(new HashSet<>());

    // Agrega permisos al registro. Lo llama el PermisoEnum del proyecto al cargarse.
    // Recibe: los permisos (ignora null). Responde: nada.
    public static void registrar(IPermiso[] permisos) {
        if (permisos != null) {
            permisosAdicionales.addAll(Arrays.asList(permisos));
        }
    }

    // Responde: una copia de los permisos registrados por el proyecto
    public static Set<IPermiso> getPermisosAdicionales() {
        return new HashSet<>(permisosAdicionales);
    }
}
