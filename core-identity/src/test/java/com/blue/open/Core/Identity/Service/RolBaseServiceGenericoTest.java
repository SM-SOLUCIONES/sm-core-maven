package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Identity.Entidad.Rol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que RolBaseService resuelve su DTO desde el parametro generico (Core 3.5.0): un proyecto con su propio
 * DTO (que extiende RolDTO) obtiene, pagina, guarda y borra con esa clase, y un proyecto que extiende sin parametro
 * (tipo crudo, como antes de 3.5.0) sigue usando RolDTO.
 */
class RolBaseServiceGenericoTest {

    @Test
    void conUnDtoPropio_obtenerYPaginarUsanEseDto() {
        ServicioConDtoPropio servicio = new ServicioConDtoPropio();

        assertEquals(Rol.class, servicio.entidad());
        assertEquals(RolPropioDTO.class, servicio.dto());
        assertEquals(RolPropioDTO.class, servicio.paginado());
    }

    @Test
    void conRolDto_usaRolDto() {
        ServicioConRolDto servicio = new ServicioConRolDto();

        assertEquals(RolDTO.class, servicio.dto());
        assertEquals(RolDTO.class, servicio.paginado());
    }

    @Test
    void sinParametro_comoAntesDe350_usaRolDto() {
        ServicioSinParametro servicio = new ServicioSinParametro();

        assertEquals(Rol.class, servicio.entidad());
        assertEquals(RolDTO.class, servicio.dto());
        assertEquals(RolDTO.class, servicio.paginado());
    }

    // DTO de un proyecto: RolDTO con un dato calculado
    static class RolPropioDTO extends RolDTO {
        private Boolean calculado;

        // Dato calculado de ejemplo
        public Boolean getCalculado() {
            return calculado;
        }
    }

    // Service de un proyecto con su propio DTO
    static class ServicioConDtoPropio extends RolBaseService<RolPropioDTO> {
        // Clase de la entidad que resolvio AbstractService
        Class<?> entidad() {
            return getClaseDeEntidad();
        }

        // Clase del DTO de obtener, guardar y borrar
        Class<?> dto() {
            return getClaseDeDto();
        }

        // Clase del DTO del paginado
        Class<?> paginado() {
            return getClaseDePaginadoDto();
        }
    }

    // Service de un proyecto que migro declarando RolDTO
    static class ServicioConRolDto extends RolBaseService<RolDTO> {
        // Clase del DTO de obtener, guardar y borrar
        Class<?> dto() {
            return getClaseDeDto();
        }

        // Clase del DTO del paginado
        Class<?> paginado() {
            return getClaseDePaginadoDto();
        }
    }

    // Service de un proyecto que todavia extiende sin parametro (tipo crudo)
    @SuppressWarnings("rawtypes")
    static class ServicioSinParametro extends RolBaseService {
        // Clase de la entidad que resolvio AbstractService
        Class<?> entidad() {
            return getClaseDeEntidad();
        }

        // Clase del DTO de obtener, guardar y borrar
        Class<?> dto() {
            return getClaseDeDto();
        }

        // Clase del DTO del paginado
        Class<?> paginado() {
            return getClaseDePaginadoDto();
        }
    }
}
