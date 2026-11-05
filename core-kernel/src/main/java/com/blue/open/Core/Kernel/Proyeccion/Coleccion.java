package com.blue.open.Core.Kernel.Proyeccion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indica que un campo List&lt;SubDTO&gt; del DTO se completa con los elementos de una coleccion de la entidad
 * (@OneToMany / @ManyToMany). El SubDTO sigue las mismas reglas que un DTO raiz: campos por nombre,
 * &#64;Columna, exclusiones y, a su vez, otras &#64;Coleccion (sin limite de niveles). Ejemplo:
 * <pre>
 * &#64;Coleccion(value = "permisos", orden = "nombre")
 * private List&lt;PermisoDTO&gt; permisos;
 * </pre>
 * Se resuelve con una consulta por anotacion para todos los registros (IN sobre los ids del nivel padre,
 * partido en lotes), nunca una por registro. Si los elementos tienen borrado logico, solo trae los vigentes
 * (status = 0). Es de solo lectura: al guardar, el Core la ignora.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Coleccion {

    // Nombre JPA del atributo coleccion de la entidad (ej: "permisos")
    String value();

    // Atributo del elemento por el que se ordena la lista, ascendente. Vacio: por id ascendente
    String orden() default "";
}
