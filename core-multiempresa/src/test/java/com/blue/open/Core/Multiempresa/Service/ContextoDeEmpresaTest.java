package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Repository.EmpresaRepositorio;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica la resolucion de la empresa de la peticion por el header X-Empresa-Uuid.
 * Portado de los casos getCurrentCompanyOrThrow_* de AuthServiceTest de Core 1.x.
 */
@ExtendWith(MockitoExtension.class)
class ContextoDeEmpresaTest {

    private ContextoDeEmpresa contexto;

    @Mock
    private EmpresaRepositorio empresaRepositorio;

    @Mock
    private UsuarioEmpresaRepository usuarioEmpresaRepository;

    @Mock
    private AuthService authService;

    private MockHttpServletRequest request;
    private UsuarioBase usuario;
    private EmpresaBase cliente;

    @BeforeEach
    void preparar() {
        // Las dependencias entran por constructor; lo que queda por reflexion son los @Value
        contexto = new ContextoDeEmpresa(empresaRepositorio, usuarioEmpresaRepository, authService);
        ReflectionTestUtils.setField(contexto, "multiempresaHabilitada", true);
        ReflectionTestUtils.setField(contexto, "uuidEmpresaPorDefecto", "2");
        ReflectionTestUtils.setField(contexto, "uuidEmpresaDeSistema", "1");

        usuario = new UsuarioBase() {
        };
        usuario.setId(7L);
        cliente = empresa(2L, "cliente-uuid");

        lenient().when(authService.autenticar()).thenReturn(usuario);
        lenient().when(empresaRepositorio.porUuid("1")).thenReturn(Optional.of(empresa(1L, "1")));
        lenient().when(empresaRepositorio.porUuidActiva("cliente-uuid")).thenReturn(Optional.of(cliente));

        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private EmpresaBase empresa(Long id, String uuid) {
        EmpresaBase empresa = new EmpresaBase() {
        };
        empresa.setId(id);
        empresa.setUuid(uuid);
        return empresa;
    }

    @Test
    void sinHeader_lanza400() {
        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, contexto::getEmpresaActualObligatoria);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMensajeParaDesarrollador().contains(ContextoDeEmpresa.ENCABEZADO_EMPRESA));
    }

    @Test
    void sinHeaderEnMonoEmpresa_usaLaEmpresaPorDefecto() {
        ReflectionTestUtils.setField(contexto, "multiempresaHabilitada", false);
        assertEquals("2", contexto.getUuidDeLaRequest());
    }

    @Test
    void empresaPropia_laDevuelve() {
        request.addHeader(ContextoDeEmpresa.ENCABEZADO_EMPRESA, "cliente-uuid");
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(2L, 7L)).thenReturn(true);

        assertSame(cliente, contexto.getEmpresaActualObligatoria());
    }

    @Test
    void usuarioDeSistemaEnEmpresaNoVinculada_laDevuelve() {
        request.addHeader(ContextoDeEmpresa.ENCABEZADO_EMPRESA, "cliente-uuid");
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(2L, 7L)).thenReturn(false);
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(1L, 7L)).thenReturn(true);

        assertSame(cliente, contexto.getEmpresaActualObligatoria());
    }

    @Test
    void empresaAjena_lanza403() {
        request.addHeader(ContextoDeEmpresa.ENCABEZADO_EMPRESA, "cliente-uuid");
        when(usuarioEmpresaRepository.existeUsuarioEnEmpresa(anyLong(), eq(7L))).thenReturn(false);

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, contexto::getEmpresaActualObligatoria);
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void empresaInexistente_lanza400() {
        request.addHeader(ContextoDeEmpresa.ENCABEZADO_EMPRESA, "no-existe");

        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, contexto::getEmpresaActualObligatoria);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
