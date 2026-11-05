package com.blue.open.Core.Integracion.Mail.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-mail: envio de emails.
@AllArgsConstructor
public enum CodigoDeErrorMail implements CodigoDeError {

    @Descripcion("La operación necesita enviar un email y el backend no tiene configurado el servidor de correo")
    MAIL_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Faltan configuraciones necesarias para el servicio de mail"),
    @Descripcion("La operación necesita enviar un email y el backend está configurado con un medio de envío (notificaciones) que todavía no está implementado")
    FUNCIONALIDAD_NO_IMPLEMENTADA(HttpStatus.NOT_IMPLEMENTED, "Funcionalidad no implementada");

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
