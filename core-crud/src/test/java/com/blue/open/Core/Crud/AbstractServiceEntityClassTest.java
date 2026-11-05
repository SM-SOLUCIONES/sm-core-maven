package com.blue.open.Core.Crud;

import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que AbstractService resuelva la clase de la entidad tanto con herencia directa como a
 * traves de una clase generica intermedia (el caso de EmpresaService y UsuarioService, que extienden
 * EmpresaBaseService / UsuarioBaseService). Hasta 2.0.0 el segundo caso quedaba en null y rompia el
 * alta y el borrado de esas entidades.
 */
class AbstractServiceEntityClassTest {

    static class Producto extends EntidadBase {
    }

    static class ProductoDTO extends EntidadBaseDTO {
    }

    static class ServicioDirecto extends AbstractService<Producto, ProductoDTO, ProductoDTO> {
    }

    static class ServicioBaseGenerico<E extends EntidadBase, D extends EntidadBaseDTO>
            extends AbstractService<E, D, D> {
    }

    static class ServicioConIntermedio extends ServicioBaseGenerico<Producto, ProductoDTO> {
    }

    @Test
    void herenciaDirecta_resuelveLaEntidad() {
        assertEquals(Producto.class, new ServicioDirecto().getClaseDeEntidad());
    }

    @Test
    void conClaseGenericaIntermedia_resuelveLaEntidad() {
        assertEquals(Producto.class, new ServicioConIntermedio().getClaseDeEntidad());
    }
}
