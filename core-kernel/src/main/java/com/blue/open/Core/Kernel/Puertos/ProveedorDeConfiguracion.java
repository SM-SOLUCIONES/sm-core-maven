package com.blue.open.Core.Kernel.Puertos;

/**
 * Puerto de configuracion dinamica. Lo usa la trazabilidad para saber si el modo DEBUG esta activo.
 * Dependencias externas: ninguna. Recibe: la clave y un valor por defecto.
 * Responde: el valor configurado, o el valor por defecto si la clave no existe.
 * Por defecto lee del Environment de Spring; un proyecto puede respaldarlo con una tabla propia.
 */
public interface ProveedorDeConfiguracion {
    String getValor(String clave, String porDefecto);
}
