package com.blue.open.Core.Kernel.Puertos;

import java.util.Set;

/**
 * Puerto que hace cumplir el permiso declarado en @Contrato antes de ejecutar un endpoint.
 * El kernel lee la anotacion (InterceptorDeContrato) pero no sabe quien es el usuario: eso lo resuelve este puerto.
 * Lo implementa core-identity (AutorizadorPorRolesYPermisos). Sin ese modulo, se usa {@link AutorizadorDenegarTodo}.
 * Dependencias externas: las de la implementacion.
 */
public interface AutorizadorDeEndpoint {

    // Recibe el permiso de @Contrato (PUBLICO, AUTENTICADO o roles/permisos).
    // Lanza ExcepcionDeApi(NO_AUTENTICADO) si el request no identifica a ningun usuario y el permiso exige sesion,
    // o ExcepcionDeApi(SIN_PERMISO) si el usuario autenticado no tiene el rol o permiso
    void autorizar(String[] permiso);

    // Nombres de roles y permisos que reconoce, para validar @Contrato al arrancar.
    // Vacio si no conoce ninguno: en ese caso no se validan los nombres
    Set<String> nombresValidos();
}
