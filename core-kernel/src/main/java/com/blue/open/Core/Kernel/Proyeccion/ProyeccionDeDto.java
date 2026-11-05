package com.blue.open.Core.Kernel.Proyeccion;

import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decide que campos de un DTO se traen de la base y de que atributo sale cada uno (la Proyeccion del DTO).
 * Recorre los campos del DTO (incluidos los heredados, ej: uuid/status/fechaDeCreacion de EntidadBaseDTO):
 * - Con @Columna("ruta"): se toma esa ruta, que puede navegar relaciones.
 * - Con @Coleccion("atributo"): no sale de la consulta principal; se resuelve aparte (ver colecciones()).
 * - Sin anotacion: se toma el atributo simple de la entidad con el mismo nombre, si existe.
 * - Con @NoProyectable, o sin atributo equivalente: no se proyecta y queda en su valor por defecto.
 * El resultado se cachea por par entidad/DTO: el recorrido por reflexion se hace una sola vez.
 * Dependencias externas: el metamodelo de JPA.
 * Recibe: la clase del DTO y el tipo de la entidad. Responde: los CampoProyectado y las ColeccionProyectada.
 */
public final class ProyeccionDeDto {

    private static final Map<String, List<CampoProyectado>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<ColeccionProyectada>> COLECCIONES = new ConcurrentHashMap<>();

    private ProyeccionDeDto() {
    }

    // Campos del DTO que salen de la consulta principal, con la ruta de cada uno (cacheado por entidad/DTO)
    public static List<CampoProyectado> de(Class<?> claseDto, ManagedType<?> entidad) {
        return CACHE.computeIfAbsent(entidad.getJavaType().getName() + "->" + claseDto.getName(),
                clave -> calcular(claseDto, entidad));
    }

    /*
     * Campos @Coleccion del DTO, validados contra el metamodelo junto con todos sus niveles: falla si un atributo
     * no existe, si no es una coleccion de entidades, si el campo no es List<SubDTO> o si hay un ciclo de DTOs.
     */
    public static List<ColeccionProyectada> colecciones(Class<?> claseDto, ManagedType<?> entidad) {
        String clave = entidad.getJavaType().getName() + "->" + claseDto.getName();
        List<ColeccionProyectada> colecciones = COLECCIONES.get(clave);
        if (colecciones == null) {
            colecciones = calcularColecciones(claseDto, entidad, new ArrayDeque<>());
            COLECCIONES.put(clave, colecciones);
        }
        return colecciones;
    }

    // true si el atributo de la entidad tiene la anotacion (en el campo que lo declara)
    public static boolean tieneAnotacion(Attribute<?, ?> atributo, Class<? extends Annotation> anotacion) {
        Member miembro = atributo.getJavaMember();
        return miembro instanceof Field campo && campo.isAnnotationPresent(anotacion);
    }

