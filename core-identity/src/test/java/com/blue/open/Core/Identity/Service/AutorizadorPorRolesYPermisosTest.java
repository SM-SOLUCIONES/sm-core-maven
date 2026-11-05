package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Auth.PermisoBaseEnum;
import com.blue.open.Core.Identity.Auth.IPermiso;
import com.blue.open.Core.Identity.Auth.IRol;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.CatalogoDeAutorizacion;
import com.blue.open.Core.Kernel.Contrato.Contrato;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Prueba AutorizadorPorRolesYPermisos: como se cumple el permiso de @Contrato con los roles y permisos de
 * core-identity (PUBLICO, AUTENTICADO, rol o permiso por nombre de constante).
 */
@ExtendWith(MockitoExtension.class)
class AutorizadorPorRolesYPermisosTest {

    @Mock
    private AuthService authService;

    @Mock
    private CatalogoDeAutorizacion catalogo;

    private AutorizadorPorRolesYPermisos autorizador;

    private UsuarioBase usuario;

    @BeforeEach
    void preparar() {
        lenient().when(catalogo.getRoles()).thenReturn(new IRol[0]);
        lenient().when(catalogo.getPermisos()).thenReturn(new IPermiso[0]);
        autorizador = new AutorizadorPorRolesYPermisos(authService, catalogo);
        usuario = new UsuarioBase() {
        };
        usuario.setId(7L);
    }

    @Test
    void publico_noConsultaLaSesion() {
        autorizador.autorizar(new String[] { Contrato.PUBLICO });
        verifyNoInteractions(authService);
    }

    @Test
    void autenticado_soloExigeSesionSinValidarLaEmpresa() {
        autorizador.autorizar(new String[] { Contrato.AUTENTICADO });
        verify(authService).getActualObligatorio();
        verify(authService, never()).autenticar();
    }

    @Test
    void permiso_seResuelvePorElNombreDeLaConstante() {
        when(authService.autenticar()).thenReturn(usuario);
        when(authService.tieneAlgunRolOPermisoPorNombre(usuario, List.of("SYSADMIN"),
                List.of(PermisoBaseEnum.USUARIO_VER.getNombre()))).thenReturn(true);

        assertDoesNotThrow(() -> autorizador.autorizar(new String[] { "SYSADMIN", "USUARIO_VER" }));
    }

    @Test
    void sinElPermiso_respondeSinPermiso() {
        when(authService.autenticar()).thenReturn(usuario);
        when(authService.tieneAlgunRolOPermisoPorNombre(any(), any(), any())).thenReturn(false);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> autorizador.autorizar(new String[] { "USUARIO_VER" }));
        assertEquals(CodigoDeErrorKernel.SIN_PERMISO, ex.getCodigo());
    }

    @Test
    void sinSesion_respondeNoAutenticado() {
        when(authService.autenticar()).thenThrow(new ExcepcionDeApi(CodigoDeErrorKernel.NO_AUTENTICADO));

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> autorizador.autorizar(new String[] { "USUARIO_VER" }));
        assertEquals(CodigoDeErrorKernel.NO_AUTENTICADO, ex.getCodigo());
    }

    @Test
    void nombresValidos_incluyeLosRolesYPermisosBase() {
        assertTrue(autorizador.nombresValidos().containsAll(List.of("SYSADMIN", "ADMIN", "USUARIO_VER", "PERMISO_VER")));
    }
}
