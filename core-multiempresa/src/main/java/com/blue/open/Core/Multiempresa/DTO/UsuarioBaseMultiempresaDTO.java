package com.blue.open.Core.Multiempresa.DTO;

import com.blue.open.Core.Identity.DTO.UsuarioBaseDTO;
import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Proyeccion.Coleccion;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO de usuario en modo multi-empresa: agrega sobre UsuarioBaseDTO las empresas del usuario y los
 * roles que tiene en cada una. Un proyecto mono-empresa no usa este DTO y por lo tanto su API de
 * usuarios no expone empresas. La entidad de usuario debe extender UsuarioBaseMultiempresa (de ahi sale la @Coleccion).
 */
@Getter
@Setter
public class UsuarioBaseMultiempresaDTO extends UsuarioBaseDTO {
    @Descripcion("Empresas vigentes del usuario y los roles que tiene en cada una, por id ascendente")
    @Coleccion("rolesPorEmpresa")
    private List<UsuarioEmpresaDTO> rolesPorEmpresa;
}
