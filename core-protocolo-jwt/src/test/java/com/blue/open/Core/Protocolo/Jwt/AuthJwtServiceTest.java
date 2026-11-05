package com.blue.open.Core.Protocolo.Jwt;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Identity.Service.CookiesDeSesion;
import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Protocolo.Jwt.Excepciones.CodigoDeErrorJwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica la convivencia de AuthJwtService con otros protocolos Bearer (Malvinas): un token cuyo alg no es HS256
 * responde null, y un HS256 invalido o vencido sigue lanzando su error. Tambien que renovarToken rechace a un
 * usuario que no esta en status 0.
 */
class AuthJwtServiceTest {

    private static final String SECRETO = "secretoDePruebaSuficientementeLargoParaHS256QueTieneMasDe32Bytes";

    private UsuarioRepositorio usuarioRepositorio;

    private JwtRefreshTokenRepository refreshTokenRepository;

    private AuthJwtService authJwtService;

    private UsuarioBase usuario;

    // Construye el servicio con sus mocks por constructor y el secreto (@Value) por reflexion
    @BeforeEach
    void preparar() {
        usuarioRepositorio = mock(UsuarioRepositorio.class);
        refreshTokenRepository = mock(JwtRefreshTokenRepository.class);
        authJwtService = new AuthJwtService(usuarioRepositorio, refreshTokenRepository, mock(PasswordEncoder.class),
                new CookiesDeSesion());
        ReflectionTestUtils.setField(authJwtService, "secretoJwt", SECRETO);

        usuario = new UsuarioBase() {
        };
        usuario.setId(7L);
        usuario.setUuid("usuario-uuid");
        usuario.setStatus(0);
    }

    // Limpia el request del hilo para no contaminar otros tests
    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getUsuario_tokenEs256_respondeNullSinConsultarLaBase() {
        enviarBearer(tokenEs256());

        assertNull(authJwtService.getUsuario());
        verifyNoInteractions(usuarioRepositorio);
    }

    @Test
    void getUsuario_tokenHs256Valido_respondeElUsuario() {
        when(usuarioRepositorio.porUuid("usuario-uuid")).thenReturn(Optional.of(usuario));
        enviarBearer(tokenHs256(SECRETO, 60000));

        assertSame(usuario, authJwtService.getUsuario());
    }

