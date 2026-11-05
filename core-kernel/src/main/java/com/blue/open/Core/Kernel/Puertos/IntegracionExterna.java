package com.blue.open.Core.Kernel.Puertos;

/**
 * Marca un servicio que depende de un sistema externo (Crypter, SM3, Mail, Captcha, SSO...).
 * Dependencias externas: las propias de cada implementacion.
 * Recibe: nada. Responde: el nombre a mostrar y si la integracion tiene su configuracion completa.
 * El smoke test de arranque recorre todos los beans que implementan esta interfaz y loguea su estado.
 */
public interface IntegracionExterna {

    String getNombreIntegracion();

    // true si estan todas las variables de entorno necesarias para usar la integracion
    boolean estaConfigurada();
}
