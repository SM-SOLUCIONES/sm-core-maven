package com.blue.open.Core.Identity.Repository;

import com.blue.open.Core.Identity.Entidad.Permiso;
import com.blue.open.Core.Kernel.Repository.EntidadRepositoryBorradoLogico;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de la entidad Permiso.
 */
@Repository
public interface PermisoRepository extends EntidadRepositoryBorradoLogico<Permiso> {
    // Permiso por su nombre persistido (ej. permission.view), sin filtrar por status
    Optional<Permiso> findFirstByNombre(String nombre);
}
