package com.blue.open.Core.Test.Contrato;

import com.blue.open.Core.Kernel.Contrato.Contrato;
import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.ErrorResponseDTO;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Registro de lo que ejecutan las pruebas de contrato. Por cada operacion (metodo de controller) y clase de test arma
 * su contrato parcial: metadatos, parametros con su @Descripcion, schema del body y de cada respuesta, codigos de
 * error provocados y ejemplos reales. Lo escribe en {directorio}/operaciones/{operacion}__{ClaseDeTest}.json;
 * EnsambladorDeContrato junta esos archivos en el openapi.yaml.
 * Hace fallar la prueba (AssertionError) si la operacion no tiene metadatos, si un DTO o parametro del contrato no
 * tiene @Descripcion o si la respuesta real no cumple el schema de su DTO.
 * Dependencias externas: el filesystem (directorio de contratos).
 */
public final class RegistroDeContratos {

    static final ObjectMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private static final GeneradorDeSchemas GENERADOR = new GeneradorDeSchemas();

    private static final String PAQUETE_PROPIO = "com.blue.open.";

    // operacion|ClaseDeTest -> contrato parcial (se reescribe a disco en cada registro)
    private static final Map<String, ObjectNode> OPERACIONES = new LinkedHashMap<>();

    // operacion|status -> schema completo (con $defs) de la respuesta, para validar sin regenerarlo
    private static final Map<String, Optional<ObjectNode>> SCHEMAS_DE_RESPUESTA = new HashMap<>();

    // Operaciones ejecutadas por alguna prueba en esta JVM (las usa la prueba de completitud)
    private static final Set<String> REGISTRADAS = new TreeSet<>();

    // Operaciones a las que ya se les generaron los casos de seguridad automaticos en esta JVM
    private static final Set<String> CON_SEGURIDAD = new HashSet<>();

    private RegistroDeContratos() {
    }

    // Identificador de la operacion en el contrato: Controller.metodo
    public static String idDeOperacion(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
    }

    // Metadatos de la operacion: los del @Contrato del metodo. Null si no tiene
    public static MetadatosDeOperacion metadatos(HandlerMethod handler) {
        Contrato contrato = handler.getMethodAnnotation(Contrato.class);
        return contrato != null ? MetadatosDeOperacion.de(contrato) : null;
    }

    // true la primera vez que se pide para la operacion en esta JVM: los casos de seguridad se generan una vez
    public static synchronized boolean primeraVerificacionDeSeguridad(String operacion) {
        return CON_SEGURIDAD.add(operacion);
    }

    // Operaciones registradas en esta JVM
    public static synchronized Set<String> registradas() {
        return Set.copyOf(REGISTRADAS);
    }

    // Registra el intercambio en el contrato de su operacion, valida la respuesta contra su schema y escribe el
    // archivo de la operacion. Lanza AssertionError si el contrato no se puede publicar
    public static synchronized void registrar(Intercambio intercambio) {
        HandlerMethod handler = intercambio.handler();
        String operacion = idDeOperacion(handler);
        if (intercambio.metadatos() == null) {
            fail("La operacion " + intercambio.metodoHttp() + " " + intercambio.ruta() + " (" + operacion
                    + ") no tiene @Contrato. Anotar el metodo del controller con @Contrato(casoDeUso, resumen, permiso)");
        }
        String clave = operacion + "|" + intercambio.claseDeTest();
        ObjectNode contrato = OPERACIONES.get(clave);
        if (contrato == null) {
            contrato = nuevaOperacion(intercambio, operacion);
            OPERACIONES.put(clave, contrato);
        }
        ObjectNode respuesta = respuesta(contrato, intercambio, operacion);
        validar(intercambio, operacion);
        registrarCodigo(respuesta, intercambio.cuerpoRespuesta(), intercambio.status());
        if (intercambio.publicar()) {
            agregarEjemplo(contrato, intercambio);
        }
        REGISTRADAS.add(operacion);
        escribir(contrato, operacion, intercambio.claseDeTest());
    }

