package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Crud.AbstractService;
import com.blue.open.Core.Identity.Auth.PermisoBaseEnum;
import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Permiso;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Repository.PermisoRepository;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de roles. Contiene solo las reglas que no dependen del modelo de empresas:
 * permisos requeridos, prevencion de escalacion de privilegios y rol por defecto unico.
 * El scoping por empresa vive en core-multiempresa (RolBaseMultiempresaService), que extiende esta clase.
 * Dependencias externas: la base de datos.
 * Generico sobre el DTO (desde Core 3.5.0): D es RolDTO o un DTO del proyecto que lo extiende (ej. con un dato
 * calculado). Lo usan obtener, paginar, guardar y borrar, sin cast. Un proyecto que extendia sin parametro migra
 * declarando RolBaseService<RolDTO>; sin parametro sigue compilando (tipo crudo) y resuelve RolDTO.
 * Recibe: D o uuid segun la operacion. Responde: el rol resultante como D.
 */
@Transactional
public class RolBaseService<D extends RolDTO> extends AbstractService<Rol, D, D> {

    protected RolRepository rolRepository;

    protected PermisoRepository permisoRepository;

    protected AuthService authService;

    /*
     * Inyeccion por setter, no por campo ni por constructor: es una clase base que extienden
     * otros modulos del Core y los proyectos. Ver core/docs/rules/inyeccion-de-dependencias.md.
     */

    // Recibe el repositorio de roles
    @Autowired
    public void setRolRepository(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    // Recibe el repositorio de permisos
    @Autowired
    public void setPermisoRepository(PermisoRepository permisoRepository) {
        this.permisoRepository = permisoRepository;
    }

    // Recibe el service de autenticacion, que valida permisos del usuario actual
    @Autowired
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void hookGuardarAuth(D request) {
        UsuarioBase usuarioActual = authService.getActualObligatorio();
        boolean esAlta = (request.getUuid() == null);
        PermisoBaseEnum permisoRequerido = esAlta ? PermisoBaseEnum.ROL_CREAR : PermisoBaseEnum.ROL_EDITAR;
        // Alta y edicion comparten endpoint: el permiso depende del request, no se puede declarar en @Contrato
        if (!authService.tienePermisos(List.of(permisoRequerido))) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "No tienes permiso",
                    "RolBaseService | hookGuardarAuth | falta " + permisoRequerido);
        }

        if (authService.esSysAdmin(usuarioActual)) {
            return;
        }

        // Un usuario no puede crear un rol con permisos que el mismo no tiene
        if (request.getPermisos() != null) {
            Set<String> permisosDelSolicitante = authService.getPermisosAgregados(usuarioActual);
            for (var pDto : request.getPermisos()) {
                // Vacio si el DTO no trae nombre y el uuid no existe: ese permiso no se controla aca
                Optional<String> nombreDePermiso = Optional.ofNullable(pDto.getNombre());
                if (nombreDePermiso.isEmpty() && pDto.getUuid() != null) {
                    nombreDePermiso = permisoRepository.buscarPorUuid(pDto.getUuid())
                            .map(permiso -> permiso.getNombre());
                }
                if (nombreDePermiso.isPresent() && !permisosDelSolicitante.contains(nombreDePermiso.get())) {
                    throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                            "Escalación detectada",
                            "No puedes asignar el permiso '" + nombreDePermiso.get() + "' porque tú mismo no lo posees.");
                }
            }
        }
    }

    @Override
    protected List<D> hookPaginarFiltrarResultados(List<D> dtos) {
        UsuarioBase usuarioActual = authService.getActualObligatorio();
        if (authService.esSysAdmin(usuarioActual)) {
            return dtos;
        }
        Set<String> permisosDelSolicitante = authService.getPermisosAgregados(usuarioActual);
        return dtos.stream()
                .filter(rol -> puedeAsignarRol(rol, usuarioActual, permisosDelSolicitante))
                .collect(Collectors.toList());
    }

    /**
     * Define si el usuario actual puede ver/asignar un rol. Por defecto, solo si posee todos
     * los permisos que ese rol otorga. El proyecto puede ampliarlo (ver core/docs/rules).
     */
    public boolean puedeAsignarRol(RolDTO rol, UsuarioBase usuarioActual, Set<String> permisosDelSolicitante) {
        if (rol.getPermisos() == null || rol.getPermisos().isEmpty()) {
            return true;
        }
        return rol.getPermisos().stream()
                .allMatch(p -> permisosDelSolicitante.contains(p.getNombre()));
    }

    // Los permisos son una @Coleccion (solo lectura): se asocian aca, por uuid, a partir del DTO
    @Override
    protected Rol hookGuardarAplicarDto(D dto, Rol modelo) {
        modelo = super.hookGuardarAplicarDto(dto, modelo);
        if (dto.getPermisos() != null) {
            List<Permiso> permisosDeLaBase = new ArrayList<>();
            for (var pDto : dto.getPermisos()) {
                if (pDto.getUuid() != null) {
                    permisoRepository.buscarPorUuid(pDto.getUuid()).ifPresent(permisosDeLaBase::add);
                }
            }
            modelo.setPermisos(permisosDeLaBase);
        }
        return modelo;
    }

    @Override
    protected Rol hookGuardarEnBase(Rol modelo) {
        // Solo puede haber un rol por defecto por empresa (o uno global en mono-empresa)
        if (modelo.getPorDefecto() != null && modelo.getPorDefecto() == 1) {
            List<Rol> rolesPorDefectoExistentes = rolRepository.findAllByIdEmpresaAndPorDefecto(modelo.getIdEmpresa(), 1);
            for (var rol : rolesPorDefectoExistentes) {
                if (!rol.getUuid().equals(modelo.getUuid())) {
                    rol.setPorDefecto(0);
                    rolRepository.save(rol);
                }
            }
        }
        return super.hookGuardarEnBase(modelo);
    }
}
