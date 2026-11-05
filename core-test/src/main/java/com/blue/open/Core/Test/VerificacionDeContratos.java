package com.blue.open.Core.Test;

import com.blue.open.Core.Test.Contrato.ConfiguracionDeContratos;
import com.blue.open.Core.Test.Contrato.RegistroDeContratos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de completitud del Contrato de endpoint: todo endpoint publicado por un controller del proyecto o del Core
 * (RequestMappingHandlerMapping, sin los de Spring: Actuator, /error) tiene que haber sido ejecutado por alguna
 * prueba de contrato en esta corrida. Un endpoint que no se escribe no esta en el mapping.
 * Solo corre en la corrida completa (sin -Dtest) y tiene que ejecutarse despues de todas las pruebas de contrato:
 * el proyecto la extiende con @Order(Integer.MAX_VALUE) y ordena las clases con
 * junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$OrderAnnotation (junit-platform.properties).
 * Mientras se migra, contratos-pendientes.txt (classpath de test, un Controller.metodo por linea, # comentario) lista
 * los endpoints que todavia no tienen prueba; un endpoint pendiente que ya tiene prueba tambien hace fallar (hay que
 * quitarlo de la lista).
 */
public abstract class VerificacionDeContratos extends TestConBaseDeDatos {

    private static final String PENDIENTES = "contratos-pendientes.txt";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Test
    void todoEndpointTieneContrato() {
        assumeTrue(ConfiguracionDeContratos.corridaCompleta(), "La completitud solo se verifica en la corrida completa");
        Set<String> registradas = RegistroDeContratos.registradas();
        Set<String> pendientes = pendientes();
        Set<String> sinContrato = new TreeSet<>();
        Set<String> pendientesConContrato = new TreeSet<>();
        for (HandlerMethod handler : mapping.getHandlerMethods().values()) {
            if (handler.getBeanType().getName().startsWith("org.springframework.")) {
                continue;
            }
            String operacion = RegistroDeContratos.idDeOperacion(handler);
            boolean registrada = registradas.contains(operacion);
            if (!registrada && !pendientes.contains(operacion)) {
                sinContrato.add(operacion);
            }
            if (registrada && pendientes.contains(operacion)) {
                pendientesConContrato.add(operacion);
            }
        }
        assertTrue(sinContrato.isEmpty(), () -> "Endpoints sin prueba de contrato (cada uno necesita @Contrato y una "
                + "prueba que lo ejecute):\n - " + String.join("\n - ", sinContrato));
        assertTrue(pendientesConContrato.isEmpty(), () -> "Endpoints que ya tienen prueba de contrato: quitarlos de "
                + PENDIENTES + ":\n - " + String.join("\n - ", pendientesConContrato));
    }

    // Endpoints pendientes de migrar (vacio si no existe el archivo)
    private static Set<String> pendientes() {
        Set<String> pendientes = new TreeSet<>();
        try (InputStream entrada = Thread.currentThread().getContextClassLoader().getResourceAsStream(PENDIENTES)) {
            if (entrada == null) {
                return pendientes;
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            lector.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(pendientes::add);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + PENDIENTES, e);
        }
        return pendientes;
    }
}
