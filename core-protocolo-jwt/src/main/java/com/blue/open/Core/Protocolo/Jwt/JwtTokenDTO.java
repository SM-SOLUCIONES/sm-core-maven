package com.blue.open.Core.Protocolo.Jwt;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.Getter;
import lombok.Setter;

/**
 * Respuesta del inicio de sesion y de la renovacion del JWT (JwtLoginController): el JWT de acceso y
 * su refresh token. La renovacion tambien lo recibe como request, con el refreshToken.
 */
@Setter
@Getter
public class JwtTokenDTO {
    @Descripcion("JWT de acceso: va en el header Authorization: Bearer. También llega en la cookie HttpOnly access_token y, sin el header, el backend lo lee de esa cookie")
    private String token;
    @Descripcion("Refresh token para pedir un JWT nuevo en POST /usuario/refresh-token. Es opcional en ese request y en POST /usuario/cerrar-sesion: si no viene, el backend usa el de la cookie HttpOnly refresh_token")
    private String refreshToken;
}
