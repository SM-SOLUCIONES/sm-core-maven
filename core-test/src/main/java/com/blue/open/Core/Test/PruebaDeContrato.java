package com.blue.open.Core.Test;

import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Test.Contrato.Intercambio;
import com.blue.open.Core.Test.Contrato.MetadatosDeOperacion;
import com.blue.open.Core.Test.Contrato.RegistroDeContratos;
import com.blue.open.Core.Test.Dobles.DobleEnMemoria;
import com.blue.open.Core.Test.Dobles.EnviadorDeMailEnMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Clase base de las pruebas de contrato: un test por caso de uso ({Entidad}{CasoDeUso}ContratoTest) que ejecuta el
 * endpoint real con MockMvc sobre el PostgreSQL embebido de {@link TestConBaseDeDatos}, dentro de la transaccion del
 * test (rollback al terminar: no deja datos). Expone el cliente fluido ({@link #como(String)},
 * {@link #sinAutenticacion()}) y limpia los dobles en memoria antes de cada test.
 * Cada operacion ejecutada se registra en el Contrato de endpoint (RegistroDeContratos): la respuesta real se valida
 * contra el schema de su DTO y queda como ejemplo. La primera vez que una operacion con permiso responde 2xx se
 * generan solos sus casos de seguridad: el mismo request sin token (401 NO_AUTENTICADO) y con un actor sin ese
 * permiso (403 SIN_PERMISO).
 * Un paso de preparacion (Peticion.preparacion()) se valida si su operacion ya tiene contrato, pero no se publica ni
 * genera casos de seguridad; si su operacion todavia no tiene contrato, no se registra.
 * Dependencias externas: ninguna (los servicios externos son dobles en memoria, ver Core.Test.Dobles).
 */
public abstract class PruebaDeContrato extends TestConBaseDeDatos {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ActoresDePrueba actores;

    @Autowired
    private ApplicationContext contexto;

    // Clase y metodo del test en curso: nombre por defecto de los ejemplos del contrato
    private String claseDeTest;

    private String metodoDeTest;

    // Vacia los dobles en memoria: cada test arranca sin archivos ni mails de otro test
    @BeforeEach
    void limpiarDobles() {
        contexto.getBeansOfType(DobleEnMemoria.class).values().forEach(DobleEnMemoria::limpiar);
    }

    // Recuerda que test se esta ejecutando
    @BeforeEach
    void recordarTest(TestInfo info) {
        claseDeTest = info.getTestClass().map(Class::getName).orElse(getClass().getName());
        metodoDeTest = info.getTestMethod().map(Method::getName).orElse(info.getDisplayName());
    }

    // Peticion como el actor del rol recibido (token Bearer de ActoresDePrueba)
    protected Peticion como(String rol) {
        return new Peticion(this, rol, actores.token(rol));
    }

    // Peticion sin header Authorization
    protected Peticion sinAutenticacion() {
        return new Peticion(this, null, null);
    }

    // Doble del EnviadorDeMail, para assertear los mails enviados. El doble de Crypter (CrypterEnMemoria) se
    // inyecta con @Autowired en la prueba que lo necesite: no se expone aca porque core-crypter es opcional
    protected EnviadorDeMailEnMemoria mails() {
        return contexto.getBean(EnviadorDeMailEnMemoria.class);
    }

    // Ejecuta la peticion, la registra en el contrato y, la primera vez, genera los casos de seguridad
    Respuesta ejecutar(Peticion peticion) {
        Respuesta respuesta = enviar(peticion);
        String ejemplo = peticion.ejemplo() != null ? peticion.ejemplo() : frase(metodoDeTest);
        registrar(peticion, respuesta, ejemplo, !peticion.esPreparacion());
        verificarSeguridad(peticion, respuesta);
        return respuesta;
    }

    // Ejecuta la peticion con MockMvc
    private Respuesta enviar(Peticion peticion) {
        try {
            MvcResult resultado = mockMvc.perform(peticion.construir()).andReturn();
            return new Respuesta(resultado, objectMapper);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo ejecutar la operacion con MockMvc", e);
        }
    }

    // Registra el intercambio en el contrato de la operacion que lo atendio (nada si ningun controller lo atendio)
    private void registrar(Peticion peticion, Respuesta respuesta, String ejemplo, boolean publicar) {
        MvcResult resultado = respuesta.resultado();
        if (!(resultado.getHandler() instanceof HandlerMethod handler)) {
            return;
        }
        MetadatosDeOperacion metadatos = RegistroDeContratos.metadatos(handler);
        if (metadatos == null && !publicar) {
            // Paso de preparacion contra un endpoint que todavia no tiene contrato: no es la operacion bajo prueba
            return;
        }
        String ruta = (String) resultado.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String contentType = resultado.getResponse().getContentType();
        JsonNode cuerpoRespuesta = esJson(contentType) && !respuesta.texto().isEmpty() ? respuesta.json() : null;
        JsonNode cuerpoRequest = peticion.cuerpo() == null ? null : objectMapper.readTree(aJson(peticion.cuerpo()));
        RegistroDeContratos.registrar(new Intercambio(handler, resultado.getRequest().getMethod(), ruta,
                metadatos, claseDeTest, ejemplo, peticion.rol(),
                cuerpoRequest, partes(peticion), respuesta.status(), contentType, cuerpoRespuesta, publicar));
    }

    // Casos de seguridad automaticos de una operacion que exige sesion, la primera vez que responde 2xx en la JVM:
    // repite el request sin token y, si exige un rol o permiso, como un actor sin ninguno de ellos.
    // Lanzan AssertionError si el endpoint no exige lo que declara su @Contrato
    private void verificarSeguridad(Peticion peticion, Respuesta respuesta) {
        if (respuesta.status() >= 300 || peticion.rol() == null || peticion.esPreparacion()
                || !(respuesta.resultado().getHandler() instanceof HandlerMethod handler)) {
            return;
        }
        MetadatosDeOperacion metadatos = RegistroDeContratos.metadatos(handler);
        String operacion = RegistroDeContratos.idDeOperacion(handler);
        if (metadatos == null || !metadatos.exigeSesion()
                || !RegistroDeContratos.primeraVerificacionDeSeguridad(operacion)) {
            return;
        }
        Peticion sinToken = peticion.conActor(null, null);
        Respuesta rechazo = enviar(sinToken);
        if (rechazo.status() != 401
                || !CodigoDeErrorKernel.NO_AUTENTICADO.codigo().equals(rechazo.json().path("codigo").asString(null))) {
            fail(operacion + " exige sesion (" + metadatos.permiso() + ") pero sin token respondio "
                    + rechazo.status() + ": " + rechazo.texto());
        }
        registrar(sinToken, rechazo, "Sin autenticación", true);
        if (!metadatos.exigePermiso()) {
            return;
        }
        actores.rolSinPermiso(metadatos.permiso()).ifPresent(rol -> {
            Peticion sinPermiso = peticion.conActor(rol, actores.token(rol));
            Respuesta prohibido = enviar(sinPermiso);
            if (prohibido.status() != 403
                    || !CodigoDeErrorKernel.SIN_PERMISO.codigo().equals(prohibido.json().path("codigo").asString(null))) {
                fail(operacion + " declara el permiso " + metadatos.permiso() + " pero el rol " + rol
                        + " (sin ese permiso) recibio " + prohibido.status() + ": " + prohibido.texto());
            }
            registrar(sinPermiso, prohibido, "Sin permiso", true);
        });
    }

    // Partes de un multipart para el contrato: nombre -> descripcion del contenido
    private static Map<String, String> partes(Peticion peticion) {
        Map<String, String> partes = new LinkedHashMap<>();
        for (MockMultipartFile parte : peticion.partes()) {
            boolean json = esJson(parte.getContentType()) && parte.getOriginalFilename().isEmpty();
            partes.put(parte.getName(), json ? "JSON (" + parte.getSize() + " bytes)"
                    : "Archivo " + parte.getOriginalFilename() + " (" + parte.getContentType() + ", " + parte.getSize()
                            + " bytes)");
        }
        return partes;
    }

    // El content type es JSON (application/json o +json)
    private static boolean esJson(String contentType) {
        return contentType != null && (contentType.startsWith("application/json") || contentType.contains("+json"));
    }

    // "eliminaUnaDependencia" -> "Elimina una dependencia"; "anteriorALaVigente" -> "Anterior a la vigente"
    private static String frase(String metodo) {
        String palabras = metodo.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2")
                .toLowerCase();
        return palabras.isEmpty() ? metodo : Character.toUpperCase(palabras.charAt(0)) + palabras.substring(1);
    }

    // Serializa el body con el ObjectMapper de la aplicacion (un String se toma como JSON literal)
    String aJson(Object valor) {
        return valor instanceof String texto ? texto : objectMapper.writeValueAsString(valor);
    }
}
