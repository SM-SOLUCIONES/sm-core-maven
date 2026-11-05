package com.blue.open.Core.Test.Contrato;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.DTO.Pagina;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.MethodScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.generator.TypeScope;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

/**
 * Genera el JSON Schema (draft 2020-12) de una clase DTO para el Contrato de endpoint y valida contra ese schema la
 * respuesta real de una prueba. El schema sale de la clase, no se escribe a mano:
 * - Propiedades segun Jackson (@JsonIgnore, @JsonValue de enums); obligatorios y limites segun Bean Validation.
 * - description = @Descripcion del getter del campo o, si el getter no la tiene, la del campo; y la de la clase. El
 *   getter gana para que un DTO hijo redefina la descripcion de un campo heredado sobreescribiendo su getter.
 * - Fechas como string date-time; con @JsonFormat del formato ISO GMT-3 de entidades-y-dtos.md, ademas el pattern.
 * - Un campo es nullable salvo que Bean Validation lo exija; no se aceptan propiedades fuera del DTO (una respuesta
 *   con un campo que el DTO no declara hace fallar la prueba).
 * Cada objeto queda en $defs con un nombre simple (ej. "DependenciaDTO"); el ensamblador los mueve a
 * components/schemas. Registra los campos de clases del proyecto o del Core (com.blue.open) sin @Descripcion.
 * Dependencias externas: ninguna.
 */
public final class GeneradorDeSchemas {

