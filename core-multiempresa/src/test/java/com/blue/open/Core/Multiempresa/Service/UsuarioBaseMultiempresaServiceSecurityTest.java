package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Auth.RolBaseEnum;
import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Permiso;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.DTO.UsuarioBaseMultiempresaDTO;
import com.blue.open.Core.Multiempresa.DTO.UsuarioEmpresaDTO;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifica las reglas de seguridad multi-empresa del alta/edicion de usuarios:
 * un admin local no puede tocar usuarios de otra empresa ni otorgar permisos que no tiene.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioBaseMultiempresaServiceSecurityTest {

    // Sin @InjectMocks: Mockito no resuelve la inyeccion por tipo en clases con campos genericos
    // (EntidadRepository<T>, Proyector), asi que se inyecta a mano.
    private final UsuarioBaseMultiempresaService<UsuarioBase, UsuarioBaseMultiempresaDTO> service = new UsuarioBaseMultiempresaService<>();

    @Mock
    private AuthService authService;

    @Mock
    private ContextoDeEmpresa contextoDeEmpresa;

    @Mock
    private UsuarioEmpresaRepository usuarioEmpresaRepository;

    @Mock
    private UsuarioRepositorio usuarioRepositorio;

    @Mock
    private RolRepository rolRepository;

    private UsuarioBase adminLocal;
    private EmpresaBase empresaPropia;

    @BeforeEach
    void preparar() {
        // UsuarioBaseMultiempresaService es una clase base: se inyecta por setter, no por constructor
        service.setAuthService(authService);
        service.setContextoDeEmpresa(contextoDeEmpresa);
        service.setUsuarioEmpresaRepository(usuarioEmpresaRepository);
        service.setUsuarioRepositorio(usuarioRepositorio);
        service.setRolRepository(rolRepository);

        // El permiso de alta/edicion (tienePermisos) se da por otorgado: aca se prueban alcance y escalada
        lenient().when(authService.tienePermisos(any())).thenReturn(true);

        adminLocal = new UsuarioBase() {
        };
        adminLocal.setId(10L);
        adminLocal.setUuid("admin-uuid");

        empresaPropia = new EmpresaBase() {
        };
        empresaPropia.setId(100L);
        empresaPropia.setUuid("empresa-propia");

        when(authService.getActualObligatorio()).thenReturn(adminLocal);
        when(authService.esSysAdmin(adminLocal)).thenReturn(false);
        when(contextoDeEmpresa.getEmpresaActualObligatoria()).thenReturn(empresaPropia);
    }

    private UsuarioBaseMultiempresaDTO dtoConRol(String uuidRegistro, String empresaUuid, String rolUuid) {
        UsuarioBaseMultiempresaDTO dto = new UsuarioBaseMultiempresaDTO();
        dto.setUuid(uuidRegistro);
        UsuarioEmpresaDTO ucr = new UsuarioEmpresaDTO();
        ucr.setEmpresaUuid(empresaUuid);
        RolDTO rol = new RolDTO();
        rol.setUuid(rolUuid);
        ucr.setRoles(List.of(rol));
        dto.setRolesPorEmpresa(List.of(ucr));
        return dto;
    }

    private Rol rolDeEmpresa(String uuid, String nombre, Long idEmpresa, String... permisos) {
        Rol rol = new Rol();
        rol.setUuid(uuid);
        rol.setNombre(nombre);
        rol.setIdEmpresa(idEmpresa);
        rol.setPermisos(List.of(permisos).stream().map(p -> {
            Permiso permiso = new Permiso();
            permiso.setNombre(p);
            return permiso;
        }).toList());
        return rol;
    }

    @Test
    void editar_usuarioDeOtraEmpresa_lanza403() {
        UsuarioBase objetivo = new UsuarioBase() {
        };
        objetivo.setId(55L);
        UsuarioBaseMultiempresaDTO dto = dtoConRol("otro-usuario", "empresa-propia", "rol-uuid");

        when(rolRepository.buscarPorUuidsConPermisos(any())).thenReturn(List.of());
        when(usuarioRepositorio.porUuid("otro-usuario")).thenReturn(Optional.of(objetivo));
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(100L, 55L)).thenReturn(false);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> service.hookGuardarAuth(dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void crear_asignandoRolDeOtraEmpresa_lanza403() {
        UsuarioBaseMultiempresaDTO dto = dtoConRol(null, "otra-empresa", "rol-uuid");
        when(rolRepository.buscarPorUuidsConPermisos(any())).thenReturn(List.of());

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> service.hookGuardarAuth(dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void crear_otorgandoPermisoQueNoPosee_lanzaEscalacion() {
        UsuarioBaseMultiempresaDTO dto = dtoConRol(null, "empresa-propia", "rol-uuid");
        Rol rol = rolDeEmpresa("rol-uuid", "OPERADOR", 100L, "user.delete");

        when(rolRepository.buscarPorUuidsConPermisos(any())).thenReturn(List.of(rol));
        when(usuarioEmpresaRepository.existeRolParaUsuario(eq(10L), eq(100L), any(), any())).thenReturn(false);
        when(authService.getPermisosAgregados(adminLocal)).thenReturn(Set.of("user.view"));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> service.hookGuardarAuth(dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Escalación") || ex.getMessage().contains("permiso"));
    }

    @Test
    void crear_comoSysadmin_puedeAsignarCualquierRol() {
        when(authService.esSysAdmin(adminLocal)).thenReturn(true);
        UsuarioBaseMultiempresaDTO dto = dtoConRol(null, "otra-empresa", "rol-sistema");

        assertDoesNotThrow(() -> service.hookGuardarAuth(dto));
    }

    @Test
    void crear_asignandoRolDeSistemaSinSerGlobal_lanza403() {
        UsuarioBaseMultiempresaDTO dto = dtoConRol(null, "empresa-propia", "rol-sistema");
        Rol rolSistema = rolDeEmpresa("rol-sistema", RolBaseEnum.SYSADMIN.name(), 1L);

        when(contextoDeEmpresa.getIdEmpresaDeSistema()).thenReturn(1L);
        when(rolRepository.buscarPorUuidsConPermisos(any())).thenReturn(List.of(rolSistema));
        when(usuarioEmpresaRepository.existeRolParaUsuario(eq(10L), eq(100L), any(), any())).thenReturn(true);
        when(authService.getPermisosAgregados(adminLocal)).thenReturn(Set.of());

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> service.hookGuardarAuth(dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void crear_sinRoles_noLanza() {
        UsuarioBaseMultiempresaDTO dto = new UsuarioBaseMultiempresaDTO();
        when(usuarioEmpresaRepository.existeRolParaUsuario(eq(10L), eq(100L), any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> service.hookGuardarAuth(dto));
    }

    @Test
    void crear_asignandoAdminSinSerAdminLocal_lanza403() {
        UsuarioBaseMultiempresaDTO dto = dtoConRol(null, "empresa-propia", "rol-admin");
        Rol rolAdmin = rolDeEmpresa("rol-admin", RolBaseEnum.ADMIN.name(), 100L);

        when(rolRepository.buscarPorUuidsConPermisos(any())).thenReturn(List.of(rolAdmin));
        when(usuarioEmpresaRepository.existeRolParaUsuario(eq(10L), eq(100L), any(), any())).thenReturn(false);
        when(authService.getPermisosAgregados(adminLocal)).thenReturn(Set.of());

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> service.hookGuardarAuth(dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
