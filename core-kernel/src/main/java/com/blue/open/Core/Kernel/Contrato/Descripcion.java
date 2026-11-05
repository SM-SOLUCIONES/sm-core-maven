package com.blue.open.Core.Kernel.Contrato;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Descripcion de un dato para el Contrato de endpoint: va sobre cada campo de un DTO, sobre los parametros
 * @PathVariable, @RequestParam y @RequestHeader de un controller, sobre cada constante de un enum CodigoDeError
 * (cuando ocurre el error) y, opcionalmente, sobre la clase del DTO. core-test la publica como "description" del
 * schema y falla si un campo de un DTO que aparece en el contrato no la tiene.
 * Sobre el getter de un campo (desde Core 3.5.0) gana sobre la del campo: un DTO hijo redefine la descripcion de un
 * campo heredado (ej. status de EntidadBaseDTO) sobreescribiendo su getter con una @Descripcion propia. Obligatorio/opcional, tipo y limites
 * no se escriben aca: salen de Bean Validation y del tipo Java. Sin logica.
 * Ver core/docs/arquitectura/testing/README.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
public @interface Descripcion {

    // Texto para el programador front: que significa el dato y, si aplica, sus valores especiales
    String value();
}
