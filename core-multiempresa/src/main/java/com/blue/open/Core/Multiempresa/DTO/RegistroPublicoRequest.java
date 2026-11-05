package com.blue.open.Core.Multiempresa.DTO;

import com.blue.open.Core.Identity.DTO.UsuarioBaseDTO;
import com.blue.open.Core.Kernel.Contrato.Descripcion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request del registro publico que crea un usuario junto con su empresa (alta publica de UsuarioController).
 */
@Getter
@Setter
public class RegistroPublicoRequest<UDTO extends UsuarioBaseDTO, EDTO extends EmpresaBaseDTO> {
    @Descripcion("Usuario a registrar")
    @NotNull(message = "El usuario es obligatorio")
    @Valid
    private UDTO usuario;
    @Descripcion("Empresa a crear con el usuario")
    @NotNull(message = "La empresa es obligatoria")
    @Valid
    private EDTO empresa;
}
