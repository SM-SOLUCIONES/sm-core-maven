package com.blue.open.Core.Test;

import java.util.List;
import java.util.Optional;

/**
 * Puerto que el proyecto implementa en app/src/test (como bean de Spring) para que las pruebas de contrato puedan
 * ejecutar un endpoint como un actor: un rol con su token Bearer. Los tokens salen de los seeds de Liquibase del
 * proyecto. core-test lo usa ademas para generar el caso automatico "sin permiso" de cada operacion con @Contrato.
 */
public interface ActoresDePrueba {

    // Token Bearer del actor que tiene el rol. Recibe el nombre del rol (ej. "SYSADMIN").
    // Lanza IllegalArgumentException si el proyecto no declara un actor para ese rol
    String token(String rol);

    // Rol de algun actor que NO tiene ninguno de los roles/permisos recibidos (los del @Contrato), para el caso
    // automatico "sin permiso". Responde vacio si todos los actores tienen alguno (el caso se omite)
    Optional<String> rolSinPermiso(List<String> permisos);
}
