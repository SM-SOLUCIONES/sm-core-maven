package com.blue.open.Core.Test.Contrato;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Ensambla el Contrato de endpoint (OpenAPI 3.1) a partir de los contratos parciales que escribieron las pruebas
 * ({directorio}/operaciones/*.json): junta las partes de una misma operacion que vienen de distintas clases de test,
 * une en una operacion las variantes de un endpoint que comparten path y verbo (un content type del body por variante),
 * mueve los schemas a components/schemas, agrega el header global de cada operacion, el esquema de seguridad Bearer
 * y el catalogo de errores. Escribe openapi.yaml e index.html (Redoc empaquetado en core-test, sin CDN, con el
 * contrato embebido para abrirlo como archivo).
 * Dependencias externas: el filesystem.
 */
public final class EnsambladorDeContrato {

    static final String OPENAPI = "openapi.yaml";

    static final String HTML = "index.html";

    // Propiedad interna de cada ejemplo: content type del body de la variante que lo registro
    private static final String VARIANTE = "contentTypeDeLaVariante";

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .disable(YAMLWriteFeature.SPLIT_LINES)
            .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE)
            .build();

    private EnsambladorDeContrato() {
    }

    // Lee los contratos parciales, arma el OpenAPI y escribe openapi.yaml e index.html en el directorio de salida.
    // Responde false si no hay ninguna operacion registrada (no escribe nada)
    public static boolean ensamblar() {
        Path operaciones = ConfiguracionDeContratos.directorioDeOperaciones();
        if (!Files.isDirectory(operaciones)) {
            return false;
        }
        List<ObjectNode> parciales = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(operaciones)) {
            for (Path archivo : archivos.filter(a -> a.toString().endsWith(".json")).sorted().toList()) {
                parciales.add((ObjectNode) RegistroDeContratos.MAPPER.readTree(Files.readString(archivo)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudieron leer los contratos parciales", e);
        }
        if (parciales.isEmpty()) {
            return false;
        }
        ObjectNode openapi = openapi(parciales);
        try {
            Path directorio = ConfiguracionDeContratos.directorio();
            Files.createDirectories(directorio);
            Files.writeString(directorio.resolve(OPENAPI), YAML.writeValueAsString(openapi));
            Files.writeString(directorio.resolve(HTML), html(openapi));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el contrato ensamblado", e);
        }
        return true;
    }

    // OpenAPI completo a partir de los contratos parciales
    static ObjectNode openapi(List<ObjectNode> parciales) {
        Map<String, ObjectNode> operaciones = new TreeMap<>();
        ObjectNode schemas = JsonNodeFactory.instance.objectNode();
        Map<String, String> globales = ConfiguracionDeContratos.encabezadosGlobales();
        for (ObjectNode parcial : parciales) {
            ObjectNode actual = operaciones.get(parcial.get("operationId").asString());
            operaciones.put(parcial.get("operationId").asString(), actual == null ? parcial.deepCopy() : fusionar(actual, parcial));
        }

        ObjectNode openapi = JsonNodeFactory.instance.objectNode();
        openapi.put("openapi", "3.1.0");
        ObjectNode info = openapi.putObject("info");
        info.put("title", ConfiguracionDeContratos.titulo());
        info.put("version", ConfiguracionDeContratos.version());
        List<CatalogoDeErrores.Entrada> catalogo = CatalogoDeErrores.leer();
        info.put("description", descripcion(globales, catalogo));
        openapi.putArray("servers").addObject().put("url", ConfiguracionDeContratos.servidor());

        // OpenAPI admite una operacion por path y verbo: las variantes de un mismo endpoint (metodos del controller que
        // se distinguen por el content type del body) se unen en una
        Map<String, ObjectNode> porVerbo = new TreeMap<>();
        for (ObjectNode operacion : operaciones.values()) {
            String clave = operacion.get("path").asString() + " " + operacion.get("metodo").asString();
            ObjectNode actual = porVerbo.get(clave);
            porVerbo.put(clave, actual == null ? conVariantes(operacion) : unirVariante(actual, operacion));
        }

        Set<String> tags = new TreeSet<>();
        ObjectNode paths = JsonNodeFactory.instance.objectNode();
        Map<String, ObjectNode> porPath = new TreeMap<>();
        for (ObjectNode operacion : porVerbo.values()) {
            schemas.setAll((ObjectNode) operacion.get("schemas"));
            tags.add(operacion.get("tag").asString());
            ObjectNode path = porPath.computeIfAbsent(operacion.get("path").asString(),
                    p -> JsonNodeFactory.instance.objectNode());
            path.set(operacion.get("metodo").asString(), operacion(operacion, globales));
        }
        ArrayNode listaDeTags = openapi.putArray("tags");
        tags.forEach(tag -> listaDeTags.addObject().put("name", tag));
        porPath.forEach(paths::set);
        openapi.set("paths", paths);

        ObjectNode components = openapi.putObject("components");
        ObjectNode ordenados = components.putObject("schemas");
        new TreeMap<>(comoMapa(schemas)).forEach(ordenados::set);
        if (!globales.isEmpty()) {
            ObjectNode parametros = components.putObject("parameters");
            globales.forEach((nombre, descripcion) -> {
                ObjectNode header = parametros.putObject(nombre);
                header.put("name", nombre);
                header.put("in", "header");
                header.put("required", false);
                header.put("description", descripcion);
                header.putObject("schema").put("type", "string");
            });
        }
        components.putObject("securitySchemes").putObject("bearer")
                .put("type", "http").put("scheme", "bearer")
                .put("description", "Token de acceso del usuario (header Authorization: Bearer {token})");

        ArrayNode anexo = openapi.putArray("x-catalogo-de-errores");
        catalogo.forEach(e -> anexo.addObject().put("codigo", e.codigo()).put("status", e.status())
                .put("mensajePorDefecto", e.mensaje()).put("cuando", e.cuando()).put("origen", e.origen()));
        reescribirReferencias(openapi);
        return openapi;
    }

    // Une dos contratos parciales de la misma operacion (de distintas clases de test): respuestas, codigos, schemas
    // y ejemplos. Metadatos, parametros y body son los mismos en ambos
    private static ObjectNode fusionar(ObjectNode actual, ObjectNode otro) {
        ((ObjectNode) actual.get("schemas")).setAll((ObjectNode) otro.get("schemas"));
        ObjectNode respuestas = (ObjectNode) actual.get("respuestas");
        for (Map.Entry<String, JsonNode> entrada : otro.get("respuestas").properties()) {
            ObjectNode existente = (ObjectNode) respuestas.get(entrada.getKey());
            if (existente == null) {
                respuestas.set(entrada.getKey(), entrada.getValue().deepCopy());
                continue;
            }
            Set<String> codigos = new LinkedHashSet<>();
            existente.get("codigos").forEach(c -> codigos.add(c.asString()));
            entrada.getValue().get("codigos").forEach(c -> codigos.add(c.asString()));
            ArrayNode union = existente.putArray("codigos");
            codigos.forEach(union::add);
            if (!existente.has("schema") && entrada.getValue().has("schema")) {
                existente.set("schema", entrada.getValue().get("schema").deepCopy());
            }
        }
        ArrayNode ejemplos = (ArrayNode) actual.get("ejemplos");
        otro.get("ejemplos").forEach(e -> ejemplos.add(e.deepCopy()));
        return actual;
    }

    // Anota la operacion como su unica variante (operationId, resumen y body) y marca cada ejemplo con el content type
    // de ese body, para repartirlos si despues se le une otra variante del mismo path y verbo
    private static ObjectNode conVariantes(ObjectNode operacion) {
        JsonNode body = operacion.get("requestBody");
        String contentType = body == null ? "" : body.get("contentType").asString();
        operacion.get("ejemplos").forEach(e -> ((ObjectNode) e).put(VARIANTE, contentType));
        ObjectNode variante = operacion.putArray("variantes").addObject()
                .put("operationId", operacion.get("operationId").asString())
                .put("resumen", operacion.get("resumen").asString());
        if (body != null) {
            variante.set("requestBody", body.deepCopy());
        }
        return operacion;
    }

    // Une a la operacion otra variante del mismo path y verbo: sus respuestas, codigos, schemas y ejemplos se fusionan
    // y su body se agrega como otro content type. Metadatos y parametros quedan los de la primera
    private static ObjectNode unirVariante(ObjectNode actual, ObjectNode otra) {
        conVariantes(otra);
        fusionar(actual, otra);
        ((ArrayNode) actual.get("variantes")).addAll((ArrayNode) otra.get("variantes"));
        return actual;
    }

    // Operacion de OpenAPI a partir del contrato parcial ya fusionado
    private static ObjectNode operacion(ObjectNode parcial, Map<String, String> globales) {
        ObjectNode operacion = JsonNodeFactory.instance.objectNode();
        operacion.putArray("tags").add(parcial.get("tag").asString());
        operacion.put("operationId", parcial.get("operationId").asString());
        operacion.put("summary", parcial.get("resumen").asString());
        String permiso = parcial.get("permiso").asString();
        // Un parcial de una corrida anterior puede no tener "sesion": ahi exigir sesion era lo mismo que exigir permiso
        boolean sesion = parcial.has("sesion") ? parcial.get("sesion").asBoolean() : !permiso.isEmpty();
        StringBuilder descripcion = new StringBuilder("**Caso de uso:** " + parcial.get("casoDeUso").asString() + "\n\n"
                + (!permiso.isEmpty() ? "**Permiso:** `" + permiso + "`"
                        : sesion ? "**Requiere sesión:** sin rol ni permiso puntual."
                        : "**Pública:** no requiere autenticación."));
        JsonNode variantes = parcial.get("variantes");
        if (variantes.size() > 1) {
            descripcion.append("\n\n**Variantes según el Content-Type del body:**");
            variantes.forEach(v -> descripcion.append("\n- `")
                    .append(v.has("requestBody") ? v.get("requestBody").get("contentType").asString() : "sin body")
                    .append("`: ").append(v.get("resumen").asString()));
        }
        operacion.put("description", descripcion.toString());
        operacion.put("x-caso-de-uso", parcial.get("casoDeUso").asString());
        if (!permiso.isEmpty()) {
            operacion.put("x-permiso", permiso);
        }
        if (sesion) {
            operacion.putArray("security").addObject().putArray("bearer");
        } else {
            operacion.putArray("security");
        }

        ArrayNode parametros = operacion.putArray("parameters");
        Set<String> declarados = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        parcial.get("parametros").forEach(p -> {
            parametros.add(p.deepCopy());
            declarados.add(p.get("name").asString());
        });
        globales.keySet().stream().filter(nombre -> !declarados.contains(nombre))
                .forEach(nombre -> parametros.addObject().put("$ref", "#/components/parameters/" + nombre));

        List<JsonNode> ejemplos = new ArrayList<>();
        parcial.get("ejemplos").forEach(ejemplos::add);

        for (JsonNode variante : variantes) {
            JsonNode body = variante.get("requestBody");
            if (body == null) {
                continue;
            }
            String contentType = body.get("contentType").asString();
            ObjectNode requestBody = operacion.has("requestBody") ? (ObjectNode) operacion.get("requestBody")
                    : operacion.putObject("requestBody").put("required", true);
            requestBody.put("required", requestBody.get("required").asBoolean() && body.get("required").asBoolean());
            ObjectNode contenidos = requestBody.has("content") ? (ObjectNode) requestBody.get("content")
                    : requestBody.putObject("content");
            ObjectNode contenido = contenidos.putObject(contentType);
            contenido.set("schema", body.get("schema").deepCopy());
            ObjectNode ejemplosDelCuerpo = JsonNodeFactory.instance.objectNode();
            Set<JsonNode> vistos = new HashSet<>();
            for (JsonNode ejemplo : ejemplos) {
                JsonNode valor = ejemplo.has("request") ? ejemplo.get("request") : ejemplo.get("partes");
                // Un mismo body (ej. el del caso feliz repetido por los casos de seguridad) se muestra una vez
                if (valor != null && contentType.equals(ejemplo.get(VARIANTE).asString()) && vistos.add(valor)) {
                    ejemplosDelCuerpo.putObject(clave(ejemplo)).put("summary", resumen(ejemplo)).set("value", valor.deepCopy());
                }
            }
            if (!ejemplosDelCuerpo.isEmpty()) {
                contenido.set("examples", ejemplosDelCuerpo);
            }
        }

        ObjectNode respuestas = operacion.putObject("responses");
        new TreeMap<>(comoMapa((ObjectNode) parcial.get("respuestas"))).forEach((status, datos) -> {
            ObjectNode respuesta = respuestas.putObject(status);
            respuesta.put("description", descripcionDeRespuesta(Integer.parseInt(status), datos));
            String contentType = datos.get("contentType").asString();
            if (contentType.isEmpty() && !datos.has("schema")) {
                return;
            }
            ObjectNode contenido = respuesta.putObject("content")
                    .putObject(contentType.isEmpty() ? "application/json" : contentType.split(";")[0].trim());
            if (datos.has("schema")) {
                contenido.set("schema", datos.get("schema").deepCopy());
            }
            ObjectNode ejemplosDeRespuesta = JsonNodeFactory.instance.objectNode();
            for (JsonNode ejemplo : ejemplos) {
                if (String.valueOf(ejemplo.get("status").asInt()).equals(status) && ejemplo.has("response")) {
                    ejemplosDeRespuesta.putObject(clave(ejemplo)).put("summary", resumen(ejemplo))
                            .set("value", ejemplo.get("response").deepCopy());
                }
            }
            if (!ejemplosDeRespuesta.isEmpty()) {
                contenido.set("examples", ejemplosDeRespuesta);
            }
        });
        return operacion;
    }

    // "OK" para los 2xx; para los errores, los codigos provocados por las pruebas (y su motivo, si lo tienen)
    private static String descripcionDeRespuesta(int status, JsonNode datos) {
        if (status < 400) {
            return "OK";
        }
        Set<String> codigos = new TreeSet<>();
        datos.get("codigos").forEach(c -> {
            String texto = c.asString();
            int motivo = texto.indexOf(" (motivo ");
            codigos.add(motivo < 0 ? "`" + texto + "`"
                    : "`" + texto.substring(0, motivo) + "` (motivo `" + texto.substring(motivo + 9, texto.length() - 1) + "`)");
        });
        return codigos.isEmpty() ? "Error" : "Errores: " + String.join(", ", codigos);
    }

    // Clave del ejemplo en OpenAPI (sin espacios ni acentos)
    private static String clave(JsonNode ejemplo) {
        String sinAcentos = Normalizer.normalize(ejemplo.get("nombre").asString(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "").toLowerCase();
    }

    // Nombre del ejemplo y el rol con el que se ejecuto
    private static String resumen(JsonNode ejemplo) {
        String actor = ejemplo.get("actor").asString();
        return actor.equals("sin autenticación") ? ejemplo.get("nombre").asString()
                : ejemplo.get("nombre").asString() + " (" + actor + ")";
    }

    // Descripcion general: la del proyecto, los headers globales y el catalogo de errores en una tabla
    private static String descripcion(Map<String, String> globales, List<CatalogoDeErrores.Entrada> catalogo) {
        StringBuilder texto = new StringBuilder(ConfiguracionDeContratos.descripcion());
        texto.append("\n\nContrato generado desde las pruebas de contrato: cada operación, schema y ejemplo salió de ")
                .append("ejecutar el endpoint real. No se edita a mano.");
        if (!globales.isEmpty()) {
            texto.append("\n\n## Headers globales\n\nSe aceptan en cualquier operación:\n");
            globales.forEach((nombre, descripcion) -> texto.append("\n- `").append(nombre).append("`: ")
                    .append(descripcion));
        }
        texto.append("\n\n## Catálogo de errores\n\nToda respuesta de error trae `codigo`: el front discrimina por ")
                .append("ese campo, nunca por `mensajeParaUsuario`.\n\n| Código | HTTP | Mensaje por defecto | Cuándo ocurre |\n")
                .append("|---|---|---|---|\n");
        catalogo.forEach(e -> texto.append("| `").append(e.codigo()).append("` | ").append(e.status()).append(" | ")
                .append(e.mensaje().replace("|", "\\|")).append(" | ").append(e.cuando().replace("|", "\\|"))
                .append(" |\n"));
        return texto.toString();
    }

    // Las referencias "#/$defs/X" del generador pasan a "#/components/schemas/X"
    private static void reescribirReferencias(JsonNode nodo) {
        if (nodo instanceof ObjectNode objeto) {
            JsonNode ref = objeto.get("$ref");
            if (ref != null && ref.isString() && ref.asString().startsWith("#/$defs/")) {
                objeto.put("$ref", "#/components/schemas/" + ref.asString().substring("#/$defs/".length()));
            }
            objeto.forEach(EnsambladorDeContrato::reescribirReferencias);
        } else if (nodo instanceof ArrayNode array) {
            array.forEach(EnsambladorDeContrato::reescribirReferencias);
        }
    }

    // Propiedades del objeto JSON como mapa, en su orden
    private static Map<String, JsonNode> comoMapa(ObjectNode objeto) {
        Map<String, JsonNode> mapa = new LinkedHashMap<>();
        objeto.properties().forEach(e -> mapa.put(e.getKey(), e.getValue()));
        return mapa;
    }

    // index.html con Redoc y el contrato embebidos (se abre sin servidor y sin salida a internet)
    private static String html(ObjectNode openapi) throws IOException {
        String plantilla = recurso("contratos/plantilla.html");
        String redoc = recurso("contratos/redoc.standalone.js");
        String spec = RegistroDeContratos.MAPPER.writeValueAsString(openapi).replace("</", "<\\/");
        return plantilla.replace("{{TITULO}}", ConfiguracionDeContratos.titulo() + " " + ConfiguracionDeContratos.version())
                .replace("{{SPEC}}", spec)
                .replace("{{REDOC}}", redoc.replace("</script", "<\\/script"));
    }

    // Contenido UTF-8 de un recurso de core-test. Lanza IOException si no existe
    private static String recurso(String nombre) throws IOException {
        try (InputStream entrada = EnsambladorDeContrato.class.getClassLoader().getResourceAsStream(nombre)) {
            if (entrada == null) {
                throw new IOException("Falta el recurso " + nombre + " en core-test");
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
