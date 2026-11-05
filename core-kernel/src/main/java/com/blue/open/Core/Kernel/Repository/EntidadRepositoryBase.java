package com.blue.open.Core.Kernel.Repository;

import com.blue.open.Core.Kernel.DTO.Pagina;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Implementacion base de todos los repositorios del sistema: agrega el motor de paginado a la
 * implementacion estandar de Spring Data.
 * En Core 1.x cada entidad necesitaba su propio {Entidad}RepositoryCustom + {Entidad}RepositoryImpl
 * solo para exponer paginar(); al registrarse como repositoryBaseClass, ese boilerplate desaparece.
 * Dependencias externas: la base de datos.
 * Recibe: el PedidoDePaginado del endpoint y la clase del DTO. Responde: la pagina de DTOs.
 */
@NoRepositoryBean
public class EntidadRepositoryBase<T extends EntidadBase> extends SimpleJpaRepository<T, Long>
        implements PaginadoAutomatico<T> {

    private final MotorDeConsultas<T> motorDeConsultas;

    // Lo instancia Spring Data por cada repositorio; arma el MotorDeConsultas con su EntityManager
    public EntidadRepositoryBase(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.motorDeConsultas = new MotorDeConsultas<>(entityManager);
    }

    @Override
    public <D> Pagina<D> paginar(PedidoDePaginado pedido, Class<D> claseDto) {
        if (pedido.getClaseDeEntidad() == null) {
            pedido.setClaseDeEntidad(getDomainClass());
        }
        return motorDeConsultas.paginar(pedido, claseDto);
    }
}
