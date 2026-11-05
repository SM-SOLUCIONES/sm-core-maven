package com.blue.open.Core.Identity.Puertos;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;

/**
 * Un protocolo de autenticacion. Cada modulo core-protocolo-* registra el suyo como bean y
 * AuthService los recorre en orden segun la propiedad auth.protocolos.
 * Antes AuthService inyectaba las cinco implementaciones por nombre concreto, lo que obligaba a
 * tener todas las tablas y variables de entorno aunque el proyecto usara una sola.
 * Dependencias externas: las de cada protocolo.
 * Recibe: la request en curso (via RequestContextHolder).
 * Responde: el usuario autenticado, o null si la request no trae credenciales de ese protocolo.
 */
public interface EstrategiaDeAutenticacion {

    // Nombre con el que se habilita en la propiedad auth.protocolos (ej. "jwt", "basic", "tokens")
    String getProtocolo();

    UsuarioBase getUsuario();
}
