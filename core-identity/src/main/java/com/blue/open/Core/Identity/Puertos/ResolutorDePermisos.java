package com.blue.open.Core.Identity.Puertos;

import java.util.List;
import java.util.Set;

/**
 * Puerto que resuelve que roles y permisos tiene un usuario. Es el unico punto donde el Core
 * decide como se vincula un usuario con sus roles, y por eso lo implementa el modulo de scoping:
 * core-monoempresa (usuario -> rol directo) o core-multiempresa (usuario -> empresa -> rol).
 * Dependencias externas: ninguna. Recibe: el id del usuario y los nombres a chequear.
 * Responde: si tiene alguno de esos roles/permisos, el set completo de permisos, y si es sysadmin.
 */
public interface ResolutorDePermisos {

    boolean tieneAlgunRolOPermiso(Long idUsuario, List<String> roles, List<String> permisos);

    Set<String> permisosDe(Long idUsuario);

    boolean esSysAdmin(Long idUsuario);
}
