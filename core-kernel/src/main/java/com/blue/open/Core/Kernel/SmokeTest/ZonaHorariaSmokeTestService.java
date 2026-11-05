package com.blue.open.Core.Kernel.SmokeTest;

import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;

import java.sql.Connection;
import java.time.OffsetDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Soporta MariaDB/MySQL y PostgreSQL.
// No corre en el profile "test": usa H2 en memoria, que no soporta UTC_TIMESTAMP()/TIMESTAMPDIFF de MySQL
@Slf4j
@Order(1)
@Profile("!test")
@Component
@RequiredArgsConstructor
public class ZonaHorariaSmokeTestService {

    private static final int DESFASE_ESPERADO_EN_SEGUNDOS = -3 * 3600;
    private static final int TOLERANCIA_EN_SEGUNDOS = 5;

    private final JdbcTemplate jdbcTemplate;

    // Al terminar el arranque, valida que el servidor y la base de datos esten en UTC-3.
    // Dependencias externas: la base de datos. Lanza IllegalStateException si alguno no lo esta.
    @EventListener(ApplicationReadyEvent.class)
    public void validarZonasHorarias() {
        validarZonaHorariaDelServidor();
        validarZonaHorariaDeLaBase();
        log.info("Smoke test UTC-3: servidor y base de datos validados correctamente");
    }

    private void validarZonaHorariaDelServidor() {
        int desfaseEnSegundos = OffsetDateTime.now(FechaHoraConfig.ZONA).getOffset().getTotalSeconds();
        if (desfaseEnSegundos != DESFASE_ESPERADO_EN_SEGUNDOS) {
            throw new IllegalStateException(
                    "El servidor no esta en UTC-3. Offset detectado en segundos: " + desfaseEnSegundos);
        }
    }

    // Devuelve el SQL que responde el offset de la sesion contra UTC, en segundos, segun el motor
    // (MariaDB/MySQL o PostgreSQL). Depende de la conexion del JdbcTemplate para saber el motor.
    private String consultaDeDesfaseEnSegundos() {
        String motor = jdbcTemplate.execute((Connection c) -> c.getMetaData().getDatabaseProductName());
        if (motor != null && motor.toLowerCase().contains("postgres")) {
            return "SELECT EXTRACT(TIMEZONE FROM NOW())::int";
        }
        return "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW())";
    }

    private void validarZonaHorariaDeLaBase() {
        Integer desfaseEnSegundos = jdbcTemplate.queryForObject(consultaDeDesfaseEnSegundos(), Integer.class);
        if (desfaseEnSegundos == null || Math.abs(desfaseEnSegundos - DESFASE_ESPERADO_EN_SEGUNDOS) > TOLERANCIA_EN_SEGUNDOS) {
            throw new IllegalStateException(
                    "La base de datos no esta en UTC-3. Offset detectado en segundos: " + desfaseEnSegundos);
        }
    }
}
