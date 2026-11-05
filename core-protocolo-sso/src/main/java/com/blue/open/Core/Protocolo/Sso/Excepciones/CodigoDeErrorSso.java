package com.blue.open.Core.Protocolo.Sso.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-protocolo-sso: SSO y autenticacion Malvinas.
@AllArgsConstructor
public enum CodigoDeErrorSso implements CodigoDeError {

    @Descripcion("El backend no tiene configurado el servicio externo de SSO. Es un error de configuración del backend")
    SSO_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Faltan configuraciones necesarias para el servicio SSO"),
    @Descripcion("El servicio de SSO no reconoce el token del header `Authorization` (en Malvinas, solo un token ES256): hay que volver a iniciar sesión en el SSO")
    SSO_AUTENTICACION_FALLIDA(HttpStatus.UNAUTHORIZED, "No se pudo autenticar con el sistema de usuarios"),
    @Descripcion("La autenticación por SSO se ejecutó fuera de un request HTTP, sin un token para resolver")
    SSO_CONTEXTO_NO_DISPONIBLE(HttpStatus.BAD_REQUEST, "Contexto de petición no disponible"),
    @Descripcion("Falló la comunicación con el servicio externo de SSO (no responde o responde con error). Se puede reintentar")
    SSO_INTEGRACION_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "Error en la comunicación con el sistema de usuarios");

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
