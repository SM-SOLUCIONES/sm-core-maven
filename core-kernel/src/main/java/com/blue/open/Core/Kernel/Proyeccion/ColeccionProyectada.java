package com.blue.open.Core.Kernel.Proyeccion;

import jakarta.persistence.metamodel.EntityType;

import java.lang.reflect.Field;

/**
 * Campo @Coleccion de un DTO ya validado contra el metamodelo: el campo List del DTO, el atributo coleccion de
 * la entidad, el orden de los elementos, el SubDTO y la entidad de los elementos.
 * Lo arma ProyeccionDeDto y lo consumen ConsultaDePaginado (para la consulta) y MotorDeConsultas (para llenar la lista).
 */
public record ColeccionProyectada(Field campo, String atributo, String orden, Class<?> subDto, EntityType<?> elemento) {
}
