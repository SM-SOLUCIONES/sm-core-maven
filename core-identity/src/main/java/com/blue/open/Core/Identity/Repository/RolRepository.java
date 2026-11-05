package com.blue.open.Core.Identity.Repository;

import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Kernel.Repository.EntidadRepositoryBorradoLogico;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de la entidad Rol. Tiene consultas por nombre, por empresa (roles globales con
 * idEmpresa null o propios de una empresa) y por rol por defecto.
 */
@Repository
public interface RolRepository extends EntidadRepositoryBorradoLogico<Rol> {

    // Rol por nombre, de cualquier empresa
    Optional<Rol> findFirstByNombre(String nombre);

    // Roles con esos uuids, con sus permisos cargados en la misma consulta (evita N+1)
    @Query("SELECT DISTINCT r FROM Rol r LEFT JOIN FETCH r.permisos WHERE r.uuid IN :uuids")
    List<Rol> buscarPorUuidsConPermisos(@Param("uuids") List<String> uuids);

    // Rol global (sin empresa) por nombre
    Optional<Rol> findFirstByNombreAndIdEmpresaIsNull(String nombre);

    // Rol de una empresa por nombre
    Optional<Rol> findFirstByNombreAndIdEmpresa(String nombre, Long idEmpresa);

    // Primer rol de la empresa con el valor de porDefecto indicado
    Optional<Rol> findFirstByIdEmpresaAndPorDefecto(Long idEmpresa, Integer porDefecto);

    // Todos los roles de la empresa con el valor de porDefecto indicado
    List<Rol> findAllByIdEmpresaAndPorDefecto(Long idEmpresa, Integer porDefecto);
}
