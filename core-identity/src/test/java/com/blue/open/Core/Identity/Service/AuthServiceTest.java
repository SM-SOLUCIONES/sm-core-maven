package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Auth.PermisoBaseEnum;
import com.blue.open.Core.Identity.Auth.RolBaseEnum;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.EstrategiaDeAutenticacion;
import com.blue.open.Core.Identity.Puertos.ResolutorDePermisos;
import com.blue.open.Core.Identity.Puertos.ValidadorDeContexto;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifica el punto unico de autenticacion/autorizacion del Core: resolucion del usuario por los
 * protocolos habilitados, validacion de contexto y chequeo de roles/permisos via el puerto
 * ResolutorDePermisos. Portado de AuthServiceTest de Core 1.x a la API de puertos de 2.0.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private AuthService authService;

    @Mock
    private EstrategiaDeAutenticacion jwt;

    @Mock
    private EstrategiaDeAutenticacion basic;

    @Mock
    private UsuarioRepositorio usuarioRepositorio;

    @Mock
    private ResolutorDePermisos resolutorDePermisos;

    @Mock
    private ValidadorDeContexto validadorDeContexto;

    private UsuarioBase usuario;

    @BeforeEach
    void preparar() {
        // Las dependencias entran por constructor; protocolos sigue siendo un @Value, no un @Autowired
        @SuppressWarnings("unchecked")
        ObjectProvider<EstrategiaDeAutenticacion> estrategias = mock(ObjectProvider.class);
        lenient().when(estrategias.iterator()).thenAnswer(i -> List.of(jwt, basic).iterator());

        authService = new AuthService(estrategias, usuarioRepositorio, resolutorDePermisos, validadorDeContexto);
        ReflectionTestUtils.setField(authService, "protocolos", "jwt,basic");

        usuario = new UsuarioBase() {
        };
        usuario.setId(7L);
        usuario.setUuid("usuario-uuid");

        lenient().when(jwt.getProtocolo()).thenReturn("jwt");
        lenient().when(basic.getProtocolo()).thenReturn("basic");
        lenient().when(usuarioRepositorio.porUuid("usuario-uuid")).thenReturn(Optional.of(usuario));
    }

    @Test
    void getActualObligatorio_sinUsuario_lanza401() {
        when(jwt.getUsuario()).thenReturn(null);
        when(basic.getUsuario()).thenReturn(null);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, authService::getActualObligatorio);
        assertEquals(CodigoDeErrorKernel.NO_AUTENTICADO, ex.getCodigo());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Usuario no identificado", ex.getMensajeParaUsuario());
    }

    @Test
    void getActualObligatorio_usuarioEnJwt_devuelveUsuarioDeLaBase() {
        when(jwt.getUsuario()).thenReturn(usuario);

        assertSame(usuario, authService.getActualObligatorio());
        verify(basic, never()).getUsuario();
    }

    @Test
    void getActualONull_protocoloNoHabilitado_seIgnora() {
        ReflectionTestUtils.setField(authService, "protocolos", "basic");
        when(basic.getUsuario()).thenReturn(null);

        assertNull(authService.getActualONull());
        verify(jwt, never()).getUsuario();
    }

    @Test
    void esProtocoloValido_sinProtocolos_devuelveFalse() {
        ReflectionTestUtils.setField(authService, "protocolos", "");
        assertFalse(authService.esProtocoloValido("jwt"));
    }

    @Test
    void autenticar_ejecutaElValidadorDeContexto() {
        when(jwt.getUsuario()).thenReturn(usuario);

        authService.autenticar();
        verify(validadorDeContexto).validar(usuario);
    }

    @Test
    void autenticar_contextoInvalido_propagaElError() {
        when(jwt.getUsuario()).thenReturn(usuario);
        doThrow(new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "Empresa no permitida", "test"))
                .when(validadorDeContexto).validar(usuario);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> authService.autenticar());
        assertEquals("Empresa no permitida", ex.getMensajeParaUsuario());
    }

    @Test
    void tienePermisos_conElPermiso_devuelveTrue() {
        when(jwt.getUsuario()).thenReturn(usuario);
        when(resolutorDePermisos.tieneAlgunRolOPermiso(eq(7L), eq(List.of()),
                eq(List.of(PermisoBaseEnum.USUARIO_VER.getNombre())))).thenReturn(true);

        assertTrue(authService.tienePermisos(List.of(PermisoBaseEnum.USUARIO_VER)));
    }

    @Test
    void tienePermisos_sinElRol_devuelveFalse() {
        when(jwt.getUsuario()).thenReturn(usuario);
        when(resolutorDePermisos.tieneAlgunRolOPermiso(eq(7L), eq(List.of("ADMIN")), eq(List.of())))
                .thenReturn(false);

        assertFalse(authService.tienePermisos(List.of(RolBaseEnum.ADMIN)));
    }

    @Test
    void tienePermisos_anonimo_devuelveFalse() {
        when(jwt.getUsuario()).thenReturn(null);
        when(basic.getUsuario()).thenReturn(null);

        assertFalse(authService.tienePermisos(List.of(RolBaseEnum.ADMIN)));
    }

    @Test
    void esSysAdmin_delegaEnElResolutor() {
        when(resolutorDePermisos.esSysAdmin(7L)).thenReturn(true);

        assertTrue(authService.esSysAdmin(usuario));
        assertFalse(authService.esSysAdmin(null));
    }
}
