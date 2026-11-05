package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Puertos.ResolutorDePermisos;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resuelve roles y permisos en modo multi-empresa: valen los roles que el usuario tiene en la
 * empresa del header X-Empresa-Uuid, mas los que tenga en la empresa de sistema.
 * Dependencias externas: la base de datos y el contexto HTTP.
 * Recibe: el id del usuario y los nombres a chequear. Responde: si los tiene, y el set de permisos.
 */
@Primary
@Service
@RequiredArgsConstructor
public class ResolutorDePermisosMultiempresa implements ResolutorDePermisos {

    private final UsuarioEmpresaRepository usuarioEmpresaRepository;

    private final ContextoDeEmpresa contextoDeEmpresa;

    @Override
    public boolean tieneAlgunRolOPermiso(Long idUsuario, List<String> roles, List<String> permisos) {
        if (idUsuario == null) {
            return false;
        }
        Long idEmpresa = contextoDeEmpresa.getIdEmpresaActualONull();
        if (roles != null && !roles.isEmpty()
                && usuarioEmpresaRepository.existeRolParaUsuario(idUsuario, idEmpresa,
                        contextoDeEmpresa.getIdEmpresaDeSistema(), roles)) {
            return true;
        }
        return permisos != null && !permisos.isEmpty()
                && usuarioEmpresaRepository.existePermisoParaUsuario(idUsuario, idEmpresa,
                        contextoDeEmpresa.getIdEmpresaDeSistema(), permisos);
    }

    @Override
    public Set<String> permisosDe(Long idUsuario) {
        if (idUsuario == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(usuarioEmpresaRepository.buscarNombresDePermisosAgregados(idUsuario,
                contextoDeEmpresa.getIdEmpresaActualONull(), contextoDeEmpresa.getIdEmpresaDeSistema()));
    }

    @Override
    public boolean esSysAdmin(Long idUsuario) {
        if (idUsuario == null) {
            return false;
        }
        return usuarioEmpresaRepository.esSysAdmin(idUsuario, contextoDeEmpresa.getIdEmpresaDeSistema());
    }
}
