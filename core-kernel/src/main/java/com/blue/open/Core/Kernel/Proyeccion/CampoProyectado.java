package com.blue.open.Core.Kernel.Proyeccion;

import java.lang.reflect.Field;

/**
 * Campo del DTO de paginado que se completa desde la consulta, junto con la ruta JPA de la que sale.
 * Lo arma ProyeccionDeDto y lo consumen ConsultaDePaginado (para el SELECT) y MotorDeConsultas (para llenar el DTO).
 */
public record CampoProyectado(Field campo, String ruta) {
}
