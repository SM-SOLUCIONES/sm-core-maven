package com.blue.open.Core.Identity.Puertos;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;

/**
 * Decide si una identidad que llega por un protocolo de autenticacion externo (Malvinas) puede entrar al
 * sistema. El protocolo la consulta despues de buscar el usuario local vigente y antes de crearlo o de
 * guardarle los datos que trae el token, asi un rechazo no deja ninguna fila creada ni modificada.
 * Dependencias externas: las que necesite la implementacion del proyecto (por defecto, ninguna).
 * Recibe: el protocolo ("malvinas"), el identificador externo (el legajo) y el usuario local vigente con ese
 * identificador, o null si todavia no existe (un parametro no es Optional, ver consultas.md).
 * Responde: nada si el acceso esta permitido. Si no lo esta, lanza ExcepcionDeApi y el protocolo la propaga
 * tal cual, sin envolverla.
 */
public interface PoliticaDeAccesoExterno {
    void validar(String protocolo, String identificador, UsuarioBase usuarioVigente);
}
