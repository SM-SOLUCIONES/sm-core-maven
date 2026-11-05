package com.blue.open.Core.Kernel.Contrato;

import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Puertos.AutorizadorDeEndpoint;
import com.blue.open.Core.Kernel.Puertos.AutorizadorDenegarTodo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Prueba InterceptorDeContrato (autoriza con el permiso de @Contrato antes de ejecutar el endpoint),
 * VerificadorDeContratos (corta el arranque ante un @Contrato mal declarado) y AutorizadorDenegarTodo.
 */
class InterceptorDeContratoTest {

    // Controller de prueba: vive en com.blue.open, asi que el interceptor y el verificador lo consideran del proyecto
    static class ControllerDePrueba {

        @Contrato(casoDeUso = "CU-T-01", resumen = "Publico", permiso = Contrato.PUBLICO)
        public void publico() {
        }

        @Contrato(casoDeUso = "CU-T-02", resumen = "Con permiso", permiso = { "PERMISO_A", "ROL_B" })
        public void conPermiso() {
        }

        public void sinContrato() {
        }

        @Contrato(casoDeUso = "CU-T-03", resumen = "Vacio", permiso = {})
        public void permisoVacio() {
        }

        @Contrato(casoDeUso = "CU-T-04", resumen = "Combinado", permiso = { Contrato.AUTENTICADO, "PERMISO_A" })
        public void combinado() {
        }

        @Contrato(casoDeUso = "CU-T-05", resumen = "Inexistente", permiso = "PERMISO_INVENTADO")
        public void inexistente() {
        }
    }

    private AutorizadorDeEndpoint autorizador;

    private InterceptorDeContrato interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        autorizador = mock(AutorizadorDeEndpoint.class);
        ObjectProvider<AutorizadorDeEndpoint> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenReturn(autorizador);
        interceptor = new InterceptorDeContrato(proveedor);
    }

    private static HandlerMethod metodo(String nombre) throws NoSuchMethodException {
        return new HandlerMethod(new ControllerDePrueba(), ControllerDePrueba.class.getMethod(nombre));
    }

    private boolean preHandle(Object handler) {
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    @Test
    void conContrato_delegaElPermisoEnElAutorizador() throws Exception {
        assertTrue(preHandle(metodo("conPermiso")));
        verify(autorizador).autorizar(new String[] { "PERMISO_A", "ROL_B" });
    }

    @Test
    void autorizadorRechaza_propagaElError() throws Exception {
        doThrow(new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO)).when(autorizador).autorizar(any());
        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> preHandle(metodo("conPermiso")));
        assertEquals(CodigoDeErrorKernel.SIN_PERMISO, ex.getCodigo());
    }

    @Test
    void sinContrato_respondeSinPermiso() throws Exception {
        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class, () -> preHandle(metodo("sinContrato")));
        assertEquals(CodigoDeErrorKernel.SIN_PERMISO, ex.getCodigo());
        verifyNoInteractions(autorizador);
    }

    @Test
    void handlerQueNoEsUnMetodo_noSeAutoriza() {
        assertTrue(preHandle(new Object()));
        verifyNoInteractions(autorizador);
    }

    @Test
    void verificador_listaCadaContratoMalDeclarado() throws Exception {
        List<String> errores = VerificadorDeContratos.verificar(List.of(metodo("publico"), metodo("conPermiso"),
                metodo("sinContrato"), metodo("permisoVacio"), metodo("combinado"), metodo("inexistente")),
                Set.of("PERMISO_A", "ROL_B"));

        assertEquals(4, errores.size(), errores::toString);
        assertTrue(errores.get(0).startsWith("ControllerDePrueba.combinado: PUBLICO y AUTENTICADO no se combinan"));
        assertTrue(errores.get(1).startsWith("ControllerDePrueba.inexistente: roles/permisos inexistentes"));
        assertTrue(errores.get(2).startsWith("ControllerDePrueba.permisoVacio: permiso vacio"));
        assertTrue(errores.get(3).startsWith("ControllerDePrueba.sinContrato: no tiene @Contrato"));
    }

    @Test
    void verificador_sinNombresValidos_noValidaLosNombres() throws Exception {
        assertTrue(VerificadorDeContratos.verificar(List.of(metodo("inexistente")), Set.of()).isEmpty());
    }

    @Test
    void denegarTodo_soloDejaPasarLoPublico() {
        AutorizadorDenegarTodo denegarTodo = new AutorizadorDenegarTodo();
        assertDoesNotThrow(() -> denegarTodo.autorizar(new String[] { Contrato.PUBLICO }));
        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> denegarTodo.autorizar(new String[] { Contrato.AUTENTICADO }));
        assertEquals(CodigoDeErrorKernel.NO_AUTENTICADO, ex.getCodigo());
    }

    @Test
    void denegarTodo_conPermiso_respondeNoAutenticado() {
        // Sin core-identity ninguna request identifica a un usuario: es falta de sesion, no de permiso
        ExcepcionDeApi ex = assertThrows(ExcepcionDeApi.class,
                () -> new AutorizadorDenegarTodo().autorizar(new String[] { "PERMISO_A" }));
        assertEquals(CodigoDeErrorKernel.NO_AUTENTICADO, ex.getCodigo());
    }
}
