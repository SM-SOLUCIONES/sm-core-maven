package com.blue.open.Core.Identity.Configuracion;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.PoliticaDeAccesoExterno;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifica los valores por defecto de core-identity que cambian el comportamiento de la autenticacion: la
 * PoliticaDeAccesoExterno por defecto deja entrar a cualquier identidad externa, como hasta 3.3.2.
 */
class IdentityConfiguracionTest {

    @Test
    void politicaDeAccesoExternoPorDefecto_noValidaNada() {
        PoliticaDeAccesoExterno politica = new IdentityConfiguracion().politicaDeAccesoExternoPermitirTodo();
        UsuarioBase vigente = new UsuarioBase() {
        };

        assertDoesNotThrow(() -> politica.validar("malvinas", "12345", null));
        assertDoesNotThrow(() -> politica.validar("malvinas", "12345", vigente));
    }
}
