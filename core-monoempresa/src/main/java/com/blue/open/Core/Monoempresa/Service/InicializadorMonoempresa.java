package com.blue.open.Core.Monoempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Puertos.InicializadorDeAutorizacion;
import com.blue.open.Core.Monoempresa.Entidad.UsuarioRol;
import com.blue.open.Core.Monoempresa.Repository.UsuarioRolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Inicializacion de autorizacion en modo mono-empresa: no hay empresas, y el usuario inicial
 * se vincula con el rol SYSADMIN mediante una fila en auth_usuarios_roles.
 * Dependencias externas: la base de datos.
 * Recibe: el usuario inicial y el rol SYSADMIN. Responde: nada; deja la relacion creada.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class InicializadorMonoempresa implements InicializadorDeAutorizacion {

    private final UsuarioRolRepository usuarioRolRepository;

    @Override
    public Long idContextoDeSistema() {
        // Sin empresas, los roles base no pertenecen a ningun contexto
        return null;
    }

    @Override
    public void vincularSysadmin(UsuarioBase usuario, Rol rolSysadmin) {
        if (usuarioRolRepository.existsByIdUsuarioAndRolUuidAndStatus(usuario.getId(), rolSysadmin.getUuid(), 0)) {
            return;
        }
        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setIdUsuario(usuario.getId());
        usuarioRol.setRol(rolSysadmin);
        usuarioRolRepository.save(usuarioRol);
        log.info("Usuario inicial vinculado al rol SYSADMIN");
    }
}
