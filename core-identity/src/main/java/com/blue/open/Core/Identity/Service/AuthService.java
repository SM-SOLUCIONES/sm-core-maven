package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Auth.IPermiso;
import com.blue.open.Core.Identity.Auth.IRol;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.EstrategiaDeAutenticacion;
import com.blue.open.Core.Identity.Puertos.ResolutorDePermisos;
import com.blue.open.Core.Identity.Puertos.ValidadorDeContexto;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Puertos.ProveedorDeUsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Punto unico de autenticacion y autorizacion del Core.
 * Dependencias externas: los modulos core-protocolo-* presentes (cada uno aporta una
 * EstrategiaDeAutenticacion) y el modulo de scoping (aporta el ResolutorDePermisos).
 * Recibe: la request en curso y la lista de roles/permisos requeridos.
 * Responde: el usuario autenticado; 401 NO_AUTENTICADO si la request no identifica a ningun usuario, o 403
 * SIN_PERMISO si esta autenticado pero no tiene permisos.
 * Implementa ProveedorDeUsuarioActual, con lo cual el kernel y el CRUD obtienen la auditoria
 * y la validacion de permisos sin conocer nada de este modulo.
 */
@Service
@RequiredArgsConstructor
public class AuthService implements ProveedorDeUsuarioActual {

    // Los protocolos presentes en el classpath. Puede estar vacio: un proyecto sin login es valido,
    // por eso es un ObjectProvider y no un List inyectado directo (que fallaria sin candidatos).
    private final ObjectProvider<EstrategiaDeAutenticacion> estrategias;

    private final UsuarioRepositorio usuarioRepositorio;

    private final ResolutorDePermisos resolutorDePermisos;

    private final ValidadorDeContexto validadorDeContexto;

    // Protocolos habilitados, separados por coma (ej: jwt,basic,tokens)
    @Value("${auth.protocolos:}")
    private String protocolos;

    // Recibe: el nombre de un protocolo (ej. jwt). Responde: true si esta habilitado en auth.protocolos
    public boolean esProtocoloValido(String protocolo) {
        if (protocolos == null || protocolos.isBlank()) {
            return false;
        }
        return Arrays.stream(protocolos.split(",")).map(p -> p.trim()).anyMatch(p -> p.equals(protocolo));
    }

    // Usuario logeado actual, resuelto por la primera estrategia de autenticacion habilitada que lo identifique
    // y releido de la base. Dependencias externas: la base de datos (y la del protocolo, ej. SSO).
    // Responde: el usuario, o null si la request no identifica a ninguno.
    public UsuarioBase getActualONull() {
        try {
            UsuarioBase usuario = resolverPorProtocolos();
            if (usuario == null) {
                return null;
            }
            // orElse(null): el contrato del metodo es devolver null cuando no hay usuario actual
            return usuarioRepositorio.porUuid(usuario.getUuid()).orElse(null);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "getActualONull: " + e.getMessage());
        }
    }

    private UsuarioBase resolverPorProtocolos() {
        for (EstrategiaDeAutenticacion estrategia : estrategias) {
            if (!esProtocoloValido(estrategia.getProtocolo())) {
                continue;
            }
            UsuarioBase usuario = estrategia.getUsuario();
            if (usuario != null) {
                return usuario;
            }
        }
        return null;
    }

    // Usuario logeado actual. Lanza 401 NO_AUTENTICADO si la request no identifica a ningun usuario
    public UsuarioBase getActualObligatorio() {
        UsuarioBase usuario = getActualONull();
        if (usuario == null)
            throw new ExcepcionDeApi(CodigoDeErrorKernel.NO_AUTENTICADO,
                    "No se encontró usuario logeado");
        return usuario;
    }

    /**
     * Identifica al usuario y valida el contexto de la peticion (con core-multiempresa,
     * que pertenezca a la empresa del header). Lo usan AutorizadorPorRolesYPermisos antes de chequear
     * un permiso de @Contrato y ContextoDeEmpresa. Lanza 401 NO_AUTENTICADO si no hay sesion y 403 SIN_PERMISO si el
     * contexto no es valido.
     */
    public UsuarioBase autenticar() {
        UsuarioBase usuario = getActualObligatorio();
        validadorDeContexto.validar(usuario);
        return usuario;
    }

    // true si el usuario tiene alguno de los roles (IRol.name()) o permisos (IPermiso.getNombre()) recibidos
    public boolean tieneAlgunRolOPermisoPorNombre(UsuarioBase usuario, List<String> nombresDeRoles, List<String> nombresDePermisos) {
        if (usuario == null || usuario.getId() == null) {
            return false;
        }
        return resolutorDePermisos.tieneAlgunRolOPermiso(usuario.getId(), nombresDeRoles, nombresDePermisos);
    }

    // true si el usuario tiene alguno de los roles (IRol) o permisos (IPermiso) recibidos como enums.
    // Responde false si el usuario o la lista vienen vacios.
    protected boolean tieneAlgunRolOPermiso(UsuarioBase usuario, List<Enum<?>> requeridos) {
        if (usuario == null || requeridos == null || requeridos.isEmpty()) {
            return false;
        }
        List<String> nombresDeRoles = new ArrayList<>();
        List<String> nombresDePermisos = new ArrayList<>();
        for (Enum<?> requerido : requeridos) {
            if (requerido instanceof IRol)
                nombresDeRoles.add(((IRol) requerido).name());
            else if (requerido instanceof IPermiso)
                nombresDePermisos.add(((IPermiso) requerido).getNombre());
        }
        return resolutorDePermisos.tieneAlgunRolOPermiso(usuario.getId(), nombresDeRoles, nombresDePermisos);
    }

    // true si el usuario logeado actual tiene el rol SYSADMIN
    public boolean esSysAdmin() {
        return esSysAdmin(getActualONull());
    }

    // true si el usuario recibido tiene el rol SYSADMIN (false si es null o no esta guardado)
    public boolean esSysAdmin(UsuarioBase usuario) {
        if (usuario == null || usuario.getId() == null)
            return false;
        return resolutorDePermisos.esSysAdmin(usuario.getId());
    }

    // Responde: los nombres de todos los permisos que el usuario tiene por sus roles (vacio si es null)
    public Set<String> getPermisosAgregados(UsuarioBase usuario) {
        if (usuario == null || usuario.getId() == null)
            return Collections.emptySet();
        return resolutorDePermisos.permisosDe(usuario.getId());
    }

    // --- ProveedorDeUsuarioActual (puerto del kernel) ---

    @Override
    public Long getIdUsuarioActual() {
        UsuarioBase usuario = getActualONull();
        return usuario == null ? null : usuario.getId();
    }

    // true si el usuario actual tiene alguno de los roles/permisos. Para autorizar segun los datos del request o
    // del registro; el permiso fijo de un endpoint va en @Contrato
    @Override
    public boolean tienePermisos(List<Enum<?>> requeridos) {
        return tieneAlgunRolOPermiso(getActualONull(), requeridos);
    }
}
