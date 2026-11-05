package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Service.RolBaseService;
import com.blue.open.Core.Kernel.DTO.FiltroDePaginado;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Excepciones.CodigoDeErrorMultiempresa;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service de roles en modo multi-empresa: agrega el scoping por empresa sobre RolBaseService.
 * Un rol pertenece a una empresa; los de la empresa de sistema solo los gestiona un sysadmin.
 * Dependencias externas: la base de datos y el contexto HTTP.
 * Generico sobre el DTO igual que RolBaseService (desde Core 3.5.0): D es RolDTO o un DTO del proyecto que lo
 * extiende. Un proyecto que extendia sin parametro migra declarando RolBaseMultiempresaService<RolDTO>.
 * Recibe: D o uuid segun la operacion. Responde: el rol resultante como D.
 */
@Transactional
public class RolBaseMultiempresaService<D extends RolDTO> extends RolBaseService<D> {

    protected ContextoDeEmpresa contextoDeEmpresa;

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

    @Override
    protected PedidoDePaginado hookPaginarAntesDeConsultar(PedidoDePaginado pedido) {
        UsuarioBase usuarioActual = authService.getActualObligatorio();

        if (!authService.esSysAdmin(usuarioActual)) {
            pedido.agregarFiltro(List.of(
                    new FiltroDePaginado("idEmpresa", "=",
                            contextoDeEmpresa.getEmpresaActualObligatoria().getId().toString()),
                    new FiltroDePaginado("idEmpresa", "=",
                            String.valueOf(contextoDeEmpresa.getIdEmpresaDeSistema()))));
        }
        return pedido;
    }

    @Override
    public void hookGuardarAuth(D request) {
        EmpresaBase empresaActual = contextoDeEmpresa.getEmpresaActualObligatoria();

        // Un rol con el mismo nombre en la empresa solo es valido si es el mismo registro que se edita
        boolean nombreEnUso = rolRepository.findFirstByNombreAndIdEmpresa(request.getNombre(), empresaActual.getId())
                .filter(existente -> request.getUuid() == null || !existente.getUuid().equals(request.getUuid()))
                .isPresent();
        if (nombreEnUso) {
            throw new ExcepcionDeApi(CodigoDeErrorMultiempresa.ROL_YA_EXISTE);
        }

        super.hookGuardarAuth(request);

        UsuarioBase usuarioActual = authService.getActualObligatorio();
        boolean usuarioEsGlobal = authService.esSysAdmin(usuarioActual);

        Long idEmpresaObjetivo = empresaActual.getId();
        if (request.getUuid() != null) {
            idEmpresaObjetivo = rolRepository.buscarPorUuid(request.getUuid())
                    .map(rol -> rol.getIdEmpresa())
                    .orElse(idEmpresaObjetivo);
        }

        validarAlcanceDeEmpresa(usuarioEsGlobal, empresaActual.getId(), idEmpresaObjetivo);
    }

    @Override
    public void hookBorrarAuth(String uuid) {
        UsuarioBase usuarioActual = authService.getActualObligatorio();
        EmpresaBase empresaActual = contextoDeEmpresa.getEmpresaActualObligatoria();
        boolean usuarioEsGlobal = authService.esSysAdmin(usuarioActual);

        rolRepository.buscarPorUuid(uuid)
                .map(rol -> rol.getIdEmpresa())
                .ifPresent(idEmpresa -> validarAlcanceDeEmpresa(usuarioEsGlobal, empresaActual.getId(), idEmpresa));
    }

    private void validarAlcanceDeEmpresa(boolean usuarioEsGlobal, Long idEmpresaActual, Long idEmpresaObjetivo) {
        boolean esRolDeSistema = idEmpresaObjetivo != null
                && idEmpresaObjetivo.equals(contextoDeEmpresa.getIdEmpresaDeSistema());
        if (esRolDeSistema && !usuarioEsGlobal) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                    "Solo los administradores globales pueden gestionar roles de sistema.");
        }
        if (!usuarioEsGlobal && !idEmpresaActual.equals(idEmpresaObjetivo)) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                    "No tienes permisos para gestionar roles de otra empresa.");
        }
    }

    @Override
    protected Rol hookGuardarAsignarUsuarioDeCreacion(Rol modelo) {
        modelo = super.hookGuardarAsignarUsuarioDeCreacion(modelo);
        modelo.setIdEmpresa(contextoDeEmpresa.getEmpresaActualObligatoria().getId());
        return modelo;
    }
}
