package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Puertos.SincronizadorDeRolesExternos;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Entidad.UsuarioEmpresa;
import com.blue.open.Core.Multiempresa.Repository.EmpresaRepositorio;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Escribe en auth_usuarios_empresas los roles que declara un sistema externo (SSO, Malvinas).
 * El contexto externo se resuelve como una empresa con ese nombre (ej. una empresa llamada "sso").
 * Si esa empresa no existe, no se sincroniza nada: el usuario queda autenticado pero sin roles.
 * Dependencias externas: la base de datos.
 * Recibe: el usuario, el nombre del contexto externo y los roles. Responde: nada.
 */
@Primary
@Service
@RequiredArgsConstructor
public class SincronizadorDeRolesExternosMultiempresa implements SincronizadorDeRolesExternos {

    private final EmpresaRepositorio empresaRepositorio;

    private final UsuarioEmpresaRepository usuarioEmpresaRepository;

    private final RolRepository rolRepository;

    @Override
    public void sincronizar(UsuarioBase usuario, String contextoExterno, List<String> nombresDeRoles) {
        // Sin empresa para ese contexto externo no hay donde escribir los roles: el usuario queda sin ellos
        Optional<EmpresaBase> empresa = empresaRepositorio.porNombreActiva(contextoExterno);
        if (empresa.isEmpty() || usuario.getId() == null) {
            return;
        }
        EmpresaBase empresaDelContexto = empresa.get();

        UsuarioEmpresa relacion = usuarioEmpresaRepository
                .buscarPorUsuarioYEmpresa(usuario.getId(), empresaDelContexto.getId())
                .orElseGet(() -> {
                    UsuarioEmpresa nueva = new UsuarioEmpresa();
                    nueva.setIdUsuario(usuario.getId());
                    nueva.setIdEmpresa(empresaDelContexto.getId());
                    return nueva;
                });

        if (nombresDeRoles != null) {
            List<Rol> roles = new ArrayList<>();
            for (String nombre : nombresDeRoles) {
                // El rol propio de la empresa gana; si no existe, se cae al rol global con ese nombre
                rolRepository.findFirstByNombreAndIdEmpresa(nombre, empresaDelContexto.getId())
                        .or(() -> rolRepository.findFirstByNombre(nombre))
                        .ifPresent(roles::add);
            }
            relacion.setRoles(roles);
        }

        usuarioEmpresaRepository.save(relacion);
    }
}
