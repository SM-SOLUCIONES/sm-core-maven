package com.blue.open.Core.Test.Contrato;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Configuracion del emisor de contratos. Sale de dos lugares:
 * - Properties de sistema que el proyecto pasa en la configuracion de surefire (app/pom.xml):
 *   contratos.directorio (salida, default target/contratos), contratos.destino (copia commiteada, ej. docs/api; sin
 *   ella no se copia), contratos.version (info.version del OpenAPI) y, por linea de comandos, -Dtest (corrida
 *   parcial) y -Dcontratos.verificar (falla si la copia commiteada difiere, para CI).
 * - contratos.properties en el classpath de test del proyecto: contratos.titulo, contratos.descripcion,
 *   contratos.servidor (url base, ej. /api) y un contratos.encabezado.{Nombre}={descripcion} por cada header global.
 */
public final class ConfiguracionDeContratos {

    private static final String ARCHIVO = "contratos.properties";

    private static final String PREFIJO_ENCABEZADO = "contratos.encabezado.";

    private ConfiguracionDeContratos() {
    }

    // Directorio de salida del contrato ensamblado (openapi.yaml, index.html)
    public static Path directorio() {
        return Path.of(System.getProperty("contratos.directorio", "target/contratos"));
    }

    // Directorio donde cada prueba escribe el contrato parcial de sus operaciones
    public static Path directorioDeOperaciones() {
        return directorio().resolve("operaciones");
    }

    // Directorio de la copia commiteada del contrato (ej. docs/api). Null si el proyecto no la declara
    public static Path destino() {
        String destino = System.getProperty("contratos.destino");
        return destino == null || destino.isBlank() ? null : Path.of(destino);
    }

    // Version de la aplicacion para info.version
    public static String version() {
        return System.getProperty("contratos.version", "0.0.0");
    }

    // true si se corre la suite completa (sin -Dtest): solo entonces se limpia, se exige completitud y se copia
    // el contrato a la copia commiteada
    public static boolean corridaCompleta() {
        String filtro = System.getProperty("test");
        return filtro == null || filtro.isBlank() || filtro.startsWith("${");
    }

    // -Dcontratos.verificar: en vez de copiar, falla si la copia commiteada difiere de la generada
    public static boolean verificar() {
        return Boolean.getBoolean("contratos.verificar");
    }

    // Titulo del contrato
    public static String titulo() {
        return propiedades().getProperty("contratos.titulo", "API");
    }

    // Descripcion general del contrato (markdown)
    public static String descripcion() {
        return propiedades().getProperty("contratos.descripcion", "");
    }

    // Url base de la API (context-path)
    public static String servidor() {
        return propiedades().getProperty("contratos.servidor", "/");
    }

    // Headers globales: nombre -> descripcion, ordenados por nombre
    public static Map<String, String> encabezadosGlobales() {
        Map<String, String> encabezados = new TreeMap<>();
        Properties propiedades = propiedades();
        for (String clave : propiedades.stringPropertyNames()) {
            if (clave.startsWith(PREFIJO_ENCABEZADO)) {
                encabezados.put(clave.substring(PREFIJO_ENCABEZADO.length()), propiedades.getProperty(clave));
            }
        }
        return new LinkedHashMap<>(encabezados);
    }

    // contratos.properties del classpath de test (vacio si no existe), leido en UTF-8
    private static Properties propiedades() {
        Properties propiedades = new Properties();
        try (InputStream entrada = Thread.currentThread().getContextClassLoader().getResourceAsStream(ARCHIVO)) {
            if (entrada != null) {
                propiedades.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ARCHIVO, e);
        }
        return propiedades;
    }
}
