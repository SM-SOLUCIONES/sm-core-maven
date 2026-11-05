package com.blue.open.Core.Identity.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Request del cambio de contrasena con el codigo de recuperacion que se envio por email
 * (flujo publico de recuperar contrasena).
 */
@Getter
@Setter
public class CambiarContrasenaDTO {
    @Descripcion("Código de recuperación recibido por email (POST /usuario/publico/recuperar-contrasena). Vale 15 minutos y "
            + "una sola vez")
    @NotBlank(message = "El codigo es obligatorio")
    private String codigo;
    @Descripcion("Email del usuario activo que recupera la contraseña")
    @NotBlank(message = "El email es obligatorio")
    private String email;
    @Descripcion("Nueva contraseña en texto plano")
    @NotBlank(message = "La contrasena es obligatoria")
    private String contrasena;
}
