package com.blue.open.Core.Identity.Repository;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;

import java.util.Optional;

/**
 * Puerto de acceso a usuarios para el codigo del Core que no es generico (AuthService, protocolos
 * de autenticacion, inicializador de base). Devuelve siempre {@link UsuarioBase}: el Core nunca
 * nombra la clase concreta de usuario del proyecto.
 * Dependencias externas: la base de datos.
 * Recibe: los identificadores de busqueda. Responde: el usuario, o un Optional vacio si no existe.
 */
public interface UsuarioRepositorio {

    Optional<UsuarioBase> porUuid(String uuid);

    Optional<UsuarioBase> porId(Long id);

    Optional<UsuarioBase> porNombreDeUsuarioActivo(String nombreDeUsuario);

    Optional<UsuarioBase> porEmailActivo(String email);

    // Busca por email ignorando solo los borrados (status != -1), para validar emails ya en uso
    Optional<UsuarioBase> porEmailNoBorrado(String email);

    Optional<UsuarioBase> porSsoUuidActivo(String ssoUuid);

    Optional<UsuarioBase> porMalvinasLegajoActivo(String legajo);

    UsuarioBase guardar(UsuarioBase usuario);

    // Crea una instancia vacia de la entidad concreta de usuario del proyecto
    UsuarioBase nuevo();

    // Clase @Entity concreta que el proyecto usa para los usuarios
    Class<? extends UsuarioBase> getClaseDeUsuario();
}
