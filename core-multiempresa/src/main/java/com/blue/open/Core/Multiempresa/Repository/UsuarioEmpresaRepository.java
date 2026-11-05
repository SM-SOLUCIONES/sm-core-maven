package com.blue.open.Core.Multiempresa.Repository;

import com.blue.open.Core.Kernel.Repository.EntidadRepositoryBorradoLogico;
import com.blue.open.Core.Multiempresa.Entidad.UsuarioEmpresa;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Consultas del RBAC multi-empresa. Los roles de la empresa de sistema valen en cualquier
 * empresa: por eso todas las consultas aceptan tanto la empresa del contexto como la de sistema.
 */
public interface UsuarioEmpresaRepository
        extends EntidadRepositoryBorradoLogico<UsuarioEmpresa> {

    @Query("SELECT CASE WHEN COUNT(ue) > 0 THEN true ELSE false END FROM UsuarioEmpresa ue "
            + "WHERE ue.idEmpresa = :idEmpresa AND ue.idUsuario = :idUsuario AND ue.status = 0")
    boolean existeUsuarioEnEmpresa(@Param("idEmpresa") Long idEmpresa, @Param("idUsuario") Long idUsuario);

    @Query("SELECT CASE WHEN COUNT(ue) > 0 THEN true ELSE false END FROM UsuarioEmpresa ue JOIN ue.roles r "
            + "WHERE ue.idUsuario = :idUsuario AND ue.status = 0 "
            + "AND (ue.idEmpresa = :idSistema OR ue.idEmpresa = :idEmpresa) AND r.nombre IN :nombresDeRoles")
    boolean existeRolParaUsuario(@Param("idUsuario") Long idUsuario, @Param("idEmpresa") Long idEmpresa,
            @Param("idSistema") Long idSistema, @Param("nombresDeRoles") List<String> nombresDeRoles);

    @Query("SELECT CASE WHEN COUNT(ue) > 0 THEN true ELSE false END FROM UsuarioEmpresa ue JOIN ue.roles r JOIN r.permisos p "
            + "WHERE ue.idUsuario = :idUsuario AND ue.status = 0 "
            + "AND (ue.idEmpresa = :idSistema OR ue.idEmpresa = :idEmpresa) AND p.nombre IN :nombresDePermisos")
    boolean existePermisoParaUsuario(@Param("idUsuario") Long idUsuario, @Param("idEmpresa") Long idEmpresa,
            @Param("idSistema") Long idSistema, @Param("nombresDePermisos") List<String> nombresDePermisos);

    // Es SYSADMIN quien tiene el rol SYSADMIN en la empresa de sistema, no cualquiera que pertenezca a ella
    @Query("SELECT CASE WHEN COUNT(ue) > 0 THEN true ELSE false END FROM UsuarioEmpresa ue JOIN ue.roles r "
            + "WHERE ue.idUsuario = :idUsuario AND ue.status = 0 AND ue.idEmpresa = :idSistema AND r.nombre = 'SYSADMIN'")
    boolean esSysAdmin(@Param("idUsuario") Long idUsuario, @Param("idSistema") Long idSistema);

    @Query("SELECT DISTINCT p.nombre FROM UsuarioEmpresa ue JOIN ue.roles r JOIN r.permisos p "
            + "WHERE ue.idUsuario = :idUsuario AND ue.status = 0 AND (ue.idEmpresa = :idSistema OR ue.idEmpresa = :idEmpresa)")
    List<String> buscarNombresDePermisosAgregados(@Param("idUsuario") Long idUsuario, @Param("idEmpresa") Long idEmpresa,
            @Param("idSistema") Long idSistema);

    @Query("SELECT ue FROM UsuarioEmpresa ue WHERE ue.idUsuario = :idUsuario AND ue.idEmpresa = :idEmpresa AND ue.status = 0")
    Optional<UsuarioEmpresa> buscarPorUsuarioYEmpresa(@Param("idUsuario") Long idUsuario, @Param("idEmpresa") Long idEmpresa);

    List<UsuarioEmpresa> findAllByIdUsuarioAndStatus(Long idUsuario, Integer status);
}
