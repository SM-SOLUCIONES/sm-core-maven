package com.blue.open.Core.Protocolo.ApiKey;

import com.blue.open.Core.Kernel.Repository.EntidadRepositoryBorradoLogico;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de los tokens de acceso personales. Las lecturas que responden los endpoints arman AccessTokenDTO
 * con una consulta propia: el codigo es @NoProyectable (el ABM generico nunca lo lee), pero el duenio del token si lo ve.
 * El usuarioUuid no sale de la consulta (el token referencia al usuario por id): lo completa quien conoce al usuario.
 */
public interface AccessTokenRepository
        extends EntidadRepositoryBorradoLogico<AccessToken> {

    Optional<AccessToken> findFirstByCodigo(String codigo);

    Optional<AccessToken> findFirstByNombreAndIdUsuarioAndStatus(String nombre, Long idUsuario, Integer status);

    List<AccessToken> findAllByIdUsuarioAndStatus(Long idUsuario, Integer status);

    // Token por uuid, sin filtrar por status (la respuesta de crear y de eliminar)
    @Query("SELECT new com.blue.open.Core.Protocolo.ApiKey.AccessTokenDTO(t.uuid, t.status, t.fechaDeCreacion, t.nombre, "
            + "t.codigo, t.activo) FROM AccessToken t WHERE t.uuid = :uuid")
    Optional<AccessTokenDTO> buscarDtoPorUuid(@Param("uuid") String uuid);

    // Tokens vigentes de un usuario, por id ascendente
    @Query("SELECT new com.blue.open.Core.Protocolo.ApiKey.AccessTokenDTO(t.uuid, t.status, t.fechaDeCreacion, t.nombre, "
            + "t.codigo, t.activo) FROM AccessToken t WHERE t.idUsuario = :idUsuario AND t.status = 0 ORDER BY t.id")
    List<AccessTokenDTO> listarDtosVigentesPorUsuario(@Param("idUsuario") Long idUsuario);
}
