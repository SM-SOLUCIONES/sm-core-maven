package com.blue.open.Core.Crud.Borrado;

import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.blue.open.Core.Kernel.Repository.EntidadRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Borrado fisico: el DELETE saca la fila de la tabla. Aplica a las entidades que extienden
 * EntidadBase sin extender EntidadConBorradoLogico, es decir, las que no tienen columna status.
 * Al no haber status, el paginado no agrega ningun filtro de vigencia.
 */
@Order(20)
@Component
public class BorradoFisico implements EstrategiaDeBorrado {

    @Override
    public boolean aplicaA(Class<?> claseDeEntidad) {
        return EntidadBase.class.isAssignableFrom(claseDeEntidad);
    }

    @Override
    public <T extends EntidadBase> Optional<T> buscarVigentePorUuid(EntidadRepository<T> repository, String uuid) {
        return repository.buscarPorUuid(uuid);
    }

    @Override
    public <T extends EntidadBase> T borrar(EntidadRepository<T> repository, T modelo, Long idUsuario) {
        repository.delete(modelo);
        return modelo;
    }

    @Override
    public void filtrarVigentes(PedidoDePaginado pedido) {
        // Un registro borrado fisicamente no esta en la tabla: no hay nada que filtrar
    }
}
