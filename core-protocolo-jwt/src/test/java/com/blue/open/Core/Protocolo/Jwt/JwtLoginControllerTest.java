package com.blue.open.Core.Protocolo.Jwt;

import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Identity.Service.CookiesDeSesion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Verifica el Path de la cookie refresh_token: por defecto el context-path mas la ruta del controller, y la propiedad
 * core.protocolo.cookie.path-refresh-token cuando un proxy publica el backend con un prefijo.
 */
class JwtLoginControllerTest {

    private JwtLoginController controller;

    @BeforeEach
    void setUp() {
        controller = new JwtLoginController(mock(AuthService.class), mock(AuthJwtService.class),
                mock(CookiesDeSesion.class));
        ReflectionTestUtils.setField(controller, "ruta", "usuario");
        ReflectionTestUtils.setField(controller, "contextPath", "/api");
    }

    @Test
    void pathDelRefreshToken_porDefecto_esElContextPathMasLaRuta() {
        assertEquals("/api/usuario", controller.pathDelRefreshToken());
    }

    @Test
    void pathDelRefreshToken_sinContextPath_esLaRuta() {
        ReflectionTestUtils.setField(controller, "contextPath", "");

        assertEquals("/usuario", controller.pathDelRefreshToken());
    }

    @Test
    void pathDelRefreshToken_configurado_ganaSobreElCalculado() {
        ReflectionTestUtils.setField(controller, "pathRefreshToken", "proyecto/api/usuario/");

        assertEquals("/proyecto/api/usuario", controller.pathDelRefreshToken());
    }
}