    // Contrato parcial de la operacion: metadatos, parametros y body (sin respuestas ni ejemplos todavia)
    private static ObjectNode nuevaOperacion(Intercambio intercambio, String operacion) {
        HandlerMethod handler = intercambio.handler();
        MetadatosDeOperacion metadatos = intercambio.metadatos();
        ObjectNode contrato = JsonNodeFactory.instance.objectNode();
        contrato.put("operationId", operacion);
        contrato.put("metodo", intercambio.metodoHttp().toLowerCase());
        contrato.put("path", intercambio.ruta().replaceAll("\\{(\\w+):[^}]*}", "{$1}"));
        contrato.put("tag", handler.getBeanType().getSimpleName().replaceFirst("Controller$", ""));
        contrato.put("casoDeUso", metadatos.casoDeUso());
        contrato.put("resumen", metadatos.resumen());
        contrato.put("permiso", metadatos.permisoDeclarado());
        contrato.put("sesion", metadatos.exigeSesion());
        contrato.put("heredado", handler.getMethod().getDeclaringClass() != handler.getBeanType()
                && handler.getMethod().getDeclaringClass().getName().startsWith("com.blue.open.Core."));
        contrato.putObject("schemas");
        contrato.putObject("respuestas");
        contrato.putArray("ejemplos");

        List<String> faltantes = new ArrayList<>();
        ArrayNode parametros = contrato.putArray("parametros");
        ObjectNode multipart = JsonNodeFactory.instance.objectNode();
        for (MethodParameter parametro : handler.getMethodParameters()) {
            parametro.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
            ResolvableType tipo = ResolvableType.forMethodParameter(parametro);
            if (parametro.hasParameterAnnotation(RequestBody.class)) {
                ObjectNode body = contrato.putObject("requestBody");
                body.put("contentType", "application/json");
                body.put("required", parametro.getParameterAnnotation(RequestBody.class).required());
                body.set("schema", schema(contrato, Tipos.aTipo(tipo), faltantes));
                continue;
            }
            if (esArchivo(tipo) || parametro.hasParameterAnnotation(RequestPart.class)) {
                ObjectNode propiedad = esArchivo(tipo) ? archivo(tipo) : schema(contrato, Tipos.aTipo(tipo), faltantes);
                descripcion(parametro, handler, faltantes).ifPresent(d -> propiedad.put("description", d));
                multipart.set(nombre(parametro), propiedad);
                continue;
            }
            String ubicacion = ubicacion(parametro);
            if (ubicacion == null) {
                continue;
            }
            ObjectNode nodo = parametros.addObject();
            nodo.put("name", nombre(parametro));
            nodo.put("in", ubicacion);
            nodo.put("required", requerido(parametro, ubicacion));
            descripcion(parametro, handler, faltantes).ifPresent(d -> nodo.put("description", d));
            nodo.set("schema", schema(contrato, Tipos.aTipo(tipo), faltantes));
        }
        if (!multipart.isEmpty() || !intercambio.partes().isEmpty()) {
            ObjectNode body = contrato.putObject("requestBody");
            body.put("contentType", "multipart/form-data");
            body.put("required", true);
            ObjectNode schema = body.putObject("schema");
            schema.put("type", "object");
            ObjectNode propiedades = schema.putObject("properties");
            if (multipart.isEmpty()) {
                // El controller lee el multipart a mano: se documentan las partes que mando la prueba
                intercambio.partes().forEach((parte, detalle) -> propiedades.putObject(parte)
                        .put("type", "string").put("description", detalle));
            } else {
                propiedades.setAll(multipart);
            }
        }
        if (!faltantes.isEmpty()) {
            fail("Faltan @Descripcion en el contrato de " + operacion + ":\n - " + String.join("\n - ", new TreeSet<>(faltantes)));
        }
        return contrato;
    }

    // Respuesta del status del intercambio en el contrato (la crea con su schema la primera vez)
    private static ObjectNode respuesta(ObjectNode contrato, Intercambio intercambio, String operacion) {
        ObjectNode respuestas = (ObjectNode) contrato.get("respuestas");
        String status = String.valueOf(intercambio.status());
        if (respuestas.has(status)) {
            return (ObjectNode) respuestas.get(status);
        }
        ObjectNode respuesta = respuestas.putObject(status);
        respuesta.put("contentType", intercambio.contentType() == null ? "" : intercambio.contentType());
        respuesta.putArray("codigos");
        schemaDeRespuesta(intercambio, operacion)
                .ifPresent(completo -> respuesta.set("schema", separar(contrato, completo.deepCopy())));
        return respuesta;
    }

