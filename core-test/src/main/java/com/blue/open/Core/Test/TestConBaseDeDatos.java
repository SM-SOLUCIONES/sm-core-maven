package com.blue.open.Core.Test;

import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Clase base de los tests que necesitan la base de datos. Levanta una sola vez por JVM un PostgreSQL 18 real embebido
 * (proceso nativo, sin Docker) y le apunta el datasource del contexto de Spring con el profile "test". Al arrancar el
 * contexto, Liquibase corre el master y los seeds sobre esa base, asi que el schema es identico al de local.
 * Todos los tests que la extienden comparten el mismo contexto y la misma base. Cada test corre en una transaccion
 * que se revierte al terminar (@Transactional de spring-test): ningun test deja datos ni depende del orden.
 * Declara @AutoConfigureMockMvc aunque solo lo usen las pruebas de contrato: asi todas las subclases comparten un
 * unico contexto de Spring por JVM (una anotacion distinta en una subclase crearia otro contexto).
 * Vive en core-test (Core 2.5.0); hasta 2.4.3 estaba en app/src/test del proyecto.
 * Dependencias externas: ninguna (los binarios de PostgreSQL vienen como dependencia de Maven).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class TestConBaseDeDatos {

    // PostgreSQL embebido compartido por todos los tests de la JVM
    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    // Levanta el PostgreSQL embebido en UTC-3 (igual que la base real) y registra su apagado al terminar la JVM.
    // Recibe: nada. Responde: la instancia levantada. Lanza IllegalStateException si el proceso no arranca.
    private static EmbeddedPostgres iniciarPostgres() {
        FechaHoraConfig.forzarZonaHorariaPorDefecto();
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                    .setServerConfig("timezone", FechaHoraConfig.ID_DE_ZONA)
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException e) {
                    // La JVM ya esta terminando: no hay nada mas que hacer con el proceso
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo levantar el PostgreSQL embebido de los tests", e);
        }
    }

    // Apunta el datasource del contexto de Spring al PostgreSQL embebido.
    // Recibe: el registro de properties dinamicas de Spring. Responde: nada.
    @DynamicPropertySource
    static void registrarDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
}
