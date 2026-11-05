package com.blue.open.Core.Kernel.Puertos;

import java.util.List;

/**
 * Puerto de autenticacion del kernel. Es lo unico que el kernel y el CRUD saben del usuario logeado.
 * Dependencias externas: ninguna (el kernel no conoce ninguna entidad de usuario).
 * Espera: la lista de roles/permisos requeridos como enums del proyecto.
 * Responde: el id del usuario actual para la auditoria, y si tiene o no los permisos pedidos.
 * Lo implementa core-identity (AuthService). Sin ese modulo, se usa {@link UsuarioActualAnonimo}.
 */
public interface ProveedorDeUsuarioActual {

    // Id del usuario logeado, o null si no hay sesion. Se usa para idUsuarioDeCreacion/Updated/Deleted.
    Long getIdUsuarioActual();

    // true si el usuario actual tiene alguno de los roles/permisos. Es la API para autorizar segun los datos del
    // request o del registro; el permiso fijo de un endpoint va en @Contrato
    boolean tienePermisos(List<Enum<?>> requeridos);
}
