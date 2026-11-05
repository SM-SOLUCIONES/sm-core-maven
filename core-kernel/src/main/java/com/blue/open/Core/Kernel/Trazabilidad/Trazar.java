package com.blue.open.Core.Kernel.Trazabilidad;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca una operacion cuyo inicio, fin, argumentos y resultado deben quedar trazados.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trazar {

    /**
     * Permite definir un nombre estable para la operacion.
     */
    String nombre() default "";

    /**
     * Lista de campos adicionales que no deben aparecer en argumentos ni resultados.
     */
    String[] excluir() default {};

    /**
     * Indica si se registran los argumentos sanitizados de la operacion.
     */
    boolean registrarArgumentos() default true;

    /**
     * Indica si se registra el resultado sanitizado de la operacion.
     */
    boolean registrarResultado() default true;
}
