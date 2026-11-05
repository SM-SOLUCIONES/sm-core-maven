package com.blue.open.Core.Test.Contrato;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que el schema sale de la clase DTO (descripciones, obligatorios, limites, fechas GMT-3) y que una
 * respuesta que no cumple el DTO se detecta.
 */
class GeneradorDeSchemasTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final GeneradorDeSchemas generador = new GeneradorDeSchemas();

    @Test
    void elSchemaTomaDescripcionObligatoriosYLimitesDelDto() {
        JsonNode schema = generador.generar(Producto.class).schema();
        JsonNode producto = schema.get("$defs").get("Producto");

        assertEquals("#/$defs/Producto", schema.get("$ref").asString());
        assertEquals("Nombre comercial", producto.get("properties").get("nombre").get("description").asString());
        assertEquals(40, producto.get("properties").get("nombre").get("maxLength").asInt());
        assertTrue(producto.get("required").toString().contains("nombre"));
        assertEquals(GeneradorDeSchemas.PATRON_FECHA_HORA_GMT3,
                producto.get("properties").get("alta").get("pattern").asString());
    }

    @Test
    void registraLosCamposSinDescripcion() {
        List<String> faltantes = generador.generar(Producto.class).faltantes();

        assertEquals(List.of(Producto.class.getName() + ".sinDescripcion"), faltantes);
    }

    @Test
    void unTipoSimpleQuedaInline() {
        JsonNode schema = generador.generar(String.class).schema();

        assertFalse(schema.has("$ref"));
        assertEquals("string", schema.get("type").asString());
    }

    @Test
    void unaRespuestaQueCumpleElDtoNoTieneErrores() {
        JsonNode respuesta = MAPPER.readTree("""
                {"nombre": "Yerba", "alta": "2026-10-07T10:46:00.123-03:00", "sinDescripcion": null}""");

        assertTrue(GeneradorDeSchemas.validar(generador.generar(Producto.class).schema(), respuesta).isEmpty());
    }

    @Test
    void unaRespuestaConUnCampoQueElDtoNoDeclaraEsUnError() {
        JsonNode respuesta = MAPPER.readTree("""
                {"nombre": "Yerba", "precio": 10}""");

        assertFalse(GeneradorDeSchemas.validar(generador.generar(Producto.class).schema(), respuesta).isEmpty());
    }

    @Test
    void unaFechaFueraDeGmt3EsUnError() {
        JsonNode respuesta = MAPPER.readTree("""
                {"nombre": "Yerba", "alta": "2026-10-07T13:46:00.123Z"}""");

        assertFalse(GeneradorDeSchemas.validar(generador.generar(Producto.class).schema(), respuesta).isEmpty());
    }

    @Test
    void laDescripcionDelGetterDelHijoGanaSobreLaDelCampoHeredado() {
        JsonNode schema = generador.generar(Contenedor.class).schema();
        JsonNode padre = schema.get("$defs").get("Padre").get("properties");
        JsonNode hijo = schema.get("$defs").get("Hijo").get("properties");

        assertEquals("Estado del padre", padre.get("estado").get("description").asString());
        assertEquals("Estado propio del hijo", hijo.get("estado").get("description").asString());
        assertEquals("Codigo del padre", hijo.get("codigo").get("description").asString());
    }

    @Test
    void unaDescripcionEnElGetterCompletaUnCampoHeredadoSinDescripcion() {
        assertEquals(List.of(), generador.generar(Hijo.class).faltantes());
        assertEquals(List.of(Padre.class.getName() + ".extra"), generador.generar(Padre.class).faltantes());
    }

    // DTO con un campo heredado cuya descripcion redefine el hijo (Hijo.getEstado)
    public static class Padre {

        @Descripcion("Estado del padre")
        private int estado;

        @Descripcion("Codigo del padre")
        private String codigo;

        // Sin @Descripcion en el campo: la aporta el getter de Hijo
        private String extra;

        public int getEstado() {
            return estado;
        }

        public String getCodigo() {
            return codigo;
        }

        public String getExtra() {
            return extra;
        }
    }

    // Hijo de Padre: redefine la descripcion de estado y completa la de extra con sus getters
    public static class Hijo extends Padre {

        @Override
        @Descripcion("Estado propio del hijo")
        public int getEstado() {
            return super.getEstado();
        }

        @Override
        @Descripcion("Dato extra del hijo")
        public String getExtra() {
            return super.getExtra();
        }
    }

    // Referencia al padre y al hijo para que los dos queden en $defs
    public static class Contenedor {

        @Descripcion("Un padre")
        private Padre padre;

        @Descripcion("Un hijo")
        private Hijo hijo;

        public Padre getPadre() {
            return padre;
        }

        public Hijo getHijo() {
            return hijo;
        }
    }

    // DTO de ejemplo
    public static class Producto {

        @Descripcion("Nombre comercial")
        @NotBlank
        @Size(max = 40)
        private String nombre;

        @Descripcion("Fecha de alta")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                timezone = "America/Argentina/Buenos_Aires")
        private Timestamp alta;

        private String sinDescripcion;

        public String getNombre() {
            return nombre;
        }

        public Timestamp getAlta() {
            return alta;
        }

        public String getSinDescripcion() {
            return sinDescripcion;
        }
    }
}
