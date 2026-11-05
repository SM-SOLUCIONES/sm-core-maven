package com.blue.open.Core.Identity.Puertos;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;

import java.util.List;

/**
 * Aplica sobre un usuario los roles que vienen de un sistema de autenticacion externo (SSO,
 * Malvinas). Como la relacion usuario-rol depende del modulo de scoping, el protocolo externo no
 * la escribe directamente: delega en este puerto.
 * Dependencias externas: la base de datos.
 * Recibe: el usuario, el nombre del contexto externo (ej. "sso") y los roles que declara ese sistema.
 * Responde: nada; deja la relacion sincronizada. Si el proyecto no tiene modulo de scoping, no hace nada.
 */
public interface SincronizadorDeRolesExternos {
    void sincronizar(UsuarioBase usuario, String contextoExterno, List<String> nombresDeRoles);
}
