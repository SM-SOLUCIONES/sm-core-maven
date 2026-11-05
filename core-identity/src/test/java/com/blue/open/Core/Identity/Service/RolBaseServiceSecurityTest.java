package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.DTO.PermisoDTO;
import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Repository.PermisoRepository;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica la regla de anti-escalacion de privilegios de RolBaseService, que no depende del
 * modelo de empresas: nadie puede crear un rol con permisos que el mismo no posee.
 */
@ExtendWith(MockitoExtension.class)
class RolBaseServiceSecurityTest {

    // Sin @InjectMocks: Mockito no resuelve la inyeccion por tipo en clases con campos genericos
    private final RolBaseService<RolDTO> rolService = new RolBaseService<>() {
    };

    @Mock
    private AuthService authService;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PermisoRepository permisoRepository;

    private UsuarioBase usuarioActual;

    @BeforeEach
    void preparar() {
        // RolBaseService es una clase base: se inyecta por setter, no por constructor
        rolService.setAuthService(authService);
        rolService.setRolRepository(rolRepository);
        rolService.setPermisoRepository(permisoRepository);

        // El permiso de alta/edicion (tienePermisos) se da por otorgado: aca se prueban alcance y escalada
        lenient().when(authService.tienePermisos(any())).thenReturn(true);

        usuarioActual = new UsuarioBase() {
        };
        usuarioActual.setId(10L);
        usuarioActual.setUuid("user-uuid");
    }

    private RolDTO rolConPermisos(String... permisos) {
        RolDTO dto = new RolDTO();
        dto.setNombre("ROL_NUEVO");
        dto.setPermisos(List.of(permisos).stream().map(p -> {
            PermisoDTO pdto = new PermisoDTO();
            pdto.setNombre(p);
            return pdto;
        }).toList());
        return dto;
    }

    @Test
    void guardar_conPermisoQueNoPosee_lanza403() {
        when(authService.getActualObligatorio()).thenReturn(usuarioActual);
        when(authService.esSysAdmin(usuarioActual)).thenReturn(false);
        when(authService.getPermisosAgregados(usuarioActual)).thenReturn(Set.of("user.view"));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> rolService.hookGuardarAuth(rolConPermisos("user.delete")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void guardar_conPermisosQuePosee_noLanza() {
        when(authService.getActualObligatorio()).thenReturn(usuarioActual);
        when(authService.esSysAdmin(usuarioActual)).thenReturn(false);
        when(authService.getPermisosAgregados(usuarioActual)).thenReturn(Set.of("user.view", "user.delete"));

        assertDoesNotThrow(() -> rolService.hookGuardarAuth(rolConPermisos("user.view", "user.delete")));
    }

    @Test
    void guardar_comoSysadmin_noValidaEscalacion() {
        when(authService.getActualObligatorio()).thenReturn(usuarioActual);
        when(authService.esSysAdmin(usuarioActual)).thenReturn(true);

        assertDoesNotThrow(() -> rolService.hookGuardarAuth(rolConPermisos("cualquier.permiso")));
        verify(authService, never()).getPermisosAgregados(any());
    }
}
