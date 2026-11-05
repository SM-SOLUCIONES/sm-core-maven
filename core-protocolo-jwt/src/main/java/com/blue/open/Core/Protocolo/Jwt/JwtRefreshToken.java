package com.blue.open.Core.Protocolo.Jwt;

import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Refresh token del protocolo de autenticacion JWT (ver AuthJwtService).
 * Se vincula al usuario por id (id_usuario) y no por asociacion JPA: el Core no conoce la clase
 * concreta de usuario del proyecto. Un usuario puede tener varios refresh tokens (uno por sesion).
 */
@Setter
@Getter
@Table(name = "auth_jwt_refresh_tokens")
@Entity
public class JwtRefreshToken extends EntidadConBorradoLogico {

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    // Valor del refresh token (opaco para el cliente), usado para pedir un nuevo access token
    @Column(nullable = false, unique = true, length = 1024)
    private String token;

    // Fecha y hora en que el refresh token deja de valer
    @Column(nullable = false)
    private LocalDateTime fechaDeVencimiento;
}