    @Test
    void getUsuario_tokenHs256ConOtraFirma_lanzaTokenInvalido() {
        enviarBearer(tokenHs256("otroSecretoDistintoTambienSuficientementeLargoParaHS256Algoritmo", 60000));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, authJwtService::getUsuario);
        assertEquals(CodigoDeErrorJwt.TOKEN_INVALIDO, ex.getCodigo());
    }

    @Test
    void getUsuario_tokenHs256Vencido_lanzaTokenVencido() {
        enviarBearer(tokenHs256(SECRETO, -60000));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, authJwtService::getUsuario);
        assertEquals(CodigoDeErrorJwt.TOKEN_VENCIDO, ex.getCodigo());
    }

    @Test
    void getUsuario_cabeceraIlegible_respondeNull() {
        enviarBearer("no-es-base64.payload.firma");

        assertNull(authJwtService.getUsuario());
    }

    @Test
    void getUsuarioDelToken_tokenEs256_respondeNull() {
        assertNull(authJwtService.getUsuarioDelToken(tokenEs256()));
        verifyNoInteractions(usuarioRepositorio);
    }

    @Test
    void getUsuarioDelToken_tokenHs256ConOtraFirma_lanzaTokenInvalido() {
        String token = tokenHs256("otroSecretoDistintoTambienSuficientementeLargoParaHS256Algoritmo", 60000);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> authJwtService.getUsuarioDelToken(token));
        assertEquals(CodigoDeErrorJwt.TOKEN_INVALIDO, ex.getCodigo());
    }

    @Test
    void renovarToken_usuarioDadoDeBaja_lanzaRefreshTokenInvalido() {
        usuario.setStatus(-1);
        when(refreshTokenRepository.findByToken("refresh")).thenReturn(Optional.of(refreshTokenVigente()));
        when(usuarioRepositorio.porId(7L)).thenReturn(Optional.of(usuario));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> authJwtService.renovarToken("refresh"));
        assertEquals(CodigoDeErrorJwt.REFRESH_TOKEN_INVALIDO, ex.getCodigo());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void renovarToken_usuarioActivo_emiteUnParNuevo() {
        when(refreshTokenRepository.findByToken("refresh")).thenReturn(Optional.of(refreshTokenVigente()));
        when(usuarioRepositorio.porId(7L)).thenReturn(Optional.of(usuario));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        JwtTokenDTO tokens = authJwtService.renovarToken("refresh");

        assertNotNull(tokens.getToken());
        assertNotNull(tokens.getRefreshToken());
    }

    @Test
    void getUsuario_sinHeaderConCookie_respondeElUsuarioDeLaCookie() {
        when(usuarioRepositorio.porUuid("usuario-uuid")).thenReturn(Optional.of(usuario));
        enviarCookie(tokenHs256(SECRETO, 60_000));

        assertSame(usuario, authJwtService.getUsuario());
    }

    @Test
    void getUsuario_headerYCookie_ganaElHeader() {
        when(usuarioRepositorio.porUuid("usuario-uuid")).thenReturn(Optional.of(usuario));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenHs256(SECRETO, 60_000));
        request.setCookies(new Cookie(CookiesDeSesion.ACCESS_TOKEN, "cookie-que-no-se-lee"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertSame(usuario, authJwtService.getUsuario());
    }

    @Test
    void getUsuario_headerBasicYCookie_respondeNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ==");
        request.setCookies(new Cookie(CookiesDeSesion.ACCESS_TOKEN, tokenHs256(SECRETO, 60_000)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertNull(authJwtService.getUsuario());
        verifyNoInteractions(usuarioRepositorio);
    }

    @Test
    void getUsuario_cookieVencida_lanzaTokenVencido() {
        enviarCookie(tokenHs256(SECRETO, -60_000));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> authJwtService.getUsuario());
        assertEquals(CodigoDeErrorJwt.TOKEN_VENCIDO, ex.getCodigo());
    }

    @Test
    void getUsuario_cookiesDeshabilitadas_respondeNull() {
        CookiesDeSesion cookies = new CookiesDeSesion();
        ReflectionTestUtils.setField(cookies, "habilitada", false);
        authJwtService = new AuthJwtService(usuarioRepositorio, refreshTokenRepository, mock(PasswordEncoder.class), cookies);
        ReflectionTestUtils.setField(authJwtService, "secretoJwt", SECRETO);
        enviarCookie(tokenHs256(SECRETO, 60_000));

        assertNull(authJwtService.getUsuario());
    }

    @Test
    void cerrarSesion_borraElRefreshToken() {
        JwtRefreshToken refreshToken = refreshTokenVigente();
        when(refreshTokenRepository.findByToken("refresh")).thenReturn(Optional.of(refreshToken));

        authJwtService.cerrarSesion("refresh");

        verify(refreshTokenRepository).delete(refreshToken);
    }

    @Test
    void cerrarSesion_refreshTokenInexistente_noHaceNada() {
        authJwtService.cerrarSesion("no-existe");

        verify(refreshTokenRepository, never()).delete(any(JwtRefreshToken.class));
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

    // Access token HS256 del usuario de prueba firmado con el secreto recibido, que vence en vencimientoMs
    private String tokenHs256(String secreto, long vencimientoMs) {
        Key clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("usuario-uuid")
                .claim("type", "access")
                .setExpiration(new Date(System.currentTimeMillis() + vencimientoMs))
                .signWith(clave, SignatureAlgorithm.HS256)
                .compact();
    }

    // Token ES256 como los que firma Malvinas, con un par de claves generado en el momento
    private String tokenEs256() {
        return Jwts.builder()
                .claim("urn:malvinas:legajo", "12345")
                .signWith(Keys.keyPairFor(SignatureAlgorithm.ES256).getPrivate(), SignatureAlgorithm.ES256)
                .compact();
    }

    // Refresh token del usuario de prueba que vence en una hora
    private JwtRefreshToken refreshTokenVigente() {
        JwtRefreshToken refreshToken = new JwtRefreshToken();
        refreshToken.setIdUsuario(7L);
        refreshToken.setToken("refresh");
        refreshToken.setFechaDeVencimiento(FechaHoraConfig.ahoraLocalDateTime().plusHours(1));
        return refreshToken;
    }
}
