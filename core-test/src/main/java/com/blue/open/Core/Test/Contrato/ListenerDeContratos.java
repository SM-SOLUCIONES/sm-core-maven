package com.blue.open.Core.Test.Contrato;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Listener de JUnit (registrado por ServiceLoader en core-test) que publica el Contrato de endpoint al terminar la
 * sesion de tests:
 * - Al abrir una corrida completa (sin -Dtest), borra los contratos parciales de corridas anteriores.
 * - Registra si algun test fallo.
 * - Al cerrar, ensambla openapi.yaml e index.html en target/contratos (siempre, tambien en corridas parciales). En
 *   una corrida completa y en verde los copia a la copia commiteada (contratos.destino, ej. docs/api); con
 *   -Dcontratos.verificar, en vez de copiar, falla si la copia commiteada difiere.
 * JUnit crea una instancia por interfaz registrada: el estado es estatico.
 * Dependencias externas: el filesystem.
 */
public class ListenerDeContratos implements LauncherSessionListener, TestExecutionListener {

    private static volatile boolean hayFallos;

    @Override
    // Al abrir una corrida completa borra los contratos parciales de corridas anteriores
    public void launcherSessionOpened(LauncherSession session) {
        hayFallos = false;
        if (ConfiguracionDeContratos.corridaCompleta()) {
            try {
                FileSystemUtils.deleteRecursively(ConfiguracionDeContratos.directorioDeOperaciones());
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudieron borrar los contratos parciales anteriores", e);
            }
        }
    }

    @Override
    // Registra si algun test fallo (un contrato con fallas no se publica)
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
            hayFallos = true;
        }
    }

    @Override
    // Ensambla el contrato y, en la corrida completa en verde, lo copia o lo verifica contra docs/api
    public void launcherSessionClosed(LauncherSession session) {
        if (!EnsambladorDeContrato.ensamblar()) {
            return;
        }
        Path destino = ConfiguracionDeContratos.destino();
        if (destino == null || !ConfiguracionDeContratos.corridaCompleta() || hayFallos) {
            return;
        }
        try {
            if (ConfiguracionDeContratos.verificar()) {
                verificar(destino);
            } else {
                copiar(destino);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo publicar el contrato en " + destino, e);
        }
    }

    // Copia openapi.yaml e index.html a la copia commiteada
    private static void copiar(Path destino) throws IOException {
        Files.createDirectories(destino);
        for (String archivo : Arrays.asList(EnsambladorDeContrato.OPENAPI, EnsambladorDeContrato.HTML)) {
            Files.copy(ConfiguracionDeContratos.directorio().resolve(archivo), destino.resolve(archivo),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Contenido del archivo con los fines de linea unificados (git en Windows hace checkout con CRLF)
    private static String sinFinesDeLinea(Path archivo) throws IOException {
        return Files.readString(archivo).replace("\r\n", "\n");
    }

    // Lanza IllegalStateException si el openapi.yaml commiteado no es el generado
    private static void verificar(Path destino) throws IOException {
        Path commiteado = destino.resolve(EnsambladorDeContrato.OPENAPI);
        Path generado = ConfiguracionDeContratos.directorio().resolve(EnsambladorDeContrato.OPENAPI);
        if (!Files.exists(commiteado) || !sinFinesDeLinea(commiteado).equals(sinFinesDeLinea(generado))) {
            throw new IllegalStateException("El contrato commiteado (" + commiteado + ") no coincide con el generado ("
                    + generado + "). Correr mvn -pl app test sin -Dcontratos.verificar y commitear docs/api");
        }
    }
}
