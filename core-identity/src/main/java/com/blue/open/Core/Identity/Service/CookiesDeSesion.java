package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Excepciones.CodigoDeErrorIdentity;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cookies de sesion: leer el access token y el refresh token desde una cookie HttpOnly, y armar las cookies que
 * emite el login. Las usan los protocolos jwt (lee y emite) y malvinas (solo lee).
 * Dependencias externas: ninguna. Lee el request actual del hilo (RequestContextHolder).
 * Recibe: la cookie access_token o refresh_token del request.
 * Responde: el valor de la cookie, o vacio si no aplica (cookies deshabilitadas, el request trae el header
 * Authorization, o no trae la cookie). Una cookie que llega en un metodo que modifica (POST, PUT, PATCH, DELETE)
 * desde un origen no permitido lanza ORIGEN_NO_PERMITIDO: es la proteccion contra CSRF.
 */
@Component
public class CookiesDeSesion {

    public static final String ACCESS_TOKEN = "access_token";

    public static final String REFRESH_TOKEN = "refresh_token";

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    // false apaga la lectura y la emision de cookies de todos los protocolos
    @Value("${core.protocolo.cookie.habilitada:true}")
    private boolean habilitada = true;

    // Atributo Secure de las cookies emitidas; false solo para desarrollo local sin HTTPS
    @Value("${core.protocolo.cookie.secure:true}")
    private boolean secure = true;

    @Value("${core.protocolo.cookie.same-site:Lax}")
    private String sameSite = "Lax";

    // Atributo Domain de las cookies emitidas; vacio = no se setea (la cookie queda para el host del backend)
    @Value("${core.protocolo.cookie.dominio:}")
    private String dominio = "";

    // Origenes (scheme://host[:puerto]) separados por coma desde los que se acepta una cookie en un metodo que
    // modifica, ademas del mismo host del request
    @Value("${core.protocolo.cookie.origenes-permitidos:}")
    private String origenesPermitidos = "";

    // Responde si las cookies de sesion estan habilitadas
    public boolean isHabilitada() {
        return habilitada;
    }

    // Responde la cookie access_token del request actual. Vacio si el request trae el header Authorization: el header
    // tiene prioridad sobre la cookie
    public Optional<String> accessToken() {
        HttpServletRequest request = requestActual();
        if (request == null || request.getHeader("Authorization") != null) {
            return Optional.empty();
        }
        return leer(request, ACCESS_TOKEN);
    }

    // Responde la cookie refresh_token del request actual, o vacio si no la trae
    public Optional<String> refreshToken() {
        HttpServletRequest request = requestActual();
        if (request == null) {
            return Optional.empty();
        }
        return leer(request, REFRESH_TOKEN);
    }

    // Arma el Set-Cookie de una cookie de sesion HttpOnly con el valor y la vigencia recibidos, con Path=/
    public String crear(String nombre, String valor, Duration vigencia) {
        return crear(nombre, valor, vigencia, "/");
    }

    // Arma el Set-Cookie de una cookie de sesion HttpOnly con el valor, la vigencia y el Path recibidos. El Path
    // limita a que rutas manda el navegador la cookie (ej. el refresh token solo a la ruta del login)
    public String crear(String nombre, String valor, Duration vigencia, String path) {
        return construir(nombre, valor, vigencia, path).toString();
    }

    // Arma el Set-Cookie que borra la cookie recibida (Max-Age=0), con Path=/
    public String borrar(String nombre) {
        return borrar(nombre, "/");
    }

    // Arma el Set-Cookie que borra la cookie recibida (Max-Age=0). El Path tiene que ser el mismo con el que se emitio
    // o el navegador no la borra
    public String borrar(String nombre, String path) {
        return construir(nombre, "", Duration.ZERO, path).toString();
    }

    private ResponseCookie construir(String nombre, String valor, Duration vigencia, String path) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(vigencia);
        if (dominio != null && !dominio.isBlank()) {
            cookie.domain(dominio.trim());
        }
        return cookie.build();
    }

    private Optional<String> leer(HttpServletRequest request, String nombre) {
        if (!habilitada || request.getCookies() == null) {
            return Optional.empty();
        }
        Optional<String> valor = Arrays.stream(request.getCookies())
                .filter(c -> nombre.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
        if (valor.isPresent()) {
            validarOrigen(request);
        }
        return valor;
    }

    // Un request que se autentica con cookie y modifica datos solo se acepta si su Origin (o Referer) es el mismo host
    // del request o uno de los origenes permitidos. Sin Origin ni Referer no es un navegador: se acepta
    private void validarOrigen(HttpServletRequest request) {
        if (METODOS_SEGUROS.contains(request.getMethod().toUpperCase())) {
            return;
        }
        String origen = request.getHeader("Origin");
        if (origen == null || origen.isBlank()) {
            origen = request.getHeader("Referer");
        }
        if (origen == null || origen.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(origen.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Origen sin esquema o host");
            }
            String puerto = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            String origenNormalizado = (uri.getScheme() + "://" + uri.getHost() + puerto).toLowerCase();
            if (listaDeOrigenes().contains(origenNormalizado)) {
                return;
            }
            String host = request.getHeader("Host");
            if (host != null && host.equalsIgnoreCase(uri.getHost() + puerto)) {
                return;
            }
        } catch (IllegalArgumentException e) {
            // Un origen ilegible no es de confianza: cae en el rechazo
        }
        throw new ExcepcionDeApi(CodigoDeErrorIdentity.ORIGEN_NO_PERMITIDO,
                "CookiesDeSesion | validarOrigen | Origen rechazado: " + origen);
    }

    private List<String> listaDeOrigenes() {
        if (origenesPermitidos == null || origenesPermitidos.isBlank()) {
            return List.of();
        }
        return Arrays.stream(origenesPermitidos.split(","))
                .map(o -> o.trim().toLowerCase())
                .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o)
                .filter(o -> !o.isEmpty())
                .toList();
    }

    private HttpServletRequest requestActual() {
        ServletRequestAttributes atributos = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return atributos == null ? null : atributos.getRequest();
    }
}
