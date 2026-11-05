package com.blue.open.Core.Test.Contrato;

import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Convierte el tipo de un parametro o del retorno de un metodo de controller, con sus genericos resueltos contra la
 * clase del controller (ej. el DTO generico de un controller -> DependenciaDTO), en un java.lang.reflect.Type que entiende
 * el generador de schemas.
 */
final class Tipos {

    private Tipos() {
    }

    // Tipo con los genericos resueltos. Un generico que no se puede resolver queda como Object
    static Type aTipo(ResolvableType tipo) {
        Class<?> crudo = tipo.resolve(Object.class);
        if (!tipo.hasGenerics() || crudo.isArray()) {
            return crudo;
        }
        Type[] argumentos = Arrays.stream(tipo.getGenerics()).map(Tipos::aTipo).toArray(Type[]::new);
        return new Parametrizado(crudo, argumentos);
    }

    // Tipo del body de la respuesta: desenvuelve ResponseEntity/HttpEntity. Null si no hay body (void, Void)
    static ResolvableType cuerpoDeRespuesta(ResolvableType retorno) {
        ResolvableType cuerpo = HttpEntity.class.isAssignableFrom(retorno.resolve(Object.class))
                ? retorno.as(HttpEntity.class).getGeneric(0)
                : retorno;
        Class<?> clase = cuerpo.resolve();
        if (clase == null || clase == void.class || clase == Void.class) {
            return null;
        }
        return cuerpo;
    }

    // ParameterizedType minimo (crudo + argumentos), suficiente para el generador de schemas
    private record Parametrizado(Class<?> crudo, Type[] argumentos) implements ParameterizedType {

        @Override
        public Type[] getActualTypeArguments() {
            return argumentos.clone();
        }

        @Override
        public Type getRawType() {
            return crudo;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}
