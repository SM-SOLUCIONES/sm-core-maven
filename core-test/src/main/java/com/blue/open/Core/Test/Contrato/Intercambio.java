package com.blue.open.Core.Test.Contrato;

import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Un request y su respuesta reales, ejecutados por una prueba de contrato, con lo necesario para registrarlos en el
 * contrato de la operacion que los atendio.
 * handler / metodoHttp / ruta: la operacion (metodo del controller, verbo y patron de la ruta, ej. /x/{uuid}).
 * metadatos: los de @Contrato del metodo (null si no tiene).
 * claseDeTest / ejemplo: quien lo ejecuto y con que nombre se publica el ejemplo.
 * actor: rol con el que se ejecuto (null = sin autenticacion).
 * cuerpoRequest: body JSON enviado (null si no hubo); partes: partes de un multipart (nombre -> descripcion).
 * status / contentType / cuerpoRespuesta: la respuesta (cuerpoRespuesta null si no es JSON).
 * publicar: false para los pasos de preparacion del escenario (se validan pero no son ejemplo del contrato).
 */
public record Intercambio(
        HandlerMethod handler,
        String metodoHttp,
        String ruta,
        MetadatosDeOperacion metadatos,
        String claseDeTest,
        String ejemplo,
        String actor,
        JsonNode cuerpoRequest,
        Map<String, String> partes,
        int status,
        String contentType,
        JsonNode cuerpoRespuesta,
        boolean publicar) {
}
