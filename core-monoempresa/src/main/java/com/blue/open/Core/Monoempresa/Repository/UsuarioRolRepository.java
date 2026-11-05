package com.blue.open.Core.Monoempresa.Repository;

import com.blue.open.Core.Kernel.Repository.EntidadRepositoryBorradoLogico;
import com.blue.open.Core.Monoempresa.Entidad.UsuarioRol;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de UsuarioRol (roles de un usuario en monoempresa). Tiene consultas para saber si el
 * usuario tiene un rol o permiso y para listar los nombres de todos sus permisos.
 */
@Repository
public interface UsuarioRolRepository extends EntidadRepositoryBorradoLogico<UsuarioRol> {

    // Roles asignados al usuario con el status indicado
    List<UsuarioRol> findAllByIdUsuarioAndStatus(Long idUsuario, Integer status);

    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UsuarioRol ur JOIN ur.rol r "
            + "WHERE ur.idUsuario = :idUsuario AND ur.status = 0 AND r.nombre IN :nombresDeRoles")
    // true si el usuario tiene vigente alguno de los roles indicados
    boolean existeRolParaUsuario(@Param("idUsuario") Long idUsuario, @Param("nombresDeRoles") List<String> nombresDeRoles);

    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UsuarioRol ur JOIN ur.rol r JOIN r.permisos p "
            + "WHERE ur.idUsuario = :idUsuario AND ur.status = 0 AND p.nombre IN :nombresDePermisos")
    // true si alguno de los roles vigentes del usuario otorga alguno de los permisos indicados
    boolean existePermisoParaUsuario(@Param("idUsuario") Long idUsuario,
            @Param("nombresDePermisos") List<String> nombresDePermisos);

    @Query("SELECT DISTINCT p.nombre FROM UsuarioRol ur JOIN ur.rol r JOIN r.permisos p "
            + "WHERE ur.idUsuario = :idUsuario AND ur.status = 0")
    // Nombres, sin repetir, de todos los permisos que otorgan los roles vigentes del usuario
    List<String> buscarNombresDePermisosAgregados(@Param("idUsuario") Long idUsuario);

    // true si el usuario tiene asignado el rol con ese uuid y el status indicado
    boolean existsByIdUsuarioAndRolUuidAndStatus(Long idUsuario, String rolUuid, Integer status);
}
