package com.blue.open.Core.Kernel.Repository;

import com.blue.open.Core.Kernel.DTO.Pagina;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;

/**
 * Paginado disponible en todos los repositorios (lo implementa EntidadRepositoryBase).
 * Responde directamente el DTO pedido: la consulta trae solo las columnas de sus campos (ver MotorDeConsultas).
 */
public interface PaginadoAutomatico<T> {
    <D> Pagina<D> paginar(PedidoDePaginado pedido, Class<D> claseDto);
}
