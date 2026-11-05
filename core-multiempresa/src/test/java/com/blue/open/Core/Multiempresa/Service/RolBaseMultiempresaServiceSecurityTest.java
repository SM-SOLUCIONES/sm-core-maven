package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.DTO.PermisoDTO;
import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Repository.PermisoRepository;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica el scoping por empresa de la gestion de roles: nombres unicos por empresa, roles de
 * sistema solo para sysadmin y ningun admin local gestionando roles de otra empresa.
 * Portado de RoleControllerTest de Core 1.x.
 */
@ExtendWith(MockitoExtension.class)
class RolBaseMultiempresaServiceSecurityTest {

    private final RolBaseMultiempresaService<RolDTO> rolService = new RolBaseMultiempresaService<>();

    @Mock
    private AuthService authService;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PermisoRepository permisoRepository;

    @Mock
    private ContextoDeEmpresa contextoDeEmpresa;

    private UsuarioBase usuario;

    @BeforeEach
    void preparar() {
        // RolBaseMultiempresaService es una clase base: se inyecta por setter, no por constructor
        rolService.setAuthService(authService);
        rolService.setRolRepository(rolRepository);
        rolService.setPermisoRepository(permisoRepository);
        rolService.setContextoDeEmpresa(contextoDeEmpresa);

        // El permiso de alta/edicion (tienePermisos) se da por otorgado: aca se prueban alcance y escalada
        lenient().when(authService.tienePermisos(any())).thenReturn(true);

        usuario = new UsuarioBase() {
        };
        usuario.setId(10L);

        EmpresaBase empresaPropia = new EmpresaBase() {
        };
        empresaPropia.setId(2L);
        empresaPropia.setUuid("empresa-propia");

        lenient().when(contextoDeEmpresa.getEmpresaActualObligatoria()).thenReturn(empresaPropia);
        lenient().when(contextoDeEmpresa.getIdEmpresaDeSistema()).thenReturn(1L);
        lenient().when(authService.getActualObligatorio()).thenReturn(usuario);
        lenient().when(authService.esSysAdmin(usuario)).thenReturn(false);
        lenient().when(authService.getPermisosAgregados(usuario)).thenReturn(Set.of("user.view"));
    }

    private RolDTO rol(String uuid, String nombre, String... permisos) {
        RolDTO dto = new RolDTO();
        dto.setUuid(uuid);
        dto.setNombre(nombre);
        dto.setPermisos(List.of(permisos).stream().map(p -> {
            PermisoDTO pdto = new PermisoDTO();
            pdto.setNombre(p);
            return pdto;
        }).toList());
        return dto;
    }

    private Rol rolExistente(String uuid, Long idEmpresa) {
        Rol rol = new Rol();
        rol.setUuid(uuid);
        rol.setIdEmpresa(idEmpresa);
        return rol;
    }

    @Test
    void crear_nombreDuplicadoEnLaEmpresa_lanzaError() {
        when(rolRepository.findFirstByNombreAndIdEmpresa("OPERADOR", 2L))
                .thenReturn(Optional.of(rolExistente("otro", 2L)));

        assertThrows(ExcepcionDeApi.class, () -> rolService.hookGuardarAuth(rol(null, "OPERADOR")));
    }

    @Test
    void crear_sinPermisos_noLanza() {
        assertDoesNotThrow(() -> rolService.hookGuardarAuth(rol(null, "OBSERVADOR")));
    }

    @Test
    void crear_conPermisoQueNoPoseeEnLaEmpresa_lanza403() {
        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> rolService.hookGuardarAuth(rol(null, "OPERADOR", "user.create")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void editar_rolDeSistemaSinSerGlobal_lanza403() {
        when(rolRepository.buscarPorUuid("rol-sistema")).thenReturn(Optional.of(rolExistente("rol-sistema", 1L)));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> rolService.hookGuardarAuth(rol("rol-sistema", "SOPORTE")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void editar_rolDeOtraEmpresa_lanza403() {
        when(rolRepository.buscarPorUuid("rol-ajeno")).thenReturn(Optional.of(rolExistente("rol-ajeno", 3L)));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> rolService.hookGuardarAuth(rol("rol-ajeno", "AJENO")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void editar_rolDeOtraEmpresaComoSysadmin_noLanza() {
        when(authService.esSysAdmin(usuario)).thenReturn(true);
        when(rolRepository.buscarPorUuid("rol-ajeno")).thenReturn(Optional.of(rolExistente("rol-ajeno", 3L)));

        assertDoesNotThrow(() -> rolService.hookGuardarAuth(rol("rol-ajeno", "AJENO", "cualquier.permiso")));
    }

    @Test
    void borrar_rolDeOtraEmpresa_lanza403() {
        when(rolRepository.buscarPorUuid("rol-ajeno")).thenReturn(Optional.of(rolExistente("rol-ajeno", 3L)));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> rolService.hookBorrarAuth("rol-ajeno"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
