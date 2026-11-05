package com.blue.open.Core.Protocolo.Sso;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.PoliticaDeAccesoExterno;
import com.blue.open.Core.Identity.Puertos.SincronizadorDeRolesExternos;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Identity.Service.CookiesDeSesion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifica la convivencia de AuthMalvinasService con el JWT propio (un Bearer que no es ES256 responde null sin
 * decodificarse) y que consulte PoliticaDeAccesoExterno antes de crear o guardar el usuario, propagando su error
 * sin envolverlo.
 */
class AuthMalvinasServiceTest {

    private static final String LEGAJO = "12345";

    private UsuarioRepositorio usuarioRepositorio;

    private SincronizadorDeRolesExternos sincronizadorDeRolesExternos;

    private PoliticaDeAccesoExterno politicaDeAccesoExterno;

    private JwtDecoder decoder;

    private AuthMalvinasService authMalvinasService;

    // Construye el servicio con sus mocks por constructor y auth.malvinas.url (@Value) por reflexion
    @BeforeEach
    void preparar() {
        usuarioRepositorio = mock(UsuarioRepositorio.class);
        sincronizadorDeRolesExternos = mock(SincronizadorDeRolesExternos.class);
        politicaDeAccesoExterno = mock(PoliticaDeAccesoExterno.class);
        decoder = mock(JwtDecoder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> proveedorDeDecoder = mock(ObjectProvider.class);
        when(proveedorDeDecoder.getIfAvailable()).thenReturn(decoder);

        authMalvinasService = new AuthMalvinasService(usuarioRepositorio, sincronizadorDeRolesExternos,
                politicaDeAccesoExterno, proveedorDeDecoder, new CookiesDeSesion());
        ReflectionTestUtils.setField(authMalvinasService, "urlDeMalvinas", "https://idp.test/.well-known/jwks");
    }

    // Limpia el request del hilo para no contaminar otros tests
    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getUsuario_tokenHs256_respondeNullSinDecodificar() {
        enviarBearer(tokenConAlg("HS256"));

        assertNull(authMalvinasService.getUsuario());
        verifyNoInteractions(decoder, usuarioRepositorio, politicaDeAccesoExterno);
    }

    @Test
    void getUsuario_cabeceraIlegible_respondeNull() {
        enviarBearer("no-es-base64.payload.firma");

        assertNull(authMalvinasService.getUsuario());
        verifyNoInteractions(decoder);
    }

    @Test
    void getUsuario_legajoNuevo_consultaLaPoliticaConNullYLoCrea() {
        String token = tokenConAlg("ES256");
        enviarBearer(token);
        when(decoder.decode(token)).thenReturn(jwtDeMalvinas(token));
        UsuarioBase nuevo = usuarioVacio();
        when(usuarioRepositorio.nuevo()).thenReturn(nuevo);
        when(usuarioRepositorio.guardar(any())).thenAnswer(i -> i.getArgument(0));

        UsuarioBase usuario = authMalvinasService.getUsuario();

        assertSame(nuevo, usuario);
        assertEquals(LEGAJO, usuario.getMalvinasLegajo());
        verify(politicaDeAccesoExterno).validar("malvinas", LEGAJO, null);
    }

    @Test
    void getUsuario_legajoExistente_consultaLaPoliticaConElUsuarioVigente() {
        String token = tokenConAlg("ES256");
        enviarBearer(token);
        when(decoder.decode(token)).thenReturn(jwtDeMalvinas(token));
        UsuarioBase vigente = usuarioVacio();
        vigente.setMalvinasLegajo(LEGAJO);
        when(usuarioRepositorio.porMalvinasLegajoActivo(LEGAJO)).thenReturn(Optional.of(vigente));
        when(usuarioRepositorio.guardar(any())).thenAnswer(i -> i.getArgument(0));

        assertSame(vigente, authMalvinasService.getUsuario());
        verify(politicaDeAccesoExterno).validar("malvinas", LEGAJO, vigente);
    }

    @Test
    void getUsuario_politicaRechaza_propagaSuErrorSinCrearNiGuardar() {
        String token = tokenConAlg("ES256");
        enviarBearer(token);
        when(decoder.decode(token)).thenReturn(jwtDeMalvinas(token));
        ExcepcionDeApi rechazo = new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO);
        doThrow(rechazo).when(politicaDeAccesoExterno).validar(eq("malvinas"), eq(LEGAJO), isNull());

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, authMalvinasService::getUsuario);

        assertSame(rechazo, ex);
        verify(usuarioRepositorio, never()).nuevo();
        verify(usuarioRepositorio, never()).guardar(any());
        verifyNoInteractions(sincronizadorDeRolesExternos);
    }

    @Test
    void getUsuario_sinHeaderConCookieEs256_resuelveElUsuarioDeLaCookie() {
        String token = tokenConAlg("ES256");
        enviarCookie(token);
        when(decoder.decode(token)).thenReturn(jwtDeMalvinas(token));
        UsuarioBase nuevo = usuarioVacio();
        when(usuarioRepositorio.nuevo()).thenReturn(nuevo);
        when(usuarioRepositorio.guardar(any())).thenAnswer(i -> i.getArgument(0));

        assertSame(nuevo, authMalvinasService.getUsuario());
        assertEquals(LEGAJO, nuevo.getMalvinasLegajo());
    }

    @Test
    void getUsuario_cookieHs256_respondeNullSinDecodificar() {
        enviarCookie(tokenConAlg("HS256"));

        assertNull(authMalvinasService.getUsuario());
        verifyNoInteractions(decoder, usuarioRepositorio, politicaDeAccesoExterno);
    }

    @Test
    void getUsuario_headerNoBearerYCookie_respondeNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ==");
        request.setCookies(new Cookie(CookiesDeSesion.ACCESS_TOKEN, tokenConAlg("ES256")));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertNull(authMalvinasService.getUsuario());
        verifyNoInteractions(decoder);
    }

    @Test
    void getUsuario_sinHeaderNiCookie_respondeNull() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertNull(authMalvinasService.getUsuario());
    }

    // Deja en el hilo un request con el header Authorization Bearer
    private void enviarBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // Deja en el hilo un request sin header Authorization y con la cookie access_token
    private void enviarCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookiesDeSesion.ACCESS_TOKEN, token));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // Token de tres segmentos con la cabecera del alg recibido. La firma no importa: el decoder es un mock
    private String tokenConAlg(String alg) {
        Base64.Encoder codificador = Base64.getUrlEncoder().withoutPadding();
        String cabecera = codificador.encodeToString(("{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}")
                .getBytes(StandardCharsets.UTF_8));
        String cuerpo = codificador.encodeToString(("{\"urn:malvinas:legajo\":\"" + LEGAJO + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return cabecera + "." + cuerpo + ".firma";
    }

    // Jwt decodificado como lo devolveria el JwtDecoder de Malvinas, con el legajo y un rol
    private Jwt jwtDeMalvinas(String token) {
        return Jwt.withTokenValue(token)
                .header("alg", "ES256")
                .claim("urn:malvinas:legajo", LEGAJO)
                .claim("name", "Usuario de Malvinas")
                .claim("role", List.of("INSPECTOR"))
                .build();
    }

    // Usuario sin datos, como el que crea UsuarioRepositorio.nuevo()
    private UsuarioBase usuarioVacio() {
        return new UsuarioBase() {
        };
    }
}
