package com.blue.open.Core.Multiempresa.Repository;

import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;

import java.util.Optional;

/**
 * Puerto de acceso a empresas para el codigo del Core que no es generico.
 * Devuelve siempre {@link EmpresaBase}: el Core nunca nombra la clase concreta del proyecto.
 * Dependencias externas: la base de datos.
 * Recibe: uuid, id o nombre. Responde: la empresa, o un Optional vacio si no existe.
 */
public interface EmpresaRepositorio {

    Optional<EmpresaBase> porUuid(String uuid);

    Optional<EmpresaBase> porUuidActiva(String uuid);

    Optional<EmpresaBase> porId(Long id);

    Optional<EmpresaBase> porNombreActiva(String nombre);

    EmpresaBase guardar(EmpresaBase empresa);

    EmpresaBase nueva();
}
