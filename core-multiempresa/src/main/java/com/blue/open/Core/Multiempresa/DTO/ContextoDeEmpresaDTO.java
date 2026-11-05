package com.blue.open.Core.Multiempresa.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.Getter;
import lombok.Setter;

/**
 * Respuesta del endpoint /contexto: le dice al frontend si el backend corre en modo multi-empresa
 * y cual es la empresa resuelta para la peticion actual. EDTO es el DTO de empresa del proyecto: la empresa activa
 * se responde con todos sus campos (por ejemplo el cuit de EmpresaDTO), no solo los de EmpresaBaseDTO.
 */
@Getter
@Setter
public class ContextoDeEmpresaDTO<EDTO extends EmpresaBaseDTO> {
    @Descripcion("true: multi-empresa (SaaS); false: mono-empresa (on-premise)")
    private boolean multiempresaHabilitada;
    @Descripcion("Uuid de la empresa por defecto cuando multiempresaHabilitada es false")
    private String uuidEmpresaPorDefecto;
    @Descripcion("true si el frontend debe enviar X-Empresa-Uuid en cada request autenticado")
    private boolean encabezadoDeEmpresaRequerido;
    @Descripcion("Valor crudo del header X-Empresa-Uuid de la request (null si no vino)")
    private String uuidEmpresaDelRequest;
    @Descripcion("Uuid de empresa resuelto para la request (con el default si multi-empresa está apagado)")
    private String uuidEmpresaResuelta;
    @Descripcion("Empresa activa de la sesión. null sin usuario autenticado o sin contexto de empresa")
    private EDTO empresaActual;
}
