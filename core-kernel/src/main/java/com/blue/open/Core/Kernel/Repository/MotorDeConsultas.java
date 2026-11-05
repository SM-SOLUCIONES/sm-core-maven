package com.blue.open.Core.Kernel.Repository;

import com.blue.open.Core.Kernel.DTO.FiltroDePaginado;
import com.blue.open.Core.Kernel.DTO.Pagina;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Proyeccion.CampoProyectado;
import com.blue.open.Core.Kernel.Proyeccion.ColeccionProyectada;
import com.blue.open.Core.Kernel.Proyeccion.ProyeccionDeDto;
import com.blue.open.Core.Kernel.Paginado.ConsultaDePaginado;
import com.blue.open.Core.Kernel.Paginado.ConversorDeValores;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.metamodel.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motor de lectura por Proyeccion: el paginado y la lectura de un registro por uuid. Arma con ConsultaDePaginado
 * las consultas JPQL, las ejecuta y llena el DTO directamente con las columnas que devuelve la base: no carga
 * entidades. Despues completa las @Coleccion del DTO con una consulta por anotacion para todos los registros
 * (IN sobre los ids del nivel padre, partido en lotes), nivel por nivel. Al ser JPQL funciona igual en MariaDB y en
 * PostgreSQL.
 * Dependencias externas: la base de datos.
 * Recibe: el PedidoDePaginado (con claseDeEntidad) o el uuid, y la clase del DTO. Responde: la pagina de DTOs o el DTO.
 */
public class MotorDeConsultas<T extends EntidadBase> {
    private static final Logger logger = LoggerFactory.getLogger(MotorDeConsultas.class);

    // Ids por consulta de una @Coleccion: muy por debajo del limite de parametros de PostgreSQL (32767)
    public static final int TAMANO_DE_LOTE = 500;

    @PersistenceContext
    protected EntityManager entityManager;

    private int tamanoDeLote = TAMANO_DE_LOTE;

    // Constructor vacio: el EntityManager se inyecta por @PersistenceContext
    public MotorDeConsultas() {
    }

