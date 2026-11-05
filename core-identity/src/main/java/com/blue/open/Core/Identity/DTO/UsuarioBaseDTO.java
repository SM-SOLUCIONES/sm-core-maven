package com.blue.open.Core.Identity.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


/**
 * Campos comunes del usuario (entidad UsuarioBase). Lo extienden los DTOs de usuario del modulo
 * de scoping (monoempresa o multiempresa) y el del proyecto, que usa el ABM de usuarios.
 */
@Setter
@Getter
public class UsuarioBaseDTO extends EntidadBaseDTO {
    // Los roles del usuario no viajan en este DTO: los agrega el modulo de scoping
    // (BaseUserMonoempresaDTO o UsuarioBaseMultiempresaDTO), porque su forma depende de ese modulo.
    @Descripcion("Nombre de usuario con el que inicia sesión con usuario y contraseña: según el proyecto, un nombre "
            + "elegido o un identificador propio de la organización (por ejemplo, un legajo). Único entre todos los usuarios")
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String nombreDeUsuario;
    // La contrasena es opcional: si no se envia, se conserva la actual (ver UsuarioBaseService)
    @Descripcion("Contraseña en texto plano. Solo se envía al guardar: si no viene, se conserva la actual. "
            + "Nunca se responde (siempre null)")
    private String contrasena;
    @Descripcion("Email del usuario. Es al que se envían los códigos de verificación y de recuperación de contraseña "
            + "cuando el proyecto los usa. Si el usuario inicia sesión con un proveedor de identidad externo, ese proveedor "
            + "puede reemplazarlo en cada ingreso")
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    private String email;
    @Descripcion("Teléfono de contacto del usuario. Si inicia sesión con un proveedor de identidad externo, ese "
            + "proveedor puede reemplazarlo en cada ingreso")
    private String telefono;
}
