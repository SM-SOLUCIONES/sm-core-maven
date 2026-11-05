package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Excepciones.CodigoDeErrorIdentity;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica CookiesDeSesion: la lectura de access_token y refresh_token (prioridad del header Authorization, apagado
 * por propiedad), la validacion de origen en los metodos que modifican y los atributos de las cookies emitidas.
 */
class CookiesDeSesionTest {

    private final CookiesDeSesion cookies = new CookiesDeSesion();

    // Limpia el request del hilo para no contaminar otros tests
    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void accessToken_sinHeader_leeLaCookie() {
        enviar(request("GET", null, null, new Cookie(CookiesDeSesion.ACCESS_TOKEN, "jwt")));

        assertEquals(Optional.of("jwt"), cookies.accessToken());
    }

    @Test
    void accessToken_conHeaderAuthorization_ignoraLaCookie() {
        MockHttpServletRequest request = request("GET", null, null, new Cookie(CookiesDeSesion.ACCESS_TOKEN, "jwt"));
        request.addHeader("Authorization", "Bearer otro");
        enviar(request);

        assertEquals(Optional.empty(), cookies.accessToken());
    }

    @Test
    void accessToken_cookiesDeshabilitadas_respondeVacio() {
        ReflectionTestUtils.setField(cookies, "habilitada", false);
        enviar(request("GET", null, null, new Cookie(CookiesDeSesion.ACCESS_TOKEN, "jwt")));

        assertEquals(Optional.empty(), cookies.accessToken());
    }

    @Test
    void accessToken_cookieVacia_respondeVacio() {
        enviar(request("GET", null, null, new Cookie(CookiesDeSesion.ACCESS_TOKEN, "")));

        assertEquals(Optional.empty(), cookies.accessToken());
    }

    @Test
    void accessToken_sinRequestEnElHilo_respondeVacio() {
        assertEquals(Optional.empty(), cookies.accessToken());
    }

    @Test
    void refreshToken_leeLaCookie() {
        enviar(request("POST", null, null, new Cookie(CookiesDeSesion.REFRESH_TOKEN, "refresh")));

        assertEquals(Optional.of("refresh"), cookies.refreshToken());
    }

    @Test
    void metodoQueModifica_origenDeOtroHost_lanzaOrigenNoPermitido() {
        enviar(request("POST", "https://sitio-malicioso.com", "api.ejemplo.com",
                new Cookie(CookiesDeSesion.REFRESH_TOKEN, "refresh")));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> cookies.refreshToken());
        assertEquals(CodigoDeErrorIdentity.ORIGEN_NO_PERMITIDO, ex.getCodigo());
    }

    @Test
    void metodoQueModifica_origenLiteralNull_lanzaOrigenNoPermitido() {
        enviar(request("DELETE", "null", "api.ejemplo.com", new Cookie(CookiesDeSesion.REFRESH_TOKEN, "refresh")));

        assertThrows(ExcepcionDeApi.class, () -> cookies.refreshToken());
    }

    @Test
    void metodoQueModifica_mismoHost_acepta() {
        enviar(request("POST", "https://api.ejemplo.com", "api.ejemplo.com",
                new Cookie(CookiesDeSesion.REFRESH_TOKEN, "refresh")));

        assertEquals(Optional.of("refresh"), cookies.refreshToken());
    }

    @Test
    void metodoQueModifica_origenEnLaLista_acepta() {
        ReflectionTestUtils.setField(cookies, "origenesPermitidos", "https://app.ejemplo.com/, http://localhost:3000");
        enviar(request("POST", "https://app.ejemplo.com", "api.ejemplo.com",
                new Cookie(CookiesDeSesion.REFRESH_TOKEN, "refresh")));

        assertEquals(Optional.of("refresh"), cookies.refreshToken());
    }

    @Test
    void metodoQueModifica_sinOriginNiReferer_acepta() {
        enviar(request("POST", null, "api.ejemplo.com", new Cookie(CookiesDeSesion.REFRESH_TOKEN, "refresh")));

        assertEquals(Optional.of("refresh"), cookies.refreshToken());
    }

    @Test
    void metodoQueModifica_sinCookie_noValidaElOrigen() {
        enviar(request("POST", "https://sitio-malicioso.com", "api.ejemplo.com"));

        assertEquals(Optional.empty(), cookies.refreshToken());
    }

    @Test
    void metodoSeguro_origenDeOtroHost_acepta() {
        enviar(request("GET", "https://sitio-malicioso.com", "api.ejemplo.com",
                new Cookie(CookiesDeSesion.ACCESS_TOKEN, "jwt")));

        assertEquals(Optional.of("jwt"), cookies.accessToken());
    }

    @Test
    void crear_armaUnaCookieHttpOnlySecureYSameSite() {
        String cookie = cookies.crear(CookiesDeSesion.ACCESS_TOKEN, "jwt", Duration.ofMinutes(5));

        assertTrue(cookie.startsWith("access_token=jwt"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=300"));
    }

    @Test
    void crear_configurable() {
        ReflectionTestUtils.setField(cookies, "secure", false);
        ReflectionTestUtils.setField(cookies, "sameSite", "Strict");
        ReflectionTestUtils.setField(cookies, "dominio", "ejemplo.com");

        String cookie = cookies.crear(CookiesDeSesion.REFRESH_TOKEN, "r", Duration.ofDays(2));

        assertTrue(!cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Domain=ejemplo.com"));
    }

    @Test
    void borrar_armaUnaCookieConMaxAgeCero() {
        String cookie = cookies.borrar(CookiesDeSesion.REFRESH_TOKEN);

        assertTrue(cookie.startsWith("refresh_token=;"));
        assertTrue(cookie.contains("Max-Age=0"));
    }

    @Test
    void crear_conPath_limitaLaCookieAEsaRuta() {
        String cookie = cookies.crear(CookiesDeSesion.REFRESH_TOKEN, "r", Duration.ofDays(2), "/api/usuario");

        assertTrue(cookie.contains("Path=/api/usuario"));
    }

    @Test
    void borrar_conPath_usaElMismoPath() {
        String cookie = cookies.borrar(CookiesDeSesion.REFRESH_TOKEN, "/api/usuario");

        assertTrue(cookie.startsWith("refresh_token=;"));
        assertTrue(cookie.contains("Path=/api/usuario"));
        assertTrue(cookie.contains("Max-Age=0"));
    }

    // Request con metodo, Origin y Host (null = no se manda) y las cookies recibidas
    private MockHttpServletRequest request(String metodo, String origen, String host, Cookie... cookiesDelRequest) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/usuario/refresh-token");
        if (origen != null) {
            request.addHeader("Origin", origen);
        }
        if (host != null) {
            request.addHeader("Host", host);
        }
        if (cookiesDelRequest.length > 0) {
            request.setCookies(cookiesDelRequest);
        }
        return request;
    }

    private void enviar(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
