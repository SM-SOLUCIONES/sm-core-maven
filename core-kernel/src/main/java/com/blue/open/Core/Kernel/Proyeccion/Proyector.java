package com.blue.open.Core.Kernel.Proyeccion;

import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import com.blue.open.Core.Kernel.DTO.FiltroDePaginado;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Paginado.ConsultaDePaginado;
import com.blue.open.Core.Kernel.Paginado.ConversorDeValores;
import com.blue.open.Core.Kernel.Repository.MotorDeConsultas;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Motor generico de Proyeccion y de aplicacion de DTOs. Es el camino por defecto del ABM (AbstractService) y lo puede
 * inyectar cualquier caso de uso para no reescribir lo que el ABM ya resuelve.
 * - Lectura: arma el DTO directamente desde la consulta, con solo las columnas de sus campos (por nombre o
 *   &#64;Columna, con LEFT JOIN por relacion) y completa sus &#64;Coleccion con una consulta por anotacion.
 * - Escritura (aplicar un DTO): copia sobre la entidad cargada los campos simples del DTO con el mismo nombre que un
 *   atributo basico, asigna las relaciones declaradas con &#64;Columna("relacion.uuid") buscando la entidad vigente,
 *   llama a aplicarCamposBase(dto) y despues a AplicaDto.aplicar(dto) si la entidad lo implementa.
 * Lo que no se lee ni se copia: &#64;NoProyectable y &#64;JsonIgnore (en la entidad o en el DTO), atributos sin setter
 * publico, rutas mas largas que relacion.uuid y &#64;Coleccion (solo lectura).
 * Dependencias externas: la base de datos.
 * Recibe: la clase de la entidad, el DTO y el uuid o la entidad cargada. Responde: el DTO o la entidad modificada.
 */
@Component
@RequiredArgsConstructor
public class Proyector {

    private final EntityManager entityManager;

    // Proyeccion de un registro por uuid (sin filtrar por status), con sus @Coleccion. Vacio si no existe.
    public <D> Optional<D> porUuid(Class<?> claseDeEntidad, String uuid, Class<D> claseDto) {
        return new MotorDeConsultas<>(entityManager).porUuid(claseDeEntidad, uuid, claseDto);
    }

    // Completa las @Coleccion de DTOs ya proyectados (por ejemplo, por un SELECT new propio). Responde la misma lista.
    public <D> List<D> completarColecciones(Class<?> claseDeEntidad, List<D> dtos) {
        return new MotorDeConsultas<>(entityManager).completarColecciones(claseDeEntidad, dtos);
    }

    /*
     * Aplica el DTO sobre la entidad cargada (o recien creada) y la devuelve. Primero resuelve todas las relaciones
     * (una consulta por relacion) y recien despues modifica la entidad: si una relacion no existe, la entidad queda
     * intacta. relacionNoEncontrada: codigo que se lanza, con el nombre del campo en datos.campo.
     */
    public <T> T aplicar(Object dto, T entidad, CodigoDeError relacionNoEncontrada) {
        Class<?> clase = Hibernate.getClass(entidad);
        ManagedType<?> tipo = entityManager.getMetamodel().managedType(clase);
        Map<Method, Object> asignaciones = new LinkedHashMap<>();
        Set<String> vistos = new HashSet<>();
        // Los campos de control de EntidadBaseDTO (uuid, status, fechaDeCreacion) los aplica aplicarCamposBase
        for (Class<?> c = dto.getClass(); c != null && c != Object.class && c != EntidadBaseDTO.class;
                c = c.getSuperclass()) {
            for (Field campo : c.getDeclaredFields()) {
                int modificadores = campo.getModifiers();
                if (Modifier.isStatic(modificadores) || Modifier.isTransient(modificadores) || campo.isSynthetic()
                        || !vistos.add(campo.getName()) || campo.isAnnotationPresent(NoProyectable.class)
                        || campo.isAnnotationPresent(Coleccion.class)) {
                    continue;
                }
                Columna columna = campo.getAnnotation(Columna.class);
                String[] ruta = (columna != null ? columna.value() : campo.getName()).split("\\.");
                if (ruta.length == 1) {
                    asignarSimple(dto, campo, tipo, clase, ruta[0], asignaciones);
                } else if (ruta.length == 2 && "uuid".equals(ruta[1])) {
                    asignarRelacion(dto, campo, tipo, clase, ruta[0], relacionNoEncontrada, asignaciones);
                }
            }
        }
        for (Map.Entry<Method, Object> asignacion : asignaciones.entrySet()) {
            invocar(asignacion.getKey(), entidad, asignacion.getValue());
        }
        if (entidad instanceof EntidadBase conControl && dto instanceof EntidadBaseDTO control) {
            conControl.aplicarCamposBase(control);
        }
        aplicarPropio(dto, entidad, clase);
        return entidad;
    }

    /*
     * Valida la proyeccion de un DTO sobre una entidad sin ejecutarla: rutas de @Columna, @Coleccion y su orden, en
     * todos los niveles, y ciclos de DTOs. Lanza IllegalStateException con el detalle si algo no es valido.
     */
    public void validar(Class<?> claseDeEntidad, Class<?> claseDto) {
        try {
            EntityType<?> entidad = entityManager.getMetamodel().entity(claseDeEntidad);
            PedidoDePaginado pedido = new PedidoDePaginado();
            pedido.agregarFiltro(List.of(new FiltroDePaginado("uuid", "validacion")));
            new ConsultaDePaginado(entityManager.getMetamodel(), claseDeEntidad, claseDto, pedido);
            validarColecciones(entidad, claseDto);
        } catch (ExcepcionDeApi e) {
            throw new IllegalStateException("Proyeccion invalida de " + claseDto.getName() + " sobre "
                    + claseDeEntidad.getName() + ": " + e.getMensajeParaUsuario() + " | " + e.getMensajeParaDesarrollador(), e);
        }
    }

    // Arma (sin ejecutar) la consulta de cada @Coleccion del DTO y baja a sus SubDTOs. Lanza ExcepcionDeApi si no es valida
    private void validarColecciones(EntityType<?> padre, Class<?> claseDto) {
        for (ColeccionProyectada coleccion : ProyeccionDeDto.colecciones(claseDto, padre)) {
            ConsultaDePaginado.deColeccion(entityManager.getMetamodel(), padre, coleccion);
            validarColecciones(coleccion.elemento(), coleccion.subDto());
        }
    }

    // Campo simple: atributo basico de la entidad con setter publico, sin @NoProyectable ni @JsonIgnore
    private void asignarSimple(Object dto, Field campo, ManagedType<?> tipo, Class<?> clase, String nombre,
            Map<Method, Object> asignaciones) {
        Attribute<?, ?> atributo = atributo(tipo, nombre);
        Method setter = setter(clase, nombre);
        if (atributo == null || atributo.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC
                || excluido(atributo) || setter == null) {
            return;
        }
        Class<?> destino = setter.getParameterTypes()[0];
        Object valor = ConversorDeValores.alTipoDelCampo(leer(campo, dto), destino);
        if (valor == null && destino.isPrimitive()) {
            return;
        }
        asignaciones.put(setter, valor);
    }

    // Relacion @ManyToOne / @OneToOne por uuid: null desasocia, un uuid sin registro vigente es un error
    private void asignarRelacion(Object dto, Field campo, ManagedType<?> tipo, Class<?> clase, String nombre,
            CodigoDeError relacionNoEncontrada, Map<Method, Object> asignaciones) {
        Attribute<?, ?> atributo = atributo(tipo, nombre);
        Method setter = setter(clase, nombre);
        if (atributo == null || excluido(atributo) || setter == null
                || (atributo.getPersistentAttributeType() != Attribute.PersistentAttributeType.MANY_TO_ONE
                        && atributo.getPersistentAttributeType() != Attribute.PersistentAttributeType.ONE_TO_ONE)) {
            return;
        }
        Object uuid = leer(campo, dto);
        if (uuid == null) {
            asignaciones.put(setter, null);
            return;
        }
        EntityType<?> relacionada = entityManager.getMetamodel().entity(atributo.getJavaType());
        String vigente = EntidadConBorradoLogico.class.isAssignableFrom(relacionada.getJavaType())
                ? " AND r.status = 0" : "";
        List<?> encontradas = entityManager.createQuery("SELECT r FROM " + relacionada.getName()
                + " r WHERE r.uuid = :uuid" + vigente).setParameter("uuid", uuid.toString()).setMaxResults(1)
                .getResultList();
        if (encontradas.isEmpty()) {
            throw new ExcepcionDeApi(relacionNoEncontrada, "Proyector | aplicar | no existe " + relacionada.getName()
                    + " vigente con uuid " + uuid + " (campo " + campo.getName() + ")")
                    .conDato("campo", campo.getName());
        }
        asignaciones.put(setter, encontradas.get(0));
    }

    // Llama a AplicaDto.aplicar(dto) si la entidad lo implementa para el tipo de este DTO
    @SuppressWarnings("unchecked")
    private void aplicarPropio(Object dto, Object entidad, Class<?> clase) {
        if (!(entidad instanceof AplicaDto<?> aplicaDto)) {
            return;
        }
        Class<?> tipoDeDto = ResolvableType.forClass(clase).as(AplicaDto.class).getGeneric(0).resolve();
        if (tipoDeDto == null || tipoDeDto.isInstance(dto)) {
            ((AplicaDto<Object>) aplicaDto).aplicar(dto);
        }
    }

    // Atributo de la entidad con ese nombre, o null si no existe
    private static Attribute<?, ?> atributo(ManagedType<?> tipo, String nombre) {
        try {
            return tipo.getAttribute(nombre);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // true si el atributo es @NoProyectable o @JsonIgnore: nunca se copia
    private static boolean excluido(Attribute<?, ?> atributo) {
        return ProyeccionDeDto.tieneAnotacion(atributo, NoProyectable.class)
                || ProyeccionDeDto.tieneAnotacion(atributo, JsonIgnore.class);
    }

    // Setter publico del atributo, o null si no tiene (ej: @Setter(AccessLevel.NONE))
    private static Method setter(Class<?> clase, String nombre) {
        PropertyDescriptor propiedad = BeanUtils.getPropertyDescriptor(clase, nombre);
        return propiedad == null ? null : propiedad.getWriteMethod();
    }

    // Valor del campo del DTO
    private static Object leer(Field campo, Object dto) {
        try {
            campo.setAccessible(true);
            return campo.get(dto);
        } catch (IllegalAccessException e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "Proyector | leer | " + campo.getName() + " | " + e.getMessage());
        }
    }

    // Llama al setter de la entidad. Una ExcepcionDeApi del setter (regla de negocio) se propaga tal cual
    private static void invocar(Method setter, Object entidad, Object valor) {
        try {
            setter.invoke(entidad, valor);
        } catch (ReflectiveOperationException e) {
            Throwable causa = e.getCause();
            if (causa instanceof ExcepcionDeApi excepcionDeApi) {
                throw excepcionDeApi;
            }
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "Proyector | aplicar | " + setter.getName() + " | " + e.getMessage());
        }
    }
}
