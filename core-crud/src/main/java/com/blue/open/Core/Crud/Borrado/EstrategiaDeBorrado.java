package com.blue.open.Core.Crud.Borrado;

import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.blue.open.Core.Kernel.Repository.EntidadRepository;

import java.util.Optional;

/**
 * Define como borra el Core una entidad. Reemplaza al borrado logico hardcodeado de Core 1.x.
 * Dependencias externas: ninguna.
 * Recibe: el repositorio y el registro sobre el que opera.
 * Responde: el registro vigente buscado, el registro borrado, y el filtro de vigencia del paginado.
 */
public interface EstrategiaDeBorrado {

    // true si esta estrategia es la que corresponde a esa entidad
    boolean aplicaA(Class<?> claseDeEntidad);

    // Optional vacio si no existe o si ya fue borrado, segun la estrategia
    <T extends EntidadBase> Optional<T> buscarVigentePorUuid(EntidadRepository<T> repository, String uuid);

    <T extends EntidadBase> T borrar(EntidadRepository<T> repository, T modelo, Long idUsuario);

    // Agrega (o no) el filtro que excluye los registros borrados del paginado
    void filtrarVigentes(PedidoDePaginado pedido);
}
