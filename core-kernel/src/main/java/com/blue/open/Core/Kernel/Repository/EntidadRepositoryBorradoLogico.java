package com.blue.open.Core.Kernel.Repository;

import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repositorio de entidades con borrado logico: agrega los buscadores que filtran por status = 0.
 * No existe para entidades de borrado fisico, donde un registro borrado simplemente no esta en la tabla.
 */
@NoRepositoryBean
public interface EntidadRepositoryBorradoLogico<T extends EntidadConBorradoLogico> extends EntidadRepository<T> {

    @Query("SELECT e FROM #{#entityName} e WHERE e.uuid = :uuid AND e.status = 0")
    Optional<T> buscarVigentePorUuid(@Param("uuid") String uuid);

    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.status = 0")
    Optional<T> buscarVigentePorId(@Param("id") Long id);
}
