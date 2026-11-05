package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Crud.AbstractService;
import com.blue.open.Core.Identity.DTO.PermisoDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Permiso;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de permisos. Solo lectura: los permisos se sincronizan desde los enums del proyecto
 * (ver InicializadorDeBaseService), nunca se crean por API.
 * Dependencias externas: la base de datos.
 * Recibe: el PedidoDePaginado del listado. Responde: los permisos que el usuario actual posee.
 */
@Transactional
@Service
@RequiredArgsConstructor
public class PermisoService extends AbstractService<Permiso, PermisoDTO, PermisoDTO> {

    private final AuthService authService;

    @Override
    protected List<PermisoDTO> hookPaginarFiltrarResultados(List<PermisoDTO> dtos) {
        UsuarioBase usuarioActual = authService.getActualObligatorio();
        if (authService.esSysAdmin(usuarioActual)) {
            return dtos;
        }

        Set<String> permisosDelSolicitante = authService.getPermisosAgregados(usuarioActual);

        return dtos.stream()
                .filter(p -> permisosDelSolicitante.contains(p.getNombre()))
                .collect(Collectors.toList());
    }
}
