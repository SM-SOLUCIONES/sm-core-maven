package com.blue.open.Core.Kernel.Proyeccion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excluye un campo de los caminos genericos de lectura y escritura (Proyeccion, paginado y aplicar un DTO).
 * - En un atributo de una entidad: el front no puede filtrar, ordenar ni proyectar por el, y el Core no lo
 *   copia al guardar (ej: contrasenas, codigos de verificacion, secretos).
 * - En un campo de un DTO: la Proyeccion no lo completa y al guardar no se copia, aunque exista un atributo
 *   con el mismo nombre.
 * Los atributos de la entidad marcados con @JsonIgnore (id, idUsuarioDeCreacion, etc) tampoco se proyectan ni se
 * copian, pero si se pueden usar para ordenar.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NoProyectable {
}
