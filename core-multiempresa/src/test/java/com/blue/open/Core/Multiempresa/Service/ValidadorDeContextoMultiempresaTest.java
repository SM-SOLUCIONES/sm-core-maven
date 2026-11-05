package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica que un usuario solo opere en la empresa del header si pertenece a ella o a la empresa
 * de sistema. Portado de los casos valid_* de AuthServiceTest de Core 1.x.
 */
@ExtendWith(MockitoExtension.class)
class ValidadorDeContextoMultiempresaTest {

    private ValidadorDeContextoMultiempresa validador;

    @Mock
    private UsuarioEmpresaRepository usuarioEmpresaRepository;

    @Mock
    private ContextoDeEmpresa contextoDeEmpresa;

    private UsuarioBase usuario;

    @BeforeEach
    void preparar() {
        validador = new ValidadorDeContextoMultiempresa(usuarioEmpresaRepository, contextoDeEmpresa);
        usuario = new UsuarioBase() {
        };
        usuario.setId(7L);
        lenient().when(contextoDeEmpresa.getIdEmpresaDeSistema()).thenReturn(1L);
    }

    @Test
    void sinHeaderDeEmpresa_noValida() {
        when(contextoDeEmpresa.getIdEmpresaActualONull()).thenReturn(null);

        assertDoesNotThrow(() -> validador.validar(usuario));
        verifyNoInteractions(usuarioEmpresaRepository);
    }

    @Test
    void usuarioDeLaEmpresa_esValido() {
        when(contextoDeEmpresa.getIdEmpresaActualONull()).thenReturn(2L);
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(2L, 7L)).thenReturn(true);

        assertDoesNotThrow(() -> validador.validar(usuario));
    }

    @Test
    void usuarioDeLaEmpresaDeSistema_esValidoEnCualquierEmpresa() {
        when(contextoDeEmpresa.getIdEmpresaActualONull()).thenReturn(2L);
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(2L, 7L)).thenReturn(false);
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(1L, 7L)).thenReturn(true);

        assertDoesNotThrow(() -> validador.validar(usuario));
    }

    @Test
    void usuarioDeOtraEmpresa_lanza403() {
        when(contextoDeEmpresa.getIdEmpresaActualONull()).thenReturn(2L);
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(anyLong(), eq(7L))).thenReturn(false);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> validador.validar(usuario));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Empresa no permitida", ex.getMensajeParaUsuario());
    }
}
