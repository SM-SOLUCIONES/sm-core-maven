package com.blue.open.Core.Protocolo.Sso;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Usuario tal como lo responde el SSO. Lo usa AuthSsoService para crear o actualizar el usuario local;
 * no viaja en ningun endpoint del proyecto.
 */
@Getter
@Setter
public class UsuarioSSO {
    private String uuid;
    private String username;
    private String email;
    private String nombre;
    private String telefono;
    private Boolean cuentaVerificada;
    private Boolean cuentaBloqueada;
    private List<String> roles;
}
