package com.blue.open.Core.Kernel.Contrato;

import com.blue.open.Core.Kernel.Puertos.AutorizadorDeEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Corta el arranque si algun endpoint de com.blue.open no declara bien quien lo puede usar. Asi ningun endpoint queda
 * abierto por olvido ni protegido por un permiso mal escrito. Lista todos los errores juntos:
 * - metodo sin @Contrato;
 * - permiso vacio;
 * - PUBLICO o AUTENTICADO combinados con otros valores;
 * - rol o permiso que el AutorizadorDeEndpoint no reconoce (solo si reconoce alguno).
 * Dependencias externas: el mapping de Spring MVC y el AutorizadorDeEndpoint.
 * Recibe: el evento de contexto listo. Responde: nada; lanza IllegalStateException si hay errores.
 */
@Component
@RequiredArgsConstructor
public class VerificadorDeContratos implements ApplicationListener<ContextRefreshedEvent> {

    @Qualifier("requestMappingHandlerMapping")
    private final ObjectProvider<RequestMappingHandlerMapping> mapping;

    private final ObjectProvider<AutorizadorDeEndpoint> autorizador;

    // Verifica todos los endpoints una vez que el contexto esta listo
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        RequestMappingHandlerMapping handlerMapping = mapping.getIfAvailable();
        AutorizadorDeEndpoint autorizadorDeEndpoint = autorizador.getIfAvailable();
        if (handlerMapping == null || autorizadorDeEndpoint == null) {
            return;
        }
        List<String> errores = verificar(handlerMapping.getHandlerMethods().values(),
                autorizadorDeEndpoint.nombresValidos());
        if (!errores.isEmpty()) {
            throw new IllegalStateException("Endpoints con @Contrato invalido (ver core/docs/rules/controllers-y-endpoints.md):\n - "
                    + String.join("\n - ", errores));
        }
    }

    // Recibe los metodos publicados y los nombres de roles/permisos validos (vacio = no se validan nombres).
    // Responde un error por metodo mal declarado, ordenados por Controller.metodo
    static List<String> verificar(Iterable<HandlerMethod> metodos, Set<String> nombresValidos) {
        Map<String, String> errores = new TreeMap<>();
        for (HandlerMethod metodo : metodos) {
            if (!InterceptorDeContrato.esDelProyecto(metodo)) {
                continue;
            }
            String operacion = metodo.getBeanType().getSimpleName() + "." + metodo.getMethod().getName();
            String error = error(metodo.getMethodAnnotation(Contrato.class), nombresValidos);
            if (error != null) {
                errores.put(operacion, operacion + ": " + error);
            }
        }
        return new ArrayList<>(errores.values());
    }

    // Error de un @Contrato, o null si esta bien declarado
    private static String error(Contrato contrato, Set<String> nombresValidos) {
        if (contrato == null) {
            return "no tiene @Contrato";
        }
        List<String> permiso = Arrays.asList(contrato.permiso());
        if (permiso.isEmpty()) {
            return "permiso vacio: declarar Contrato.PUBLICO, Contrato.AUTENTICADO o los roles/permisos";
        }
        boolean especial = permiso.contains(Contrato.PUBLICO) || permiso.contains(Contrato.AUTENTICADO);
        if (especial && permiso.size() > 1) {
            return "PUBLICO y AUTENTICADO no se combinan con otros valores: " + permiso;
        }
        if (especial || nombresValidos.isEmpty()) {
            return null;
        }
        List<String> desconocidos = permiso.stream().filter(nombre -> !nombresValidos.contains(nombre)).toList();
        return desconocidos.isEmpty() ? null : "roles/permisos inexistentes: " + desconocidos;
    }
}
