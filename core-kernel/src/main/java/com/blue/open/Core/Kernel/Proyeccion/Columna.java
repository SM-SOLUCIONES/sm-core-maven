package com.blue.open.Core.Kernel.Proyeccion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indica de que atributo de la entidad sale un campo del DTO de paginado. Solo hace falta cuando
 * el nombre del campo no coincide con un atributo de la entidad, o cuando el dato viene de una
 * entidad relacionada. La ruta usa nombres JPA (atributos, no columnas) separados por punto,
 * navegando asociaciones @ManyToOne / @OneToOne. Ejemplo:
 * <pre>
 * &#64;Columna("tipoVehiculo.uuid")
 * private String tipoVehiculoUuid;
 * </pre>
 * Las colecciones (@OneToMany, @ManyToMany) no se pueden proyectar: esos campos se completan en
 * AbstractService.hookPaginarCompletarResultados.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Columna {

    // Ruta JPA del atributo, relativa a la entidad paginada (ej: "nombre", "tipoVehiculo.pais.nombre")
    String value();
}