    // Schema completo de la respuesta: ErrorResponseDTO para los errores, el tipo de retorno del metodo para el
    // resto. Vacio si la respuesta no es JSON o el metodo no declara un tipo (Object). Lanza AssertionError si un
    // campo del DTO no tiene @Descripcion
    private static Optional<ObjectNode> schemaDeRespuesta(Intercambio intercambio, String operacion) {
        String clave = operacion + "|" + intercambio.status();
        Optional<ObjectNode> cacheado = SCHEMAS_DE_RESPUESTA.get(clave);
        if (cacheado != null) {
            return cacheado;
        }
        Type tipo = null;
        if (intercambio.status() >= 400) {
            tipo = ErrorResponseDTO.class;
        } else if (intercambio.cuerpoRespuesta() != null) {
            HandlerMethod handler = intercambio.handler();
            ResolvableType cuerpo = Tipos.cuerpoDeRespuesta(
                    ResolvableType.forMethodReturnType(handler.getMethod(), handler.getBeanType()));
            if (cuerpo != null && cuerpo.resolve() != Object.class) {
                tipo = Tipos.aTipo(cuerpo);
            }
        }
        Optional<ObjectNode> schema = Optional.empty();
        if (tipo != null) {
            GeneradorDeSchemas.Resultado resultado = GENERADOR.generar(tipo);
            if (!resultado.faltantes().isEmpty()) {
                fail("Faltan @Descripcion en la respuesta " + intercambio.status() + " de " + operacion + ":\n - "
                        + String.join("\n - ", resultado.faltantes()));
            }
            schema = Optional.of(resultado.schema());
        }
        SCHEMAS_DE_RESPUESTA.put(clave, schema);
        return schema;
    }

    // Valida el body real contra el schema de su status. Lanza AssertionError con el detalle de cada campo
    private static void validar(Intercambio intercambio, String operacion) {
        if (intercambio.cuerpoRespuesta() == null) {
            return;
        }
        Optional<ObjectNode> schema = SCHEMAS_DE_RESPUESTA.get(operacion + "|" + intercambio.status());
        if (schema == null || schema.isEmpty()) {
            return;
        }
        List<String> errores = GeneradorDeSchemas.validar(schema.get(), intercambio.cuerpoRespuesta());
        if (!errores.isEmpty()) {
            fail("La respuesta " + intercambio.status() + " de " + operacion + " no cumple el schema de su DTO:\n - "
                    + String.join("\n - ", errores) + "\nBody: " + intercambio.cuerpoRespuesta());
        }
    }

    // Agrega el codigo de error (y el motivo, si viene en datos) a los codigos documentados del status
    private static void registrarCodigo(ObjectNode respuesta, JsonNode cuerpo, int status) {
        if (status < 400 || cuerpo == null || !cuerpo.path("codigo").isString()) {
            return;
        }
        String codigo = cuerpo.get("codigo").asString();
        String motivo = cuerpo.path("datos").path("motivo").asString(null);
        String texto = motivo == null ? codigo : codigo + " (motivo " + motivo + ")";
        ArrayNode codigos = (ArrayNode) respuesta.get("codigos");
        for (JsonNode existente : codigos) {
            if (existente.asString().equals(texto)) {
                return;
            }
        }
        codigos.add(texto);
    }

    // Agrega el ejemplo real (normalizado) con nombre unico dentro de la operacion
    private static void agregarEjemplo(ObjectNode contrato, Intercambio intercambio) {
        ArrayNode ejemplos = (ArrayNode) contrato.get("ejemplos");
        String nombre = intercambio.ejemplo();
        Set<String> usados = new HashSet<>();
        ejemplos.forEach(e -> usados.add(e.get("nombre").asString()));
        String unico = nombre;
        for (int i = 2; usados.contains(unico); i++) {
            unico = nombre + " (" + i + ")";
        }
        NormalizadorDeEjemplos normalizador = new NormalizadorDeEjemplos();
        ObjectNode ejemplo = ejemplos.addObject();
        ejemplo.put("nombre", unico);
        ejemplo.put("actor", intercambio.actor() == null ? "sin autenticación" : intercambio.actor());
        ejemplo.put("status", intercambio.status());
        if (intercambio.cuerpoRequest() != null) {
            ejemplo.set("request", normalizador.normalizar(intercambio.cuerpoRequest()));
        }
        if (!intercambio.partes().isEmpty()) {
            ObjectNode partes = ejemplo.putObject("partes");
            intercambio.partes().forEach(partes::put);
        }
        if (intercambio.cuerpoRespuesta() != null) {
            ejemplo.set("response", normalizador.normalizar(intercambio.cuerpoRespuesta()));
        }
    }

    // Schema de un tipo dentro del contrato: mueve sus $defs a "schemas" y deja la referencia
    private static ObjectNode schema(ObjectNode contrato, Type tipo, List<String> faltantes) {
        GeneradorDeSchemas.Resultado resultado = GENERADOR.generar(tipo);
        faltantes.addAll(resultado.faltantes());
        return separar(contrato, resultado.schema());
    }

    // Quita los $defs del schema y los agrega a los "schemas" del contrato
    private static ObjectNode separar(ObjectNode contrato, ObjectNode schema) {
        JsonNode definiciones = schema.remove("$defs");
        if (definiciones instanceof ObjectNode defs) {
            ((ObjectNode) contrato.get("schemas")).setAll(defs);
        }
        return schema;
    }

