package com.blue.open.Core.Kernel.Contrato;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Unica declaracion de un endpoint: autoriza el request y alimenta el Contrato de endpoint (docs/api/openapi.yaml).
 * Va sobre todo metodo de controller de com.blue.open: la aplicacion no arranca si falta (VerificadorDeContratos).
 * casoDeUso: identificador del caso de uso que resuelve (ej. "CU-DEP-03"); agrupa la operacion en el contrato.
 * resumen: una linea, en lenguaje del usuario final, de lo que hace la operacion.
 * permiso: quien puede ejecutarla. PUBLICO, AUTENTICADO, o nombres de roles/permisos (alcanza con tener uno), tal
 * como se llama la constante del enum (ej. "SYSADMIN", "USUARIO_VER"). Lo hace cumplir InterceptorDeContrato antes de
 * ejecutar el metodo, a traves del puerto AutorizadorDeEndpoint. No tiene default: siempre se declara.
 * Ver core/docs/arquitectura/testing/README.md y core/docs/rules/controllers-y-endpoints.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Contrato {

    // Endpoint publico: no exige sesion
    String PUBLICO = "#PUBLICO";

    // Exige sesion, pero ningun rol ni permiso puntual
    String AUTENTICADO = "#AUTENTICADO";

    // Identificador del caso de uso (ej. "CU-DEP-03")
    String casoDeUso();

    // Que hace la operacion, en una linea
    String resumen();

    // PUBLICO, AUTENTICADO, o roles/permisos que la habilitan (alcanza con uno)
    String[] permiso();
}
