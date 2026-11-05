package com.blue.open.Core.Identity.Puertos;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;

/**
 * Parte de la inicializacion de la base que depende del modulo de scoping.
 * En Core 1.x esto estaba dentro de InicializadorDeBaseService, que creaba a mano la empresa
 * "Sistema" (uuid 1) y la empresa "BlueOpenData" (uuid 2) en todos los proyectos.
 * Dependencias externas: la base de datos.
 * Recibe: el usuario inicial y el rol SYSADMIN ya sincronizados.
 * Responde: el id del contexto de sistema al que pertenecen los roles base (null en mono-empresa)
 * y la vinculacion del usuario inicial con ese rol.
 */
public interface InicializadorDeAutorizacion {

    Long idContextoDeSistema();

    void vincularSysadmin(UsuarioBase usuario, Rol rolSysadmin);
}