    // El parametro recibe uno o varios archivos (MultipartFile, array o coleccion)
    private static boolean esArchivo(ResolvableType tipo) {
        Class<?> clase = tipo.resolve(Object.class);
        if (MultipartFile.class.isAssignableFrom(clase)) {
            return true;
        }
        if (clase.isArray()) {
            return MultipartFile.class.isAssignableFrom(clase.getComponentType());
        }
        return Iterable.class.isAssignableFrom(clase)
                && MultipartFile.class.isAssignableFrom(tipo.getGeneric(0).resolve(Object.class));
    }

    // Schema de una parte de archivo (uno o varios)
    private static ObjectNode archivo(ResolvableType tipo) {
        ObjectNode binario = JsonNodeFactory.instance.objectNode().put("type", "string").put("format", "binary");
        if (MultipartFile.class.isAssignableFrom(tipo.resolve(Object.class))) {
            return binario;
        }
        ObjectNode lista = JsonNodeFactory.instance.objectNode().put("type", "array");
        lista.set("items", binario);
        return lista;
    }

    // path, query o header; null si el parametro no viaja en el request (HttpServletRequest, principal, etc)
    private static String ubicacion(MethodParameter parametro) {
        if (parametro.hasParameterAnnotation(PathVariable.class)) {
            return "path";
        }
        if (parametro.hasParameterAnnotation(RequestParam.class)) {
            return "query";
        }
        if (parametro.hasParameterAnnotation(RequestHeader.class)) {
            return "header";
        }
        return null;
    }

    // Nombre del parametro en el request: el de su anotacion o, si no lo declara, el del parametro Java
    private static String nombre(MethodParameter parametro) {
        String nombre = null;
        PathVariable path = parametro.getParameterAnnotation(PathVariable.class);
        RequestParam query = parametro.getParameterAnnotation(RequestParam.class);
        RequestHeader header = parametro.getParameterAnnotation(RequestHeader.class);
        RequestPart parte = parametro.getParameterAnnotation(RequestPart.class);
        if (path != null) {
            nombre = path.name().isEmpty() ? path.value() : path.name();
        } else if (query != null) {
            nombre = query.name().isEmpty() ? query.value() : query.name();
        } else if (header != null) {
            nombre = header.name().isEmpty() ? header.value() : header.name();
        } else if (parte != null) {
            nombre = parte.name().isEmpty() ? parte.value() : parte.name();
        }
        return nombre == null || nombre.isEmpty() ? parametro.getParameterName() : nombre;
    }

    // El parametro es obligatorio: path siempre; query/header segun required y defaultValue
    private static boolean requerido(MethodParameter parametro, String ubicacion) {
        if ("path".equals(ubicacion)) {
            return true;
        }
        if (parametro.getParameterType() == Optional.class) {
            return false;
        }
        RequestParam query = parametro.getParameterAnnotation(RequestParam.class);
        if (query != null) {
            return query.required() && ValueConstants.DEFAULT_NONE.equals(query.defaultValue());
        }
        RequestHeader header = parametro.getParameterAnnotation(RequestHeader.class);
        return header != null && header.required() && ValueConstants.DEFAULT_NONE.equals(header.defaultValue());
    }

    // @Descripcion del parametro. Si falta en un controller propio, lo registra como faltante
    private static Optional<String> descripcion(MethodParameter parametro, HandlerMethod handler, List<String> faltantes) {
        Descripcion descripcion = parametro.getParameterAnnotation(Descripcion.class);
        if (descripcion != null) {
            return Optional.of(descripcion.value());
        }
        if (handler.getBeanType().getName().startsWith(PAQUETE_PROPIO)) {
            faltantes.add(handler.getMethod().getDeclaringClass().getName() + "#" + handler.getMethod().getName()
                    + "(" + nombre(parametro) + ")");
        }
        return Optional.empty();
    }

    // Escribe el contrato parcial en {directorio}/operaciones/{operacion}__{ClaseDeTest}.json
    private static void escribir(ObjectNode contrato, String operacion, String claseDeTest) {
        Path directorio = ConfiguracionDeContratos.directorioDeOperaciones();
        String simple = claseDeTest.substring(claseDeTest.lastIndexOf('.') + 1);
        try {
            Files.createDirectories(directorio);
            Files.writeString(directorio.resolve(operacion + "__" + simple + ".json"), MAPPER.writeValueAsString(contrato));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el contrato de " + operacion, e);
        }
    }
}
