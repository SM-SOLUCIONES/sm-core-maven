package com.blue.open.Core.Crud.Borrado;

import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;
import com.blue.open.Core.Kernel.DTO.FiltroDePaginado;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import com.blue.open.Core.Kernel.Repository.EntidadRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Borrado logico: marca el registro con status = -1 y guarda quien y cuando lo borro.
 * Aplica a toda entidad que extienda EntidadConBorradoLogico. Es el comportamiento por defecto
 * y el unico que existia en Core 1.x.
 */
@Order(10)
@Component
public class BorradoLogico implements EstrategiaDeBorrado {

    @Override
    public boolean aplicaA(Class<?> claseDeEntidad) {
        return EntidadConBorradoLogico.class.isAssignableFrom(claseDeEntidad);
    }

    @Override
    public <T extends EntidadBase> Optional<T> buscarVigentePorUuid(EntidadRepository<T> repository, String uuid) {
        return repository.buscarPorUuid(uuid)
                .filter(modelo -> modelo instanceof EntidadConBorradoLogico conBorrado
                        && conBorrado.getStatus() != null && conBorrado.getStatus() == 0);
    }

    @Override
    public <T extends EntidadBase> T borrar(EntidadRepository<T> repository, T modelo, Long idUsuario) {
        EntidadConBorradoLogico conBorrado = (EntidadConBorradoLogico) modelo;
        conBorrado.setStatus(-1);
        conBorrado.setFechaDeBorrado(FechaHoraConfig.ahoraTimestamp());
        if (idUsuario != null) {
            conBorrado.setIdUsuarioDeBorrado(idUsuario);
        }
        return repository.save(modelo);
    }

    @Override
    public void filtrarVigentes(PedidoDePaginado pedido) {
        pedido.agregarFiltro(List.of(new FiltroDePaginado("status", "0")));
    }
}
