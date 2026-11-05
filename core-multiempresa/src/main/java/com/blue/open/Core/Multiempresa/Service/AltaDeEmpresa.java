package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Multiempresa.DTO.EmpresaBaseDTO;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;

/**
 * Crea una empresa a partir de su DTO durante el alta publica de usuario + empresa.
 * Existe como puerto para que el Core no tenga que conocer el DTO concreto de empresa del proyecto.
 * Recibe: el DTO de empresa. Responde: la empresa persistida.
 */
public interface AltaDeEmpresa {
    EmpresaBase crear(EmpresaBaseDTO dto);
}