    // Usado por EntidadRepositoryBase, que recibe el EntityManager de Spring Data y no por inyeccion
    public MotorDeConsultas(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Igual que el anterior, con otro tamano de lote para el IN de las colecciones (lo usan los test)
    public MotorDeConsultas(EntityManager entityManager, int tamanoDeLote) {
        this.entityManager = entityManager;
        this.tamanoDeLote = tamanoDeLote;
    }

    // Pagina la entidad del pedido proyectando cada fila en el DTO (conteo + consulta de la pagina pedida).
    // Dependencias externas: la base de datos.
    // Recibe: el PedidoDePaginado (pagina desde 1, con claseDeEntidad) y la clase del DTO.
    // Responde: la Pagina de DTOs (numerada desde 0); lanza PAGINADO_INVALIDO si el pedido no es valido.
    public <D> Pagina<D> paginar(PedidoDePaginado pedido, Class<D> claseDto) {
        if (pedido.getClaseDeEntidad() == null || claseDto == null) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO, "Error al obtener el paginado",
                    "MotorDeConsultas | paginar | falta la entidad o el DTO de salida");
        }
        if (pedido.getPagina() < 1 || pedido.getTamano() < 1) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                    "La pagina y el tamano del paginado deben ser mayores a 0",
                    "MotorDeConsultas | paginar | pagina=" + pedido.getPagina() + " tamano=" + pedido.getTamano());
        }

        ConsultaDePaginado consulta = new ConsultaDePaginado(entityManager.getMetamodel(),
                pedido.getClaseDeEntidad(), claseDto, pedido);
        logger.info("JPQL paginado: {} | parametros: {}", consulta.getConsulta(), consulta.getParametros());

        // El pedido numera las paginas desde 1 y la respuesta desde 0, como hasta ahora el Page de Spring Data
        int numero = pedido.getPagina() - 1;

        TypedQuery<Long> consultaDeConteo = entityManager.createQuery(consulta.getConsultaDeConteo(), Long.class);
        setParametros(consultaDeConteo, consulta.getParametros());
        long total = consultaDeConteo.getSingleResult();
        if (total == 0) {
            return new Pagina<>(new ArrayList<>(), numero, pedido.getTamano(), 0);
        }

        TypedQuery<Tuple> consultaDeFilas = entityManager.createQuery(consulta.getConsulta(), Tuple.class);
        setParametros(consultaDeFilas, consulta.getParametros());
        consultaDeFilas.setFirstResult(numero * pedido.getTamano());
        consultaDeFilas.setMaxResults(pedido.getTamano());

        List<D> dtos = leer(consultaDeFilas, claseDto, consulta, pedido.getClaseDeEntidad());
        return new Pagina<>(dtos, numero, pedido.getTamano(), total);
    }

    /*
     * Proyeccion de un registro por uuid, sin filtrar por status, con sus @Coleccion completas.
     * Responde vacio si no existe.
     */
    public <D> Optional<D> porUuid(Class<?> claseDeEntidad, String uuid, Class<D> claseDto) {
        PedidoDePaginado pedido = new PedidoDePaginado();
        pedido.agregarFiltro(List.of(new FiltroDePaginado("uuid", uuid)));
        ConsultaDePaginado consulta = new ConsultaDePaginado(entityManager.getMetamodel(), claseDeEntidad, claseDto, pedido);
        TypedQuery<Tuple> consultaJpa = entityManager.createQuery(consulta.getConsulta(), Tuple.class);
        setParametros(consultaJpa, consulta.getParametros());
        consultaJpa.setMaxResults(1);
        return leer(consultaJpa, claseDto, consulta, claseDeEntidad).stream().findFirst();
    }

    /*
     * Completa las @Coleccion de DTOs ya proyectados de la entidad. Busca los ids por el uuid de cada DTO
     * (una consulta por lote) y despues resuelve las colecciones igual que el paginado.
     */
    public <D> List<D> completarColecciones(Class<?> claseDeEntidad, List<D> dtos) {
        EntityType<?> entidad = entityManager.getMetamodel().entity(claseDeEntidad);
        if (dtos.isEmpty() || ProyeccionDeDto.colecciones(dtos.get(0).getClass(), entidad).isEmpty()) {
            return dtos;
        }
        Map<Object, List<Object>> porUuid = new LinkedHashMap<>();
        for (D dto : dtos) {
            porUuid.computeIfAbsent(uuidDe(dto), uuid -> new ArrayList<>()).add(dto);
        }
        Map<Object, List<Object>> porId = new LinkedHashMap<>();
        for (List<Object> lote : lotes(porUuid.keySet())) {
            List<Tuple> filas = entityManager.createQuery("SELECT t.uuid, t.id FROM " + entidad.getName()
                    + " t WHERE t.uuid IN (:uuids)", Tuple.class).setParameter("uuids", lote).getResultList();
            for (Tuple fila : filas) {
                porId.put(fila.get(1), porUuid.get(fila.get(0)));
            }
        }
        completarColecciones(entidad, dtos.get(0).getClass(), porId);
        return dtos;
    }

    private void setParametros(Query consultaJpa, Map<String, Object> parametros) {
        for (Map.Entry<String, Object> parametro : parametros.entrySet()) {
            consultaJpa.setParameter(parametro.getKey(), parametro.getValue());
        }
    }

    // Llena los DTOs de la consulta y, si el DTO tiene @Coleccion, las completa con el id que trae cada fila
    private <D> List<D> leer(TypedQuery<Tuple> consultaJpa, Class<D> claseDto, ConsultaDePaginado consulta,
            Class<?> claseDeEntidad) {
        List<D> dtos = new ArrayList<>();
        Map<Object, List<Object>> porId = new LinkedHashMap<>();
        for (Tuple fila : consultaJpa.getResultList()) {
            D dto = crearDto(claseDto, consulta.getCampos(), fila);
            dtos.add(dto);
            if (consulta.isConId()) {
                porId.computeIfAbsent(fila.get(consulta.getCampos().size()), id -> new ArrayList<>()).add(dto);
            }
        }
        if (consulta.isConId()) {
            completarColecciones(entityManager.getMetamodel().entity(claseDeEntidad), claseDto, porId);
        }
        return dtos;
    }

    /*
     * Resuelve las @Coleccion de un nivel: por cada anotacion, una consulta por lote de ids padre. Cada fila es un
     * elemento de un padre: se agrega a la lista de todos los DTOs de ese padre. Despues baja al nivel siguiente con
     * los ids de los elementos. Un padre sin elementos queda con la lista vacia, nunca null.
     */
    @SuppressWarnings("unchecked")
    private void completarColecciones(EntityType<?> padre, Class<?> claseDto, Map<Object, List<Object>> porId) {
        for (ColeccionProyectada coleccion : ProyeccionDeDto.colecciones(claseDto, padre)) {
            try {
                for (List<Object> dtos : porId.values()) {
                    for (Object dto : dtos) {
                        coleccion.campo().set(dto, new ArrayList<>());
                    }
                }
                if (porId.isEmpty()) {
                    continue;
                }
                ConsultaDePaginado consulta = ConsultaDePaginado.deColeccion(entityManager.getMetamodel(), padre,
                        coleccion);
                int columnas = consulta.getCampos().size();
                Map<Object, List<Object>> elementos = new LinkedHashMap<>();
                for (List<Object> lote : lotes(porId.keySet())) {
                    List<Tuple> filas = entityManager.createQuery(consulta.getConsulta(), Tuple.class)
                            .setParameter("ids", lote).getResultList();
                    for (Tuple fila : filas) {
                        Object elemento = crearDto(coleccion.subDto(), consulta.getCampos(), fila);
                        for (Object dto : porId.get(fila.get(columnas + 1))) {
                            ((List<Object>) coleccion.campo().get(dto)).add(elemento);
                        }
                        elementos.computeIfAbsent(fila.get(columnas), id -> new ArrayList<>()).add(elemento);
                    }
                }
                completarColecciones(coleccion.elemento(), coleccion.subDto(), elementos);
            } catch (IllegalAccessException e) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "MotorDeConsultas | completarColecciones | "
                        + claseDto.getSimpleName() + "." + coleccion.campo().getName() + " | " + e.getMessage());
            }
        }
    }

    // Parte los ids en lotes de tamanoDeLote para el IN de cada consulta
    private List<List<Object>> lotes(Collection<Object> ids) {
        List<Object> todos = new ArrayList<>(ids);
        List<List<Object>> lotes = new ArrayList<>();
        for (int i = 0; i < todos.size(); i += tamanoDeLote) {
            lotes.add(todos.subList(i, Math.min(i + tamanoDeLote, todos.size())));
        }
        return lotes;
    }

    // Uuid de un DTO (campo uuid, propio o heredado)
    private static Object uuidDe(Object dto) {
        for (Class<?> clase = dto.getClass(); clase != null && clase != Object.class; clase = clase.getSuperclass()) {
            try {
                Field campo = clase.getDeclaredField("uuid");
                campo.setAccessible(true);
                return campo.get(dto);
            } catch (NoSuchFieldException e) {
                // sigue con la superclase
            } catch (IllegalAccessException e) {
                break;
            }
        }
        throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                "MotorDeConsultas | uuidDe | " + dto.getClass().getSimpleName() + " no tiene uuid");
    }

    // Instancia el DTO y copia cada columna en su campo, en el mismo orden del SELECT
    private <D> D crearDto(Class<D> claseDto, List<CampoProyectado> campos, Tuple fila) {
        CampoProyectado actual = null;
        try {
            D dto = claseDto.getDeclaredConstructor().newInstance();
            for (int i = 0; i < campos.size(); i++) {
                actual = campos.get(i);
                Object valor = ConversorDeValores.alTipoDelCampo(fila.get(i), actual.campo().getType());
                if (valor != null || !actual.campo().getType().isPrimitive()) {
                    actual.campo().set(dto, valor);
                }
            }
            return dto;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO, "Error al convertir los datos del paginado",
                    "MotorDeConsultas | crearDto | " + claseDto.getSimpleName()
                            + (actual != null ? "." + actual.campo().getName() : "") + " | " + e.getMessage());
        }
    }
}
