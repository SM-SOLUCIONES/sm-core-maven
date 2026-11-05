package com.blue.open.Core.Protocolo.Jwt;

import com.blue.open.Core.Kernel.Repository.EntidadRepositoryBorradoLogico;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Repositorio de JwtRefreshToken: los refresh tokens emitidos al iniciar sesion con JWT.
 */
@Repository
public interface JwtRefreshTokenRepository
        extends EntidadRepositoryBorradoLogico<JwtRefreshToken> {

    // Refresh token por su valor, sin filtrar por status
    Optional<JwtRefreshToken> findByToken(String token);

    // Borra fisicamente todos los refresh tokens del usuario
    @Modifying
    @Transactional
    void deleteByIdUsuario(Long idUsuario);
}
