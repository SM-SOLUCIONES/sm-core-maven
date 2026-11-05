package com.blue.open.Core.Test;

import com.blue.open.Core.Crud.Excepciones.CodigoDeErrorCrud;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de contrato del ABM de una entidad, para los controllers escritos con la receta de
 * core/core-crud/docs/receta-controller.md (GET /uuid/{uuid}, POST /paginar, POST /guardar, DELETE /borrar/{uuid}). El proyecto
 * la extiende una vez por entidad ({Entidad}ContratoTest) indicando el controller, el actor, un uuid vigente de los
 * seeds, un cuerpo valido y uno invalido. Los metadatos (caso de uso, resumen, permiso) salen del @Contrato de cada
 * metodo del controller. Una operacion que el controller no publica se saltea. Casos comunes que cubre:
 * - uuid: obtener un registro vigente; uuid inexistente (REGISTRO_NO_ENCONTRADO).
 * - paginar: primera pagina.
 * - guardar: alta con el cuerpo valido; cuerpo invalido (FORMULARIO_INVALIDO).
 * - borrar: eliminar un registro recien creado; uuid inexistente y doble borrado (REGISTRO_NO_ENCONTRADO).
 * Los casos de seguridad los genera PruebaDeContrato. Los casos propios de la entidad (reglas de negocio) van en
 * pruebas aparte, una por caso de uso.
 */
public abstract class ContratoDeAbmTest extends PruebaDeContrato {

    private static final String UUID_INEXISTENTE = "ffffffff-ffff-4fff-bfff-ffffffffffff";

    // Operaciones del ABM de la receta: verbo y ruta relativa al @RequestMapping del controller
    private enum OperacionAbm {
        OBTENER(RequestMethod.GET, "/uuid/{uuid}"),
        PAGINAR(RequestMethod.POST, "/paginar"),
        GUARDAR(RequestMethod.POST, "/guardar"),
        ELIMINAR(RequestMethod.DELETE, "/borrar/{uuid}");

        private final RequestMethod verbo;

        private final String ruta;

        OperacionAbm(RequestMethod verbo, String ruta) {
            this.verbo = verbo;
            this.ruta = ruta;
        }
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    // Controller de la entidad (de su @RequestMapping sale la ruta)
    protected abstract Class<?> controlador();

    // Rol del actor que ejecuta los casos (debe tener los permisos de lectura y escritura)
    protected abstract String rol();

    // Uuid de un registro vigente de los seeds
    protected abstract String uuidVigente();

    // Cuerpo de un alta valida (DTO, Map o JSON literal)
    protected abstract Object cuerpoValido();

    // Cuerpo que no pasa Bean Validation
    protected abstract Object cuerpoInvalido();

    // Cuerpo del paginado del caso feliz (por defecto, la primera pagina de 5)
    protected Object paginado() {
        return Map.of("pagina", 1, "tamano", 5);
    }

    // Uuid de un registro que se puede eliminar: por defecto se crea con guardar y el cuerpo valido
    protected String uuidEliminable() {
        return como(rol()).post(ruta() + "/guardar").json(cuerpoValido()).preparacion().ejecutar()
                .esperar(200).json().get("uuid").asString();
    }

    @Test
    void obtenerPorUuid() {
        publicada(OperacionAbm.OBTENER);
        como(rol()).get(ruta() + "/uuid/{uuid}", uuidVigente()).ejecutar().esperar(200);
    }

    @Test
    void obtenerUuidInexistente() {
        publicada(OperacionAbm.OBTENER);
        noEncontrado(como(rol()).get(ruta() + "/uuid/{uuid}", UUID_INEXISTENTE).ejecutar());
    }

    @Test
    void paginar() {
        publicada(OperacionAbm.PAGINAR);
        como(rol()).post(ruta() + "/paginar").json(paginado()).ejecutar().esperar(200);
    }

    @Test
    void guardarAlta() {
        publicada(OperacionAbm.GUARDAR);
        como(rol()).post(ruta() + "/guardar").json(cuerpoValido()).ejecutar().esperar(200);
    }

    @Test
    void guardarCuerpoInvalido() {
        publicada(OperacionAbm.GUARDAR);
        como(rol()).post(ruta() + "/guardar").json(cuerpoInvalido()).ejecutar()
                .esperarError(CodigoDeErrorKernel.FORMULARIO_INVALIDO.status().value(),
                        CodigoDeErrorKernel.FORMULARIO_INVALIDO.codigo());
    }

    // Las rutas del ABM anteriores a Core 3.3.0 (POST /paginate, POST /save y DELETE /{uuid}) ya no existen: responden 404
    @Test
    void rutasAnterioresNoExisten() {
        for (String rutaAnterior : List.of("/paginate", "/save")) {
            como(rol()).post(ruta() + rutaAnterior).json(Map.of()).ejecutar().esperar(404);
        }
        como(rol()).delete(ruta() + "/{uuid}", uuidVigente()).ejecutar().esperar(404);
    }

    @Test
    void eliminar() {
        publicada(OperacionAbm.ELIMINAR);
        String uuid = uuidEliminable();
        como(rol()).delete(ruta() + "/borrar/{uuid}", uuid).ejecutar().esperar(200);
    }

    @Test
    void eliminarUuidInexistente() {
        publicada(OperacionAbm.ELIMINAR);
        noEncontrado(como(rol()).delete(ruta() + "/borrar/{uuid}", UUID_INEXISTENTE).ejecutar());
    }

    @Test
    void eliminarDosVeces() {
        publicada(OperacionAbm.ELIMINAR);
        String uuid = uuidEliminable();
        como(rol()).delete(ruta() + "/borrar/{uuid}", uuid).preparacion().ejecutar().esperar(200);
        noEncontrado(como(rol()).delete(ruta() + "/borrar/{uuid}", uuid).ejecutar());
    }

    // Exige el error de registro inexistente o eliminado del Core (REGISTRO_NO_ENCONTRADO)
    private static void noEncontrado(Respuesta respuesta) {
        respuesta.esperarError(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO.status().value(),
                CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO.codigo());
    }

    // Saltea el test si el controller no publica la operacion (no hay metodo con ese verbo y ruta)
    private void publicada(OperacionAbm operacion) {
        String patron = ruta() + operacion.ruta;
        boolean publicada = false;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entrada.getKey();
            if (entrada.getValue().getBeanType() == controlador()
                    && info.getMethodsCondition().getMethods().contains(operacion.verbo)
                    && info.getPatternValues().contains(patron)) {
                publicada = true;
            }
        }
        assumeTrue(publicada, () -> controlador().getSimpleName() + " no publica " + operacion.verbo + " " + patron);
    }

    // Ruta base del controller (primer valor de su @RequestMapping), con "/" inicial
    private String ruta() {
        RequestMapping mapping = controlador().getAnnotation(RequestMapping.class);
        String[] valores = mapping.value().length > 0 ? mapping.value() : mapping.path();
        String ruta = valores[0];
        return ruta.startsWith("/") ? ruta : "/" + ruta;
    }
}
