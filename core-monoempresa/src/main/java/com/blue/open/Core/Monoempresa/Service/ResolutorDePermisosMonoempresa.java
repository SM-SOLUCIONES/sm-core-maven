package com.blue.open.Core.Monoempresa.Service;

import com.blue.open.Core.Identity.Auth.RolBaseEnum;
import com.blue.open.Core.Identity.Puertos.ResolutorDePermisos;
import com.blue.open.Core.Monoempresa.Repository.UsuarioRolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resuelve roles y permisos leyendo auth_usuarios_roles: el usuario tiene roles directos, sin empresa.
 * Dependencias externas: la base de datos.
 * Recibe: el id del usuario y los nombres a chequear.
 * Responde: si tiene alguno, el set completo de permisos, y si es SYSADMIN.
 */
@Primary
@Service
@RequiredArgsConstructor
public class ResolutorDePermisosMonoempresa implements ResolutorDePermisos {

    private final UsuarioRolRepository usuarioRolRepository;

    @Override
    public boolean tieneAlgunRolOPermiso(Long idUsuario, List<String> roles, List<String> permisos) {
        if (idUsuario == null) {
            return false;
        }
        if (roles != null && !roles.isEmpty() && usuarioRolRepository.existeRolParaUsuario(idUsuario, roles)) {
            return true;
        }
        return permisos != null && !permisos.isEmpty()
                && usuarioRolRepository.existePermisoParaUsuario(idUsuario, permisos);
    }

    @Override
    public Set<String> permisosDe(Long idUsuario) {
        if (idUsuario == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(usuarioRolRepository.buscarNombresDePermisosAgregados(idUsuario));
    }

    @Override
    public boolean esSysAdmin(Long idUsuario) {
        if (idUsuario == null) {
            return false;
        }
        return usuarioRolRepository.existeRolParaUsuario(idUsuario, List.of(RolBaseEnum.SYSADMIN.name()));
    }
}
