package com.blue.open.Core.Kernel.SmokeTest;

import com.blue.open.Core.Kernel.Puertos.IntegracionExterna;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Smoke test de arranque de las integraciones externas. Ya no conoce ninguna integracion concreta:
 * recibe por inyeccion todos los beans que implementan {@link IntegracionExterna}, de modo que
 * agregar o quitar un modulo de integracion no requiere tocar esta clase.
 * Dependencias externas: las de cada integracion registrada.
 * Recibe: la lista de integraciones presentes en el classpath.
 * Responde: un log INFO/WARN por integracion. Nunca corta la ejecucion: las integraciones son opcionales.
 */
@Slf4j
@Order(2)
@Component
public class IntegracionesSmokeTest {

    private final List<IntegracionExterna> integraciones;

    // Recibe todas las integraciones externas presentes en el classpath
    public IntegracionesSmokeTest(List<IntegracionExterna> integraciones) {
        this.integraciones = integraciones;
    }

    // Al terminar el arranque, loguea INFO por cada integracion configurada y WARN por cada una que no lo esta
    @EventListener(ApplicationReadyEvent.class)
    public void registrarEstadoDeIntegraciones() {
        if (integraciones.isEmpty()) {
            log.info("Smoke test integraciones externas: el proyecto no declara ninguna integracion");
            return;
        }
        for (IntegracionExterna integracion : integraciones) {
            if (integracion.estaConfigurada()) {
                log.info("Smoke test integraciones externas: {} configurado correctamente",
                        integracion.getNombreIntegracion());
            } else {
                log.warn("Smoke test integraciones externas: {} no esta configurado, sus funcionalidades fallaran si se usan",
                        integracion.getNombreIntegracion());
            }
        }
    }
}
