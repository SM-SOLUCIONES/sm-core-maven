package com.blue.open.Core.Kernel.Trazabilidad;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sanitiza datos antes de escribirlos en logs para evitar exposicion de secretos,
 * credenciales y contenido binario o de gran tamano.
 */
@Component
public class SanitizadorDeLogs {

    private static final int LARGO_MAXIMO_EN_LOG = 10_000;
    private static final String TAPADO = "[REDACTED]";
    private static final String OMITIDO = "[payload omitido]";

    // Raices normalizadas (minusculas, sin "-" ni "_"): se tapa toda clave que CONTENGA alguna (contrasenaActual,
    // contrasenaNueva, refreshToken, clientSecret, setCookie, imagenBase64). Puede tapar booleanos como
    // tieneContrasena: es preferible a dejar pasar un secreto
    private static final Set<String> RAICES_SENSIBLES = Set.of(
            "authorization",
            "cookie",
            "password",
            "contrasena",
            "token",
            "secret",
            "codigodeverificacion",
            "apikey",
            "captcha",
            "base64"
    );

    // Excepcion de la raiz "secret": secretario/secretaria es un rol del negocio, no un secreto
    private static final String SECRETARIO = "secretari";

    // Claves normalizadas que se tapan solo si coinciden exactas: como raiz taparian listas y nombres de archivo
    // (archivos, nombreDelArchivo). "contenido" no esta: es la lista de registros de Pagina<T>; el contenido de un
    // archivo se nombra contenidoDelArchivo
    private static final Set<String> CLAVES_SENSIBLES = Set.of(
            "archivo",
            "contenidodelarchivo",
            "contenidoarchivo"
    );

    private final ObjectMapper objectMapper;

    // Recibe el ObjectMapper de la aplicacion, con el que lee y reescribe los JSON a sanitizar
    public SanitizadorDeLogs(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Sanitiza un payload JSON. Los payloads no JSON o demasiado grandes no se
     * escriben en el log. No depende de servicios externos y responde un texto seguro.
     */
    public String sanitizarCuerpo(String cuerpo, String tipoDeContenido) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return "";
        }

        if (cuerpo.length() > LARGO_MAXIMO_EN_LOG || !esContenidoJson(tipoDeContenido)) {
            return OMITIDO;
        }

        try {
            JsonNode sanitizado = sanitizarNodo(objectMapper.readTree(cuerpo), Set.of());
            String resultado = objectMapper.writeValueAsString(sanitizado);
            return resultado.length() <= LARGO_MAXIMO_EN_LOG ? resultado : OMITIDO;
        } catch (Exception excepcion) {
            return "[payload JSON invalido omitido]";
        }
    }

    /**
     * Sanitiza headers seleccionados sin registrar credenciales ni cookies.
     */
    public String sanitizarEncabezado(String nombreDelEncabezado, String valorDelEncabezado) {
        if (nombreDelEncabezado == null || valorDelEncabezado == null) {
            return "";
        }

        String nombreNormalizado = normalizarClave(nombreDelEncabezado);
        return esClaveNormalizadaSensible(nombreNormalizado) ? TAPADO : valorDelEncabezado;
    }

    /**
     * Convierte y sanitiza un argumento o resultado de una operacion trazada.
     * Los campos adicionales se comparan sin distinguir mayusculas, guiones ni guiones bajos.
     */
    public String sanitizarValor(Object valor, String[] clavesSensiblesAdicionales) {
        if (valor == null) {
            return "null";
        }
        if (valor instanceof byte[]) {
            return "[contenido binario omitido]";
        }

        try {
            Set<String> clavesAdicionales = new HashSet<>();
            if (clavesSensiblesAdicionales != null) {
                for (String clave : clavesSensiblesAdicionales) {
                    if (clave != null && !clave.isBlank()) {
                        clavesAdicionales.add(normalizarClave(clave));
                    }
                }
            }
            JsonNode sanitizado = sanitizarNodo(objectMapper.valueToTree(valor), clavesAdicionales);
            String resultado = objectMapper.writeValueAsString(sanitizado);
            return resultado.length() <= LARGO_MAXIMO_EN_LOG ? resultado : OMITIDO;
        } catch (Exception excepcion) {
            return "[valor no serializable omitido]";
        }
    }

    private JsonNode sanitizarNodo(JsonNode nodo, Set<String> clavesSensiblesAdicionales) {
        if (nodo == null || nodo.isNull() || nodo.isValueNode()) {
            return nodo;
        }

        if (nodo.isArray()) {
            ArrayNode listaSanitizada = JsonNodeFactory.instance.arrayNode();
            for (JsonNode hijo : nodo) {
                listaSanitizada.add(sanitizarNodo(hijo, clavesSensiblesAdicionales));
            }
            return listaSanitizada;
        }

        ObjectNode objetoSanitizado = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> campo : nodo.properties()) {
            String claveNormalizada = normalizarClave(campo.getKey());
            if (esClaveNormalizadaSensible(claveNormalizada) || clavesSensiblesAdicionales.contains(claveNormalizada)) {
                objetoSanitizado.put(campo.getKey(), TAPADO);
            } else {
                objetoSanitizado.set(campo.getKey(), sanitizarNodo(campo.getValue(), clavesSensiblesAdicionales));
            }
        }
        return objetoSanitizado;
    }

    private boolean esContenidoJson(String tipoDeContenido) {
        return tipoDeContenido == null
                || tipoDeContenido.toLowerCase(Locale.ROOT).contains("json");
    }

    // Indica si una clave ya normalizada es sensible: coincide exacta con CLAVES_SENSIBLES o contiene alguna de
    // RAICES_SENSIBLES (la raiz "secret" no cuenta dentro de "secretari"). No depende de servicios externos.
    private static boolean esClaveNormalizadaSensible(String claveNormalizada) {
        if (CLAVES_SENSIBLES.contains(claveNormalizada)) {
            return true;
        }
        // Se quita "secretari" antes de buscar las raices, para que secretarioUuid no matchee "secret"
        String claveSinSecretario = claveNormalizada.replace(SECRETARIO, "");
        for (String raiz : RAICES_SENSIBLES) {
            if (claveSinSecretario.contains(raiz)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizarClave(String clave) {
        return clave.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /**
     * Indica si una clave (sin normalizar) es sensible, segun el mismo criterio que sanitiza
     * los logs. Publico y estatico para que ExcepcionDeApi.conDato la reutilice sin duplicar
     * la lista de claves.
     */
    public static boolean esClaveSensible(String clave) {
        return clave != null && esClaveNormalizadaSensible(normalizarClave(clave));
    }
}