    private static List<CampoProyectado> calcular(Class<?> claseDto, ManagedType<?> entidad) {
        List<CampoProyectado> campos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Class<?> clase = claseDto; clase != null && clase != Object.class; clase = clase.getSuperclass()) {
            for (Field campo : clase.getDeclaredFields()) {
                int modificadores = campo.getModifiers();
                // Un campo redeclarado en una subclase oculta al de la superclase: gana el mas especifico
                if (Modifier.isStatic(modificadores) || Modifier.isTransient(modificadores) || campo.isSynthetic()
                        || !vistos.add(campo.getName()) || campo.isAnnotationPresent(NoProyectable.class)
                        || campo.isAnnotationPresent(Coleccion.class)) {
                    continue;
                }
                Columna columna = campo.getAnnotation(Columna.class);
                String ruta = columna != null ? columna.value() : atributoEquivalente(entidad, campo.getName());
                if (ruta == null) {
                    continue;
                }
                campo.setAccessible(true);
                campos.add(new CampoProyectado(campo, ruta));
            }
        }
        if (campos.isEmpty()) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO, "Error al obtener el paginado",
                    "ProyeccionDeDto | " + claseDto.getName() + " no tiene ningun campo que se pueda traer de "
                            + entidad.getJavaType().getName());
        }
        return campos;
    }

    // Recorre los campos @Coleccion del DTO y valida cada nivel. recorrido: DTOs del camino actual, para detectar ciclos
    private static List<ColeccionProyectada> calcularColecciones(Class<?> claseDto, ManagedType<?> entidad,
            Deque<Class<?>> recorrido) {
        if (recorrido.contains(claseDto)) {
            throw invalida(claseDto, entidad, "hay un ciclo de DTOs con @Coleccion: " + recorrido.reversed().stream().map(Class::getSimpleName).toList() + " -> "
                    + claseDto.getSimpleName());
        }
        recorrido.push(claseDto);
        List<ColeccionProyectada> colecciones = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Class<?> clase = claseDto; clase != null && clase != Object.class; clase = clase.getSuperclass()) {
            for (Field campo : clase.getDeclaredFields()) {
                Coleccion coleccion = campo.getAnnotation(Coleccion.class);
                if (coleccion == null || Modifier.isStatic(campo.getModifiers()) || !vistos.add(campo.getName())
                        || campo.isAnnotationPresent(NoProyectable.class)) {
                    continue;
                }
                EntityType<?> elemento = elementoDeLaColeccion(claseDto, entidad, coleccion.value());
                Class<?> subDto = subDto(claseDto, entidad, campo);
                calcularColecciones(subDto, elemento, recorrido);
                campo.setAccessible(true);
                colecciones.add(new ColeccionProyectada(campo, coleccion.value(), coleccion.orden(), subDto, elemento));
            }
        }
        recorrido.pop();
        return colecciones;
    }

    // Entidad de los elementos del atributo coleccion, o error si el atributo no existe o no es una coleccion de entidades
    private static EntityType<?> elementoDeLaColeccion(Class<?> claseDto, ManagedType<?> entidad, String nombre) {
        Attribute<?, ?> atributo;
        try {
            atributo = entidad.getAttribute(nombre);
        } catch (IllegalArgumentException e) {
            throw invalida(claseDto, entidad, "@Coleccion(\"" + nombre + "\") no es un atributo de la entidad");
        }
        if (!(atributo instanceof PluralAttribute<?, ?, ?> plural)
                || !(plural.getElementType() instanceof EntityType<?> elemento)) {
            throw invalida(claseDto, entidad, "@Coleccion(\"" + nombre + "\") no es una coleccion de entidades");
        }
        if (tieneAnotacion(atributo, NoProyectable.class) || tieneAnotacion(atributo, JsonIgnore.class)) {
            throw invalida(claseDto, entidad, "@Coleccion(\"" + nombre + "\") es @NoProyectable o @JsonIgnore");
        }
        return elemento;
    }

    // SubDTO de un campo List<SubDTO>
    private static Class<?> subDto(Class<?> claseDto, ManagedType<?> entidad, Field campo) {
        if (campo.getType().isAssignableFrom(ArrayList.class) && Collection.class.isAssignableFrom(campo.getType())
                && campo.getGenericType() instanceof ParameterizedType tipo
                && tipo.getActualTypeArguments()[0] instanceof Class<?> subDto) {
            return subDto;
        }
        throw invalida(claseDto, entidad, "el campo @Coleccion " + campo.getName() + " debe ser List<SubDTO>");
    }

    // Error de una proyeccion mal declarada (lo convierte en fallo de arranque ValidadorDeProyecciones)
    private static ExcepcionDeApi invalida(Class<?> claseDto, ManagedType<?> entidad, String detalle) {
        return new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "ProyeccionDeDto | " + claseDto.getName()
                + " sobre " + entidad.getJavaType().getName() + " | " + detalle);
    }

    // Nombre del atributo simple de la entidad que coincide con el campo del DTO, o null si no hay
    private static String atributoEquivalente(ManagedType<?> entidad, String nombre) {
        Attribute<?, ?> atributo;
        try {
            atributo = entidad.getAttribute(nombre);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (atributo.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC
                || tieneAnotacion(atributo, NoProyectable.class) || tieneAnotacion(atributo, JsonIgnore.class)) {
            return null;
        }
        return nombre;
    }
}
