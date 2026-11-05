package com.blue.open.Core.Test;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente fluido de una prueba de contrato: arma el request de una operacion como un actor (o sin autenticacion) y
 * lo ejecuta con MockMvc dentro de la transaccion del test. Se obtiene con {@link PruebaDeContrato#como(String)} o
 * {@link PruebaDeContrato#sinAutenticacion()}.
 * Ejemplo: como("SYSADMIN").post("/dependencias/guardar").json(body).ejecutar().esperar(200)
 */
public final class Peticion {

    private final PruebaDeContrato prueba;

    // Rol del actor (se muestra en el ejemplo del contrato). Null = sin autenticacion
    private final String rol;

    // Token Bearer del actor. Null = sin autenticacion
    private final String token;

    private HttpMethod metodo;

    // Ruta de la operacion sin el context-path (ej. "/dependencias/uuid/{uuid}")
    private String ruta;

    private Object[] variables = new Object[0];

    // Body JSON (objeto serializable o String con JSON literal). Null = sin body
    private Object cuerpo;

    private final List<MockMultipartFile> partes = new ArrayList<>();

    private final Map<String, List<String>> parametros = new LinkedHashMap<>();

    private final Map<String, String> encabezados = new LinkedHashMap<>();

    private final Map<String, String> cookies = new LinkedHashMap<>();

    // Nombre del ejemplo en el contrato. Null = nombre del metodo de test
    private String ejemplo;

    // Paso de preparacion del escenario: se valida contra el contrato pero no se publica como ejemplo
    private boolean preparacion;

    Peticion(PruebaDeContrato prueba, String rol, String token) {
        this.prueba = prueba;
        this.rol = rol;
        this.token = token;
    }

    // GET a la ruta recibida (con variables {x} reemplazadas en orden)
    public Peticion get(String ruta, Object... variables) {
        return operacion(HttpMethod.GET, ruta, variables);
    }

    // POST a la ruta recibida
    public Peticion post(String ruta, Object... variables) {
        return operacion(HttpMethod.POST, ruta, variables);
    }

    // PUT a la ruta recibida
    public Peticion put(String ruta, Object... variables) {
        return operacion(HttpMethod.PUT, ruta, variables);
    }

    // PATCH a la ruta recibida
    public Peticion patch(String ruta, Object... variables) {
        return operacion(HttpMethod.PATCH, ruta, variables);
    }

    // DELETE a la ruta recibida
    public Peticion delete(String ruta, Object... variables) {
        return operacion(HttpMethod.DELETE, ruta, variables);
    }

    // Fija verbo, ruta y variables de la operacion
    private Peticion operacion(HttpMethod metodo, String ruta, Object... variables) {
        this.metodo = metodo;
        this.ruta = ruta;
        this.variables = variables;
        return this;
    }

    // Body JSON: un objeto (DTO, Map) que se serializa con el ObjectMapper de la aplicacion, o un String con JSON
    public Peticion json(Object cuerpo) {
        this.cuerpo = cuerpo;
        return this;
    }

    // Parametro de query (?nombre=valor). Se puede repetir
    public Peticion parametro(String nombre, Object valor) {
        parametros.computeIfAbsent(nombre, k -> new ArrayList<>()).add(String.valueOf(valor));
        return this;
    }

    // Header adicional
    public Peticion encabezado(String nombre, Object valor) {
        encabezados.put(nombre, String.valueOf(valor));
        return this;
    }

    // Cookie del request (por ejemplo access_token o refresh_token)
    public Peticion cookie(String nombre, String valor) {
        cookies.put(nombre, valor);
        return this;
    }

    // Archivo de un request multipart/form-data
    public Peticion archivo(String parte, String nombreArchivo, String contentType, byte[] contenido) {
        partes.add(new MockMultipartFile(parte, nombreArchivo, contentType, contenido));
        return this;
    }

    // Parte JSON de un request multipart/form-data (objeto serializable o String con JSON)
    public Peticion parteJson(String parte, Object valor) {
        partes.add(new MockMultipartFile(parte, "", MediaType.APPLICATION_JSON_VALUE,
                prueba.aJson(valor).getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    // Nombre del ejemplo en el contrato (por defecto, el nombre del metodo de test)
    public Peticion ejemplo(String nombre) {
        this.ejemplo = nombre;
        return this;
    }

    // Marca la peticion como paso de preparacion del escenario (crear un dato que el caso necesita): la respuesta se
    // valida contra el contrato igual, pero no se publica como ejemplo de la operacion
    public Peticion preparacion() {
        this.preparacion = true;
        return this;
    }

    // Ejecuta la operacion con MockMvc. Responde la respuesta para assertear
    public Respuesta ejecutar() {
        if (metodo == null) {
            throw new IllegalStateException("Falta indicar la operacion: get/post/put/patch/delete");
        }
        return prueba.ejecutar(this);
    }

    // Arma el request de MockMvc con lo declarado
    AbstractMockHttpServletRequestBuilder<?> construir() {
        AbstractMockHttpServletRequestBuilder<?> builder;
        if (!partes.isEmpty()) {
            MockMultipartHttpServletRequestBuilder multipart = MockMvcRequestBuilders.multipart(metodo, ruta, variables);
            partes.forEach(multipart::file);
            builder = multipart;
        } else {
            builder = MockMvcRequestBuilders.request(metodo, ruta, variables);
            if (cuerpo != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(prueba.aJson(cuerpo));
            }
        }
        builder.characterEncoding(StandardCharsets.UTF_8).accept(MediaType.APPLICATION_JSON, MediaType.ALL);
        parametros.forEach((nombre, valores) -> builder.param(nombre, valores.toArray(String[]::new)));
        encabezados.forEach(builder::header);
        cookies.forEach((nombre, valor) -> builder.cookie(new Cookie(nombre, valor)));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return builder;
    }

    // Copia de esta peticion con otro actor (o sin autenticacion si rol y token son null): la usan los casos de
    // seguridad automaticos para repetir el request del caso feliz
    Peticion conActor(String otroRol, String otroToken) {
        Peticion copia = new Peticion(prueba, otroRol, otroToken);
        copia.metodo = metodo;
        copia.ruta = ruta;
        copia.variables = variables;
        copia.cuerpo = cuerpo;
        copia.partes.addAll(partes);
        parametros.forEach((k, v) -> copia.parametros.put(k, new ArrayList<>(v)));
        copia.encabezados.putAll(encabezados);
        copia.cookies.putAll(cookies);
        return copia;
    }

// Rol del actor (null = sin autenticacion)
    String rol() {
        return rol;
    }

// Body JSON declarado (null = sin body)
    Object cuerpo() {
        return cuerpo;
    }

// Partes del multipart (vacia si no es multipart)
    List<MockMultipartFile> partes() {
        return partes;
    }

// Nombre del ejemplo declarado (null = el del metodo de test)
    String ejemplo() {
        return ejemplo;
    }

// Es un paso de preparacion del escenario
    boolean esPreparacion() {
        return preparacion;
    }
}
