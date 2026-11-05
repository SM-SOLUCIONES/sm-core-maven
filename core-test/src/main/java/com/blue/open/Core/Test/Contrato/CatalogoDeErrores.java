package com.blue.open.Core.Test.Contrato;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Catalogo global de codigos de error para el anexo del contrato: recorre por reflexion todos los enums que
 * implementan CodigoDeError en com.blue.open (los de cada modulo del Core y el del proyecto), igual que
 * CodigosDeErrorTest de app (ningunCodigoSeRepite, regla 12 de errores.md). "Cuando ocurre" sale de @Descripcion
 * sobre la constante, si la tiene.
 */
final class CatalogoDeErrores {

    private CatalogoDeErrores() {
    }

    // Codigos ordenados por codigo
    static List<Entrada> leer() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(CodigoDeError.class));
        List<Entrada> entradas = new ArrayList<>();
        for (BeanDefinition definicion : scanner.findCandidateComponents("com.blue.open")) {
            Class<?> clase;
            try {
                clase = Class.forName(definicion.getBeanClassName(), false,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                continue;
            }
            if (!clase.isEnum()) {
                continue;
            }
            for (Object constante : clase.getEnumConstants()) {
                CodigoDeError codigo = (CodigoDeError) constante;
                entradas.add(new Entrada(codigo.codigo(), codigo.status().value(), codigo.mensajePorDefecto(),
                        cuando(clase, ((Enum<?>) constante).name()), clase.getSimpleName()));
            }
        }
        entradas.sort(Comparator.comparing(Entrada::codigo));
        return entradas;
    }

    // "Cuando ocurre": @Descripcion de la constante del enum, o vacio si no la tiene
    private static String cuando(Class<?> clase, String constante) {
        try {
            Descripcion descripcion = clase.getField(constante).getAnnotation(Descripcion.class);
            return descripcion == null ? "" : descripcion.value();
        } catch (NoSuchFieldException e) {
            return "";
        }
    }

    // Un codigo de error: codigo estable, status HTTP, mensaje por defecto, cuando ocurre y enum que lo declara
    record Entrada(String codigo, int status, String mensaje, String cuando, String origen) {
    }
}
