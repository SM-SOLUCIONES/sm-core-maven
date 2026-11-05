package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Crud.AbstractService;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.DTO.EmpresaBaseDTO;
import com.blue.open.Core.Multiempresa.DTO.ContextoDeEmpresaDTO;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service base de empresas. Ademas del CRUD heredado expone el contexto multi-empresa que consume
 * el frontend para saber si debe mandar el header X-Empresa-Uuid.
 * Dependencias externas: la base de datos y el contexto HTTP.
 * Recibe: el DTO de empresa del proyecto. Responde: ese DTO, o el ContextoDeEmpresaDTO.
 */
@Transactional
public abstract class EmpresaBaseService<E extends EmpresaBase, DTO extends EmpresaBaseDTO>
        extends AbstractService<E, DTO, DTO> {

    protected ContextoDeEmpresa contextoDeEmpresa;

    protected AuthService authService;

    /*
     * Inyeccion por setter, no por campo ni por constructor: es una clase base que extienden
     * otros modulos del Core y los proyectos. Con constructor, toda subclase estaria obligada a
     * declarar uno y encadenar super(...). Ver core/docs/rules/inyeccion-de-dependencias.md.
     */

    // Recibe ContextoDeEmpresa
    @Autowired
    public void setContextoDeEmpresa(ContextoDeEmpresa contextoDeEmpresa) {
        this.contextoDeEmpresa = contextoDeEmpresa;
    }

    // Recibe AuthService
    @Autowired
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    // Arma el contexto de empresa de la request (multiempresa habilitada o no, uuid de la empresa por defecto,
    // del header y resuelto) y, si hay sesion y una empresa valida, agrega la Proyeccion de la empresa actual.
    // Dependencias externas: la base de datos. Recibe: nada. Responde: ContextoDeEmpresaDTO.
    @Transactional(readOnly = true)
    public ContextoDeEmpresaDTO<DTO> obtenerContexto() {
        ContextoDeEmpresaDTO<DTO> contexto = new ContextoDeEmpresaDTO<>();
        contexto.setMultiempresaHabilitada(contextoDeEmpresa.isMultiempresaHabilitada());
        contexto.setUuidEmpresaPorDefecto(contextoDeEmpresa.getUuidEmpresaPorDefecto());
        contexto.setEncabezadoDeEmpresaRequerido(contextoDeEmpresa.isMultiempresaHabilitada());

        String uuidEmpresaDelRequest = contextoDeEmpresa.getUuidCrudoDeLaRequest();
        contexto.setUuidEmpresaDelRequest(uuidEmpresaDelRequest);
        contexto.setUuidEmpresaResuelta(contextoDeEmpresa.resolverUuidDeEmpresa(uuidEmpresaDelRequest));

        UsuarioBase user = authService.getActualONull();
        if (user != null) {
            try {
                EmpresaBase empresaActual = contextoDeEmpresa.getEmpresaActualObligatoria();
                contexto.setEmpresaActual(this.proyectar(empresaActual.getUuid()));
            } catch (ExcepcionDeApi ignored) {
                // Sin header en modo multi-empresa: se devuelve solo la configuracion
            }
        }

        return contexto;
    }
}
