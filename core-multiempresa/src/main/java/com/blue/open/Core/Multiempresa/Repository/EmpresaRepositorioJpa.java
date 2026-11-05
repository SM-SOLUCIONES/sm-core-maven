package com.blue.open.Core.Multiempresa.Repository;

import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementacion de {@link EmpresaRepositorio}. Descubre en el metamodelo de JPA cual es la
 * entidad concreta que extiende EmpresaBase (la del proyecto) y arma las consultas sobre ella.
 * Dependencias externas: la base de datos.
 * Recibe: los criterios de busqueda. Responde: instancias de la entidad de empresa del proyecto.
 */
@Repository
public class EmpresaRepositorioJpa implements EmpresaRepositorio {

    @PersistenceContext
    protected EntityManager entityManager;

    private Class<? extends EmpresaBase> claseDeEmpresa;
    private String nombreDeEntidad;

    @SuppressWarnings("unchecked")
    private String entidad() {
        if (claseDeEmpresa == null) {
            for (EntityType<?> tipo : entityManager.getMetamodel().getEntities()) {
                if (EmpresaBase.class.isAssignableFrom(tipo.getJavaType())) {
                    claseDeEmpresa = (Class<? extends EmpresaBase>) tipo.getJavaType();
                    nombreDeEntidad = tipo.getName();
                    break;
                }
            }
            if (claseDeEmpresa == null) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                        "No hay ninguna @Entity que extienda EmpresaBase. core-multiempresa necesita que el proyecto declare su entidad de empresa.");
            }
        }
        return nombreDeEntidad;
    }

    private Optional<EmpresaBase> primera(String condicion, Object... parametros) {
        var consulta = entityManager.createQuery("SELECT c FROM " + entidad() + " c WHERE " + condicion, EmpresaBase.class);
        for (int i = 0; i < parametros.length; i++) {
            consulta.setParameter(i + 1, parametros[i]);
        }
        List<EmpresaBase> resultados = consulta.setMaxResults(1).getResultList();
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public Optional<EmpresaBase> porUuid(String uuid) {
        return primera("c.uuid = ?1", uuid);
    }

    @Override
    public Optional<EmpresaBase> porUuidActiva(String uuid) {
        return primera("c.uuid = ?1 AND c.status = 0", uuid);
    }

    @Override
    public Optional<EmpresaBase> porId(Long id) {
        return primera("c.id = ?1", id);
    }

    @Override
    public Optional<EmpresaBase> porNombreActiva(String nombre) {
        return primera("c.nombre = ?1 AND c.status = 0", nombre);
    }

    @Override
    @Transactional
    public EmpresaBase guardar(EmpresaBase empresa) {
        if (empresa.getId() == null) {
            entityManager.persist(empresa);
            return empresa;
        }
        return entityManager.merge(empresa);
    }

    @Override
    public EmpresaBase nueva() {
        try {
            entidad();
            return claseDeEmpresa.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "EmpresaRepositorioJpa | nueva | " + e.getMessage());
        }
    }
}
