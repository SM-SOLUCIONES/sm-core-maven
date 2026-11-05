package com.blue.open.Core.Test.Contrato;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deja estables los ejemplos reales que se publican en el contrato, para que dos corridas sobre el mismo codigo
 * generen el mismo openapi.yaml (y su diff solo muestre cambios de contrato):
 * - traceId y fechaHora (de ErrorResponseDTO) por valores fijos.
 * - Los uuid generados en la corrida (los de los seeds tienen un grupo "0000" y se conservan) por uuids de ejemplo
 *   numerados en orden de aparicion, iguales en el request y en la respuesta del mismo intercambio.
 * - Las fechas del dia de la corrida (altas, auditoria) por una fecha fija; si son a medianoche (una vigencia que
 *   rige desde hoy) se conserva la hora.
 * - Los arrays, recortados a sus primeros 3 elementos, y las propiedades de cada objeto, ordenadas por nombre. Los
 *   "errores" de validacion (ErrorResponseDTO), ordenados: Bean Validation no garantiza su orden.
 * No altera el tipo de ningun valor: el ejemplo sigue cumpliendo el schema.
 */
final class NormalizadorDeEjemplos {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final int MAXIMO_ELEMENTOS = 3;

    private static final String FECHA_FIJA = "2026-01-01";

    private static final String FECHA_HORA_FIJA = "2026-01-01T12:00:00.000-03:00";

    // uuid real -> uuid de ejemplo, compartido entre el request y la respuesta de un intercambio
    private final Map<String, String> uuids = new HashMap<>();

    private final String hoy = LocalDate.now(ZONA).toString();

    // Copia normalizada del JSON recibido (null si es null)
    JsonNode normalizar(JsonNode json) {
        return json == null ? null : copiar(json.deepCopy(), null);
    }

    // Normaliza el nodo (ya copiado) recursivamente; clave es el nombre de la propiedad que lo contiene
    private JsonNode copiar(JsonNode nodo, String clave) {
        if (nodo instanceof ObjectNode objeto) {
            ObjectNode ordenado = JsonNodeFactory.instance.objectNode();
            for (String nombre : objeto.propertyNames().stream().sorted().toList()) {
                ordenado.set(nombre, copiar(objeto.get(nombre), nombre));
            }
            return ordenado;
        }
        if (nodo instanceof ArrayNode array) {
            if ("errores".equals(clave)) {
                // Los errores de Bean Validation llegan en un orden que cambia entre corridas y no tiene significado
                List<JsonNode> ordenados = new ArrayList<>();
                array.forEach(ordenados::add);
                ordenados.sort(Comparator.comparing(JsonNode::toString));
                array.removeAll();
                ordenados.forEach(array::add);
            }
            while (array.size() > MAXIMO_ELEMENTOS) {
                array.remove(array.size() - 1);
            }
            for (int i = 0; i < array.size(); i++) {
                array.set(i, copiar(array.get(i), clave));
            }
            return array;
        }
        if (nodo.isString()) {
            return StringNode.valueOf(texto(nodo.asString(), clave));
        }
        return nodo;
    }

    // Valor estable de un string segun su clave y su forma (uuid generado, fecha de hoy, traza)
    private String texto(String valor, String clave) {
        if ("traceId".equals(clave)) {
            return "0000000000000000";
        }
        if ("fechaHora".equals(clave)) {
            return FECHA_HORA_FIJA;
        }
        if (UUID.matcher(valor).matches() && !valor.contains("-0000-")) {
            return uuids.computeIfAbsent(valor, v -> String.format("aaaaaaaa-aaaa-4aaa-8aaa-%012d", uuids.size() + 1));
        }
        if (valor.length() >= 10 && valor.startsWith(hoy)) {
            if (valor.length() == 10) {
                return FECHA_FIJA;
            }
            // Una vigencia "desde hoy" es a medianoche: se conserva para no contradecir al endpoint
            return valor.startsWith(hoy + "T00:00:00.000") ? FECHA_FIJA + valor.substring(10) : FECHA_HORA_FIJA;
        }
        return valor;
    }
}
