package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Auth.PermisoBaseEnum;
import com.blue.open.Core.Identity.Auth.RolBaseEnum;
import com.blue.open.Core.Identity.Auth.IPermiso;
import com.blue.open.Core.Identity.Auth.IRol;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.CatalogoDeAutorizacion;
import com.blue.open.Core.Kernel.Contrato.Contrato;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Puertos.AutorizadorDeEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hace cumplir el permiso de @Contrato con los roles y permisos de core-identity.
 * - PUBLICO: no exige nada.
 * - AUTENTICADO: exige sesion (sin validar la empresa del header: lo usan endpoints como vincularse a una empresa
 *   a la que el usuario todavia no pertenece).
 * - Roles/permisos: exige sesion, contexto valido (AuthService.autenticar) y alguno de los nombres declarados.
 * Los nombres son los de la constante del enum (ej. "SYSADMIN", "USUARIO_VER") de RolBaseEnum, PermisoBaseEnum y
 * del CatalogoDeAutorizacion del proyecto.
 * Dependencias externas: la base de datos (via AuthService). Recibe: el permiso de @Contrato.
 * Responde: nada si el request lo cumple; ExcepcionDeApi(NO_AUTENTICADO) si exige sesion y no la hay;
 * ExcepcionDeApi(SIN_PERMISO) si el usuario autenticado no tiene ninguno de los roles o permisos.
 */
@Component
@RequiredArgsConstructor
public class AutorizadorPorRolesYPermisos implements AutorizadorDeEndpoint {

    private final AuthService authService;

    private final CatalogoDeAutorizacion catalogo;

    @Override
    public void autorizar(String[] permiso) {
        List<String> nombres = Arrays.asList(permiso);
        if (nombres.contains(Contrato.PUBLICO)) {
            return;
        }
        if (nombres.contains(Contrato.AUTENTICADO)) {
            authService.getActualObligatorio();
            return;
        }
        UsuarioBase usuario = authService.autenticar();
        Map<String, Object> declarados = declarados();
        List<String> roles = new ArrayList<>();
        List<String> permisos = new ArrayList<>();
        for (String nombre : nombres) {
            Object declarado = declarados.get(nombre);
            if (declarado instanceof IRol rol) {
                roles.add(rol.name());
            } else if (declarado instanceof IPermiso permisoDeclarado) {
                permisos.add(permisoDeclarado.getNombre());
            }
        }
        if (!authService.tieneAlgunRolOPermisoPorNombre(usuario, roles, permisos)) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "No tienes permiso",
                    "Falta de permisos para acceder a la ruta: " + nombres);
        }
    }

    @Override
    public Set<String> nombresValidos() {
        return declarados().keySet();
    }

    // Nombre de la constante -> rol o permiso declarado. Lanza IllegalStateException si un rol y un permiso
    // comparten nombre (el @Contrato seria ambiguo)
    private Map<String, Object> declarados() {
        Map<String, Object> declarados = new HashMap<>();
        List<Object> todos = new ArrayList<>();
        todos.addAll(Arrays.asList(RolBaseEnum.values()));
        todos.addAll(Arrays.asList(catalogo.getRoles()));
        todos.addAll(Arrays.asList(PermisoBaseEnum.values()));
        todos.addAll(Arrays.asList(catalogo.getPermisos()));
        for (Object declarado : todos) {
            String nombre = ((Enum<?>) declarado).name();
            Object anterior = declarados.put(nombre, declarado);
            if (anterior != null && (anterior instanceof IRol) != (declarado instanceof IRol)) {
                throw new IllegalStateException("El nombre " + nombre + " es a la vez un rol y un permiso: "
                        + "@Contrato no puede distinguirlos");
            }
        }
        return declarados;
    }
}