    // Patron JSON de @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = America/Argentina/Buenos_Aires)
    static final String PATRON_FECHA_HORA_GMT3 = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}-03:00$";

    private static final String FORMATO_ISO_GMT3 = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    // Paquete raiz de las clases que deben documentar sus campos con @Descripcion
    private static final String PAQUETE_PROPIO = "com.blue.open.";

    private static final SchemaRegistry REGISTRO = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    // Campos sin @Descripcion encontrados en la generacion en curso ("Clase.campo")
    private final TreeSet<String> faltantes = new TreeSet<>();

    private final SchemaGenerator generador;

    // Configura el generador de JSON Schema: Jackson, Bean Validation, descripciones desde @Descripcion
    // y patron de fecha GMT-3 en los campos con @JsonFormat
    public GeneradorDeSchemas() {
        SchemaGeneratorConfigBuilder config = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON)
                .with(new JacksonSchemaModule(JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE,
                        JacksonOption.RESPECT_JSONPROPERTY_REQUIRED))
                .with(new JakartaValidationModule(JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                        JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS))
                .with(Option.DEFINITIONS_FOR_ALL_OBJECTS, Option.DEFINITION_FOR_MAIN_SCHEMA,
                        Option.PLAIN_DEFINITION_KEYS, Option.NULLABLE_FIELDS_BY_DEFAULT,
                        Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT, Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES)
                .without(Option.SCHEMA_VERSION_INDICATOR, Option.NONPUBLIC_NONSTATIC_FIELDS_WITHOUT_GETTERS);
        config.forFields().withDescriptionResolver(this::descripcionDelCampo);
        config.forFields().withStringPatternResolver(GeneradorDeSchemas::patronDeFecha);
        config.forTypesInGeneral().withDescriptionResolver(GeneradorDeSchemas::descripcionDelTipo);
        config.forTypesInGeneral().withCustomDefinitionProvider(GeneradorDeSchemas::definicionPropia);
        this.generador = new SchemaGenerator(config.build());
    }

    // Schema del tipo recibido ({"$ref": "#/$defs/X", "$defs": {...}} para objetos; inline para tipos simples) y los
    // campos sin @Descripcion. No es thread-safe: las pruebas de contrato corren en un solo hilo
    public synchronized Resultado generar(Type tipo) {
        faltantes.clear();
        ObjectNode schema = generador.generateSchema(tipo);
        return new Resultado(inlinearSimple(schema), List.copyOf(faltantes));
    }

    // Un tipo simple (String, numero, enum, lista) no queda como definicion propia: se devuelve inline
    private static ObjectNode inlinearSimple(ObjectNode schema) {
        JsonNode ref = schema.get("$ref");
        JsonNode definiciones = schema.get("$defs");
        if (ref == null || !(definiciones instanceof ObjectNode defs)) {
            return schema;
        }
        String nombre = ref.asString().substring(ref.asString().lastIndexOf('/') + 1);
        JsonNode principal = defs.get(nombre);
        if (principal == null || "object".equals(principal.path("type").asString(""))) {
            return schema;
        }
        ObjectNode inline = ((ObjectNode) principal).deepCopy();
        defs.remove(nombre);
        if (!defs.isEmpty()) {
            inline.set("$defs", defs);
        }
        return inline;
    }

    // Valida el JSON recibido contra el schema (con sus $defs). Responde los errores; vacio si cumple
    public static List<String> validar(ObjectNode schema, JsonNode json) {
        Schema compilado = REGISTRO.getSchema(SchemaLocation.of("urn:contrato:respuesta"), schema);
        List<String> errores = new ArrayList<>();
        for (Error error : compilado.validate(json)) {
            errores.add(error.getInstanceLocation() + ": " + error.getMessage());
        }
        return errores;
    }

    // description del campo = @Descripcion de su getter (el mas especifico: el que sobreescribe el DTO hijo) o, si no
    // la tiene, la del campo. Si falta en los dos en una clase propia, lo registra
    private String descripcionDelCampo(FieldScope campo) {
        MethodScope getter = campo.findGetter();
        Descripcion descripcion = getter == null ? null : getter.getAnnotation(Descripcion.class);
        if (descripcion == null) {
            descripcion = campo.getAnnotation(Descripcion.class);
        }
        if (descripcion != null) {
            return descripcion.value();
        }
        Class<?> clase = campo.getDeclaringType().getErasedType();
        if (clase.getName().startsWith(PAQUETE_PROPIO) && !campo.isFakeContainerItemScope()) {
            faltantes.add(clase.getName() + "." + campo.getDeclaredName());
        }
        return null;
    }

    // description del objeto = @Descripcion de la clase, si la tiene
    private static String descripcionDelTipo(TypeScope tipo) {
        Descripcion descripcion = tipo.getType().getErasedType().getAnnotation(Descripcion.class);
        return descripcion == null ? null : descripcion.value();
    }

    // pattern de las fechas serializadas con el formato ISO GMT-3 del proyecto
    private static String patronDeFecha(FieldScope campo) {
        JsonFormat formato = campo.getAnnotationConsideringFieldAndGetter(JsonFormat.class);
        if (formato != null && FORMATO_ISO_GMT3.equals(formato.pattern())) {
            return PATRON_FECHA_HORA_GMT3;
        }
        return null;
    }

    // Tipos que Jackson serializa distinto de lo que victools deduce de sus campos
    private static CustomDefinition definicionPropia(ResolvedType tipo, SchemaGenerationContext contexto) {
        Class<?> clase = tipo.getErasedType();
        ObjectNode nodo = JsonNodeFactory.instance.objectNode();
        if (CodigoDeError.class.isAssignableFrom(clase)) {
            nodo.put("type", "string");
            nodo.put("description", "Código estable del error (ver el catálogo de errores)");
        } else if (clase == HttpStatus.class) {
            // Jackson serializa HttpStatus con su toString: codigo y nombre (ej. "400 BAD_REQUEST")
            nodo.put("type", "string");
            nodo.put("pattern", "^\\d{3} [A-Z_]+$");
        } else if (Date.class.isAssignableFrom(clase) || clase == Instant.class || clase == OffsetDateTime.class
                || clase == ZonedDateTime.class || clase == LocalDateTime.class) {
            nodo.put("type", "string");
            nodo.put("format", "date-time");
        } else if (clase == LocalDate.class) {
            nodo.put("type", "string");
            nodo.put("format", "date");
        } else if (clase == LocalTime.class) {
            nodo.put("type", "string");
            nodo.put("format", "time");
        } else if (clase == byte[].class) {
            nodo.put("type", "string");
            nodo.put("contentEncoding", "base64");
        } else if (MultipartFile.class.isAssignableFrom(clase)) {
            nodo.put("type", "string");
            nodo.put("format", "binary");
        } else if (clase == Object.class || JsonNode.class.isAssignableFrom(clase)) {
            // Cualquier valor JSON
            return new CustomDefinition(nodo, CustomDefinition.DefinitionType.INLINE,
                    CustomDefinition.AttributeInclusion.NO);
        } else if (Pagina.class.isAssignableFrom(clase)) {
            return new CustomDefinition(pagina(tipo, contexto), CustomDefinition.DefinitionType.STANDARD,
                    CustomDefinition.AttributeInclusion.NO);
        } else {
            return null;
        }
        return new CustomDefinition(nodo, CustomDefinition.DefinitionType.INLINE, CustomDefinition.AttributeInclusion.NO);
    }

    // Pagina<T> del kernel tal como la serializa Jackson: contenido con los T y los datos de la paginacion
    private static ObjectNode pagina(ResolvedType tipo, SchemaGenerationContext contexto) {
        ObjectNode pagina = JsonNodeFactory.instance.objectNode();
        pagina.put("type", "object");
        pagina.put("description", "Página de resultados");
        ObjectNode propiedades = pagina.putObject("properties");
        ObjectNode contenido = propiedades.putObject("contenido");
        contenido.put("type", "array");
        contenido.put("description", "Registros de la página");
        List<ResolvedType> parametros = tipo.getTypeBindings().getTypeParameters();
        if (parametros.isEmpty()) {
            parametros = tipo.typeParametersFor(Pagina.class);
        }
        if (parametros != null && !parametros.isEmpty()) {
            contenido.set("items", contexto.createDefinitionReference(parametros.get(0)));
        }
        entero(propiedades, "totalDeElementos", "Total de registros que cumplen los filtros");
        entero(propiedades, "totalDePaginas", "Total de páginas");
        entero(propiedades, "tamano", "Tamaño de la página");
        entero(propiedades, "numero", "Número de página (desde 0)");
        entero(propiedades, "cantidadDeElementos", "Registros en esta página");
        booleano(propiedades, "primera", "Es la primera página");
        booleano(propiedades, "ultima", "Es la última página");
        booleano(propiedades, "vacia", "La página no tiene registros");
        pagina.put("additionalProperties", false);
        return pagina;
    }

    // Agrega una propiedad entera con su descripcion
    private static void entero(ObjectNode propiedades, String nombre, String descripcion) {
        propiedades.putObject(nombre).put("type", "integer").put("description", descripcion);
    }

    // Agrega una propiedad booleana con su descripcion
    private static void booleano(ObjectNode propiedades, String nombre, String descripcion) {
        propiedades.putObject(nombre).put("type", "boolean").put("description", descripcion);
    }

    // Schema generado y campos de clases propias sin @Descripcion ("paquete.Clase.campo")
    public record Resultado(ObjectNode schema, List<String> faltantes) {
    }
}
