package com.blue.open.Core.Kernel.Repository;

import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repositorio base de cualquier entidad del Core. Solo expone lo que no depende del modo de borrado:
 * busqueda por uuid/id y el motor de paginado.
 * Si la entidad tiene borrado logico, su repositorio debe extender {@link EntidadRepositoryBorradoLogico}.
 */
@NoRepositoryBean
public interface EntidadRepository<T extends EntidadBase>
        extends CrudRepository<T, Long>, JpaSpecificationExecutor<T>, PaginadoAutomatico<T> {

    @Query("SELECT e FROM #{#entityName} e WHERE e.uuid = :uuid")
    Optional<T> buscarPorUuid(@Param("uuid") String uuid);

    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id")
    Optional<T> buscarPorId(@Param("id") Long id);
}
