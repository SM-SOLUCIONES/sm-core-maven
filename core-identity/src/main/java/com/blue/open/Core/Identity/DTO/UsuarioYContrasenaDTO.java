package com.blue.open.Core.Identity.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Credenciales del inicio de sesion con usuario y contrasena (POST de login de core-protocolo-jwt).
 */
@Setter
@Getter
public class UsuarioYContrasenaDTO {
    @Descripcion("Identificador con el que inicia sesión: el nombre de usuario de la cuenta, que según el proyecto "
            + "puede ser un nombre elegido o un identificador propio de la organización (por ejemplo, un legajo)")
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String nombreDeUsuario;
    @Descripcion("Contraseña en texto plano")
    @NotBlank(message = "La contrasena es obligatoria")
    private String contrasena;
}
