package com.blue.open.Core.Identity.Repository;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementacion de {@link UsuarioRepositorio}. Descubre en el metamodelo de JPA cual es la
 * entidad concreta que extiende UsuarioBase (la del proyecto) y arma las consultas sobre ella.
 * Es lo que permite que el Core trabaje con usuarios sin importar com.blue.open.Code.Usuario.Usuario.
 * Dependencias externas: la base de datos.
 * Recibe: los criterios de busqueda. Responde: instancias de la entidad de usuario del proyecto.
 */
@Repository
public class UsuarioRepositorioJpa implements UsuarioRepositorio {

    @PersistenceContext
    protected EntityManager entityManager;

    private Class<? extends UsuarioBase> claseDeUsuario;
    private String nombreDeEntidad;

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends UsuarioBase> getClaseDeUsuario() {
        if (claseDeUsuario == null) {
            for (EntityType<?> tipo : entityManager.getMetamodel().getEntities()) {
                if (UsuarioBase.class.isAssignableFrom(tipo.getJavaType())) {
                    claseDeUsuario = (Class<? extends UsuarioBase>) tipo.getJavaType();
                    nombreDeEntidad = tipo.getName();
                    break;
                }
            }
            if (claseDeUsuario == null) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                        "No hay ninguna @Entity que extienda UsuarioBase. core-identity necesita que el proyecto declare su entidad de usuario.");
            }
        }
        return claseDeUsuario;
    }

    private String entidad() {
        getClaseDeUsuario();
        return nombreDeEntidad;
    }

    private Optional<UsuarioBase> primero(String condicion, Object... parametros) {
        var consulta = entityManager.createQuery("SELECT u FROM " + entidad() + " u WHERE " + condicion, UsuarioBase.class);
        for (int i = 0; i < parametros.length; i++) {
            consulta.setParameter(i + 1, parametros[i]);
        }
        List<UsuarioBase> resultados = consulta.setMaxResults(1).getResultList();
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public Optional<UsuarioBase> porUuid(String uuid) {
        return primero("u.uuid = ?1", uuid);
    }

    @Override
    public Optional<UsuarioBase> porId(Long id) {
        return primero("u.id = ?1", id);
    }

    @Override
    public Optional<UsuarioBase> porNombreDeUsuarioActivo(String nombreDeUsuario) {
        return primero("u.nombreDeUsuario = ?1 AND u.status = 0", nombreDeUsuario);
    }

    @Override
    public Optional<UsuarioBase> porEmailActivo(String email) {
        return primero("u.email = ?1 AND u.status = 0", email);
    }

    @Override
    public Optional<UsuarioBase> porEmailNoBorrado(String email) {
        return primero("u.email = ?1 AND u.status <> -1", email);
    }

    @Override
    public Optional<UsuarioBase> porSsoUuidActivo(String ssoUuid) {
        return primero("u.ssoUuid = ?1 AND u.status = 0", ssoUuid);
    }

    @Override
    public Optional<UsuarioBase> porMalvinasLegajoActivo(String legajo) {
        return primero("u.malvinasLegajo = ?1 AND u.status = 0", legajo);
    }

    @Override
    @Transactional
    public UsuarioBase guardar(UsuarioBase usuario) {
        if (usuario.getId() == null) {
            entityManager.persist(usuario);
            return usuario;
        }
        return entityManager.merge(usuario);
    }

    @Override
    public UsuarioBase nuevo() {
        try {
            return getClaseDeUsuario().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "UsuarioRepositorioJpa | nuevo | " + e.getMessage());
        }
    }
}
