package com.blue.open.Core.Identity.Puertos;

import com.blue.open.Core.Identity.Auth.IPermiso;
import com.blue.open.Core.Identity.Auth.IRol;

/**
 * Catalogo de roles y permisos propios del proyecto, que el Core sincroniza contra la base al
 * arrancar. Reemplaza al Class.forName("com.blue.open.Code.Auth.PermissionEnum") de Core 1.x, que
 * obligaba a que todo proyecto tuviera esos enums en ese paquete exacto.
 * Dependencias externas: ninguna. Recibe: nada.
 * Responde: los roles y permisos declarados por el proyecto (vacio si no define ninguno).
 */
public interface CatalogoDeAutorizacion {

    IRol[] getRoles();

    IPermiso[] getPermisos();
}
