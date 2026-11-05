package com.blue.open.Core.Integracion.Captcha.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-captcha: verificacion de hCaptcha.
@AllArgsConstructor
public enum CodigoDeErrorCaptcha implements CodigoDeError {

    @Descripcion("La operación verifica un captcha y el backend no tiene configurada la verificación de hCaptcha. Es un error de configuración del backend")
    CAPTCHA_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Faltan configuraciones necesarias para el servicio de captcha"),
    @Descripcion("La operación verifica un captcha y el request no trae el token de hCaptcha")
    CAPTCHA_NO_COMPLETADO(HttpStatus.BAD_REQUEST, "Captcha no completado"),
    @Descripcion("hCaptcha rechazó el token enviado, o no se pudo verificar con el servicio externo. Hay que resolver el captcha de nuevo")
    CAPTCHA_INVALIDO(HttpStatus.BAD_REQUEST, "Verificación de Captcha fallida");

    private final HttpStatus status;
    private final String mensajePorDefecto;

    @Override
    public String codigo() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String mensajePorDefecto() {
        return mensajePorDefecto;
    }
}
