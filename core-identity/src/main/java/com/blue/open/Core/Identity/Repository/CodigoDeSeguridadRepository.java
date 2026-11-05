package com.blue.open.Core.Identity.Repository;

import com.blue.open.Core.Identity.Entidad.CodigoDeSeguridad;
import com.blue.open.Core.Kernel.Repository.EntidadRepositoryBorradoLogico;

import java.util.Optional;

/**
 * Repositorio de CodigoDeSeguridad: los codigos de verificacion de cuenta y de recuperacion de
 * contrasena que se envian por email.
 */
public interface CodigoDeSeguridadRepository extends EntidadRepositoryBorradoLogico<CodigoDeSeguridad> {

    // Ultimo codigo emitido para el usuario con el status indicado
    Optional<CodigoDeSeguridad> findFirstByIdUsuarioAndStatusOrderByFechaDeEmisionDesc(Long idUsuario, Integer status);

    // Codigo del usuario con ese valor, sin filtrar por status
    Optional<CodigoDeSeguridad> findFirstByCodigoAndIdUsuario(String codigo, Long idUsuario);
}
