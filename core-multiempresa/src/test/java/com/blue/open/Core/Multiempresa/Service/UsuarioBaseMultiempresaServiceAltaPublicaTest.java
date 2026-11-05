package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Proyeccion.Proyector;
import com.blue.open.Core.Multiempresa.DTO.UsuarioBaseMultiempresaDTO;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Entidad.UsuarioEmpresa;
import com.blue.open.Core.Multiempresa.Excepciones.CodigoDeErrorMultiempresa;
import com.blue.open.Core.Multiempresa.Repository.EmpresaRepositorio;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifica las reglas de seguridad de los endpoints de alta publica (Core 2.0.1): publico/vincular
 * exige sesion y solo vincula al propio usuario, y ninguna alta publica entra a la empresa de
 * sistema, cuyos roles valen en todas las empresas.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioBaseMultiempresaServiceAltaPublicaTest {

    private final UsuarioBaseMultiempresaService<UsuarioBase, UsuarioBaseMultiempresaDTO> service = new UsuarioBaseMultiempresaService<>();

    @Mock
    private AuthService authService;

    @Mock
    private ContextoDeEmpresa contextoDeEmpresa;

    @Mock
    private EmpresaRepositorio empresaRepositorio;

    @Mock
    private UsuarioEmpresaRepository usuarioEmpresaRepository;

    @Mock
    private UsuarioRepositorio usuarioRepositorio;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private Proyector proyector;

    private UsuarioBase usuario;
    private EmpresaBase sistema;
    private EmpresaBase cliente;

    @BeforeEach
    void preparar() {
        // UsuarioBaseMultiempresaService es una clase base: se inyecta por setter, no por constructor
        service.setAuthService(authService);
        service.setContextoDeEmpresa(contextoDeEmpresa);
        service.setEmpresaRepositorio(empresaRepositorio);
        service.setUsuarioEmpresaRepository(usuarioEmpresaRepository);
        service.setUsuarioRepositorio(usuarioRepositorio);
        service.setRolRepository(rolRepository);
        service.setProyector(proyector);

        usuario = new UsuarioBase() {
        };
        usuario.setId(7L);
        usuario.setUuid("usuario-uuid");

        sistema = new EmpresaBase() {
        };
        sistema.setId(1L);
        sistema.setUuid("1");

        cliente = new EmpresaBase() {
        };
        cliente.setId(2L);
        cliente.setUuid("cliente-uuid");

        lenient().when(contextoDeEmpresa.getIdEmpresaDeSistema()).thenReturn(1L);
        lenient().when(contextoDeEmpresa.resolverUuidDeEmpresa(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(empresaRepositorio.porUuidActiva("1")).thenReturn(Optional.of(sistema));
        lenient().when(empresaRepositorio.porUuidActiva("cliente-uuid")).thenReturn(Optional.of(cliente));
        lenient().when(usuarioRepositorio.porUuid("usuario-uuid")).thenReturn(Optional.of(usuario));
        lenient().when(proyector.porUuid(any(), any(), any())).thenReturn(Optional.of(new UsuarioBaseMultiempresaDTO()));
    }

    @Test
    void vincular_anonimo_lanza401() {
        when(authService.getActualObligatorio())
                .thenThrow(new ExcepcionDeApi(CodigoDeErrorKernel.NO_AUTENTICADO, "Usuario no identificado", "test"));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> service.vincular("cliente-uuid", "usuario-uuid"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(usuarioEmpresaRepository, never()).save(any());
    }

    @Test
    void vincular_otroUsuario_lanza403() {
        when(authService.getActualObligatorio()).thenReturn(usuario);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> service.vincular("cliente-uuid", "otro-usuario-uuid"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(usuarioEmpresaRepository, never()).save(any());
    }

    @Test
    void vincular_aLaEmpresaDeSistema_lanzaEmpresaDeSistemaNoPermitida() {
        when(authService.getActualObligatorio()).thenReturn(usuario);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> service.vincular("1", "usuario-uuid"));
        assertEquals(CodigoDeErrorMultiempresa.EMPRESA_DE_SISTEMA_NO_PERMITIDA, ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(usuarioEmpresaRepository, never()).save(any());
    }

    @Test
    void vincular_yaVinculado_noDuplicaLaRelacion() {
        when(authService.getActualObligatorio()).thenReturn(usuario);
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(2L, 7L)).thenReturn(true);

        service.vincular("cliente-uuid", "usuario-uuid");
        verify(usuarioEmpresaRepository, never()).save(any());
    }

    @Test
    void vincular_propioUsuario_loVinculaConElRolPorDefecto() {
        Rol rolPorDefecto = new Rol();
        rolPorDefecto.setNombre("CLIENTE");
        when(authService.getActualObligatorio()).thenReturn(usuario);
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(2L, 7L)).thenReturn(false);
        when(rolRepository.findFirstByIdEmpresaAndPorDefecto(2L, 1)).thenReturn(Optional.of(rolPorDefecto));

        service.vincular("cliente-uuid", "usuario-uuid");

        verify(usuarioEmpresaRepository).save(argThat((UsuarioEmpresa ucr) ->
                ucr.getIdEmpresa().equals(2L) && ucr.getIdUsuario().equals(7L)
                        && ucr.getRoles().contains(rolPorDefecto)));
    }

    @Test
    void registroPublicoDeCliente_enLaEmpresaDeSistema_lanzaEmpresaDeSistemaNoPermitida() {
        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> service.registroPublicoDeCliente("1", new UsuarioBaseMultiempresaDTO()));
        assertEquals(CodigoDeErrorMultiempresa.EMPRESA_DE_SISTEMA_NO_PERMITIDA, ex.getCodigo());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(usuarioEmpresaRepository, never()).save(any());
    }
}
