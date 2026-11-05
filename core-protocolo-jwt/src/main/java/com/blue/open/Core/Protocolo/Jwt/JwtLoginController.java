package com.blue.open.Core.Protocolo.Jwt;

import com.blue.open.Core.Identity.DTO.UsuarioYContrasenaDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Identity.Service.CookiesDeSesion;
import com.blue.open.Core.Kernel.Contrato.Contrato;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Protocolo.Jwt.Excepciones.CodigoDeErrorJwt;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de login por JWT. Viven en este modulo y no en el controller base de usuarios:
 * un proyecto que no declare core-protocolo-jwt no publica /iniciar-sesion ni /refresh-token.
 * Dependencias externas: la base de datos.
 * Recibe: usuario y contrasena, o un refresh token.
 * Responde: el par access token + refresh token, en el body y en las cookies access_token y refresh_token (HttpOnly).
 * La cookie refresh_token se limita al Path de este controller (context-path + ruta, o
 * core.protocolo.cookie.path-refresh-token si el backend se publica detras de un proxy con prefijo): solo viaja a
 * refresh-token y cerrar-sesion.
 * Si las cookies estan habilitadas, el refresh token tambien se lee de la cookie refresh_token.
 */
@RestController
@RequestMapping("${core.protocolo.jwt.ruta:usuario}")
@RequiredArgsConstructor
public class JwtLoginController {

    private final AuthService authService;

    private final AuthJwtService authJwtService;

    private final CookiesDeSesion cookiesDeSesion;

    @Value("${core.protocolo.jwt.ruta:usuario}")
    private String ruta;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    // Path de la cookie refresh_token tal como lo ve el navegador. Vacio = context-path + ruta. Se define cuando un
    // proxy agrega un prefijo (ej. nginx en /proyecto/api -> /proyecto/api/usuario)
    @Value("${core.protocolo.cookie.path-refresh-token:}")
    private String pathRefreshToken;

    // Inicia sesion con usuario y contrasena. Publico. Responde el JWT y su refresh token, y los setea como cookies
    @PostMapping("iniciar-sesion")
    @Contrato(casoDeUso = "CU-USR-10", resumen = "Inicia sesión con usuario y contraseña y responde un JWT y su refresh token (también como cookies HttpOnly access_token y refresh_token)",
            permiso = Contrato.PUBLICO)
    public ResponseEntity<JwtTokenDTO> iniciarSesion(@Valid @RequestBody UsuarioYContrasenaDTO cuerpo) {
        if (!authService.esProtocoloValido("jwt")) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.LOGIN_NO_DISPONIBLE);
        }
        UsuarioBase usuario = authJwtService.autenticarConContrasena(cuerpo);
        return conCookies(authJwtService.generarToken(usuario));
    }

    // Renueva el JWT con el refresh token (del body o, si no viene, de la cookie). Publico: el refresh token es la
    // credencial
    @PostMapping("refresh-token")
    @Contrato(casoDeUso = "CU-USR-11", resumen = "Renueva el JWT con el refresh token, del body o de la cookie refresh_token (el refresh token anterior deja de valer)",
            permiso = Contrato.PUBLICO)
    public ResponseEntity<JwtTokenDTO> renovarToken(@RequestBody(required = false) JwtTokenDTO request) {
        String refreshToken = refreshTokenRecibido(request);
        if (refreshToken == null) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.REFRESH_TOKEN_FALTANTE);
        }
        return conCookies(authJwtService.renovarToken(refreshToken));
    }

    // Cierra la sesion: borra el refresh token (del body o de la cookie) y las cookies. Publico e idempotente: sin
    // refresh token solo borra las cookies
    @PostMapping("cerrar-sesion")
    @Contrato(casoDeUso = "CU-USR-15", resumen = "Cierra la sesión: borra el refresh token y las cookies access_token y refresh_token",
            permiso = Contrato.PUBLICO)
    public ResponseEntity<Void> cerrarSesion(@RequestBody(required = false) JwtTokenDTO request) {
        String refreshToken = refreshTokenRecibido(request);
        if (refreshToken != null) {
            authJwtService.cerrarSesion(refreshToken);
        }
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(HttpStatus.NO_CONTENT);
        if (cookiesDeSesion.isHabilitada()) {
            respuesta.header(HttpHeaders.SET_COOKIE, cookiesDeSesion.borrar(CookiesDeSesion.ACCESS_TOKEN));
            respuesta.header(HttpHeaders.SET_COOKIE, cookiesDeSesion.borrar(CookiesDeSesion.REFRESH_TOKEN,
                    pathDelRefreshToken()));
            // La refresh_token que emitia Core 3.6.0 con Path=/: sin esto quedaria en el navegador despues de cerrar
            respuesta.header(HttpHeaders.SET_COOKIE, cookiesDeSesion.borrar(CookiesDeSesion.REFRESH_TOKEN));
        }
        return respuesta.build();
    }

    // El refresh token explicito del body gana; sin el, el de la cookie. Null si no hay ninguno
    private String refreshTokenRecibido(JwtTokenDTO request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isEmpty()) {
            return request.getRefreshToken();
        }
        return cookiesDeSesion.refreshToken().orElse(null);
    }

    // Responde el par de tokens en el body y, con las cookies habilitadas, tambien como Set-Cookie HttpOnly
    private ResponseEntity<JwtTokenDTO> conCookies(JwtTokenDTO tokens) {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        if (cookiesDeSesion.isHabilitada()) {
            respuesta.header(HttpHeaders.SET_COOKIE, cookiesDeSesion.crear(CookiesDeSesion.ACCESS_TOKEN,
                    tokens.getToken(), authJwtService.getVigenciaDelToken()));
            respuesta.header(HttpHeaders.SET_COOKIE, cookiesDeSesion.crear(CookiesDeSesion.REFRESH_TOKEN,
                    tokens.getRefreshToken(), authJwtService.getVigenciaDelRefreshToken(), pathDelRefreshToken()));
        }
        return respuesta.body(tokens);
    }

    // Path de la cookie refresh_token: core.protocolo.cookie.path-refresh-token si esta definido; si no, el context-path
    // mas la ruta de este controller (ej. /api/usuario). Sin barras repetidas ni barra final
    String pathDelRefreshToken() {
        String path = pathRefreshToken != null && !pathRefreshToken.isBlank() ? "/" + pathRefreshToken.trim()
                : "/" + (contextPath == null ? "" : contextPath.trim()) + "/" + ruta.trim();
        path = path.replaceAll("/+", "/");
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
