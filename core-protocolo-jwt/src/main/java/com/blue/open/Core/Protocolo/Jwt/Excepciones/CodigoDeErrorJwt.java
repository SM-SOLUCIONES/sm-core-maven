package com.blue.open.Core.Protocolo.Jwt.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-protocolo-jwt: login, access token y refresh token.
@AllArgsConstructor
public enum CodigoDeErrorJwt implements CodigoDeError {

    @Descripcion("Login con usuario y contraseña cuando el backend no tiene habilitado el protocolo JWT")
    LOGIN_NO_DISPONIBLE(HttpStatus.BAD_REQUEST, "No está disponible este metodo de login"),
    @Descripcion("El body de la renovación del token no trae el refresh token")
    REFRESH_TOKEN_FALTANTE(HttpStatus.BAD_REQUEST, "Refresh token is missing"),
    @Descripcion("El refresh token enviado no existe, o su usuario no existe o está dado de baja: hay que volver a iniciar sesión")
    REFRESH_TOKEN_INVALIDO(HttpStatus.UNAUTHORIZED, "Refresh token inválido o no encontrado"),
    @Descripcion("El refresh token enviado venció: hay que volver a iniciar sesión")
    REFRESH_TOKEN_VENCIDO(HttpStatus.UNAUTHORIZED, "El refresh token ha expirado. Por favor inicie sesión nuevamente"),
    @Descripcion("El access token del header `Authorization` venció: hay que renovarlo con el refresh token")
    TOKEN_VENCIDO(HttpStatus.UNAUTHORIZED, "El token de acceso ha expirado. Por favor inicie sesión nuevamente"),
    @Descripcion("El access token HS256 del header `Authorization` no es válido (firma, tipo o usuario asociado). Un token con otro `alg` no es de este protocolo y no recibe este código")
    TOKEN_INVALIDO(HttpStatus.UNAUTHORIZED, "Token de acceso inválido"),
    @Descripcion("Al conectarse por WebSocket (frame STOMP `CONNECT`) sin el header `Authorization: Bearer {token}` o con el token vacío. Llega como frame `ERROR` de STOMP: hay que iniciar sesión y reconectar con el access token. Un endpoint HTTP sin token responde `NO_AUTENTICADO`")
    TOKEN_REQUERIDO(HttpStatus.UNAUTHORIZED, "Token de acceso requerido");

    private final HttpStatus status;
    private final String mensajePorDefecto;

    @Override
    public String codigo() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String mensajePorDefecto() {
        return mensajePorDefecto;
    }
}
