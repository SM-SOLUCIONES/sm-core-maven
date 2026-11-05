package com.blue.open.Core.Identity.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-identity: login, verificacion por codigo y recuperacion de contrasena.
@AllArgsConstructor
public enum CodigoDeErrorIdentity implements CodigoDeError {

    @Descripcion("Login con usuario o contraseña incorrectos. No distingue si el usuario no existe o si la contraseña no coincide")
    CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos"),
    @Descripcion("Al body le falta un dato obligatorio del flujo de verificación, recuperación de contraseña o registro público (uuid, código, email o los datos de la empresa)")
    DATOS_INCOMPLETOS(HttpStatus.BAD_REQUEST, "Datos incompletos"),
    @Descripcion("El código de verificación o de recuperación de contraseña enviado no coincide con el generado")
    CODIGO_INVALIDO(HttpStatus.BAD_REQUEST, "Código inválido"),
    @Descripcion("El código de recuperación de contraseña ya fue usado: hay que pedir uno nuevo")
    CODIGO_YA_UTILIZADO(HttpStatus.BAD_REQUEST, "El código ya fue utilizado"),
    @Descripcion("El código de verificación o de recuperación de contraseña venció: hay que pedir uno nuevo")
    CODIGO_VENCIDO(HttpStatus.BAD_REQUEST, "El código ha expirado"),
    @Descripcion("Se intenta verificar el código de un usuario que no está pendiente de verificación ni activo (por ejemplo, fue eliminado)")
    USUARIO_NO_PENDIENTE_VERIFICACION(HttpStatus.BAD_REQUEST, "El usuario no está pendiente de verificación"),
    @Descripcion("Se pidió un nuevo código de verificación o de recuperación antes de que termine el tiempo de espera desde el anterior")
    DEMASIADOS_INTENTOS(HttpStatus.TOO_MANY_REQUESTS, "Por favor espere unos minutos antes de reintentar"),
    @Descripcion("El flujo necesita enviar un código por email y el backend no tiene el envío de emails habilitado")
    SERVICIO_DE_MAIL_NO_DISPONIBLE(HttpStatus.INTERNAL_SERVER_ERROR, "No se pueden enviar emails"),
    @Descripcion("El backend no tiene implementado el contenido del email del flujo (registro o recuperación de contraseña). Es un error de configuración del backend")
    HOOK_NO_IMPLEMENTADO(HttpStatus.INTERNAL_SERVER_ERROR, "No implementado"),
    @Descripcion("El request se autentica con una cookie de sesión (`access_token` o `refresh_token`) y modifica datos (POST, PUT, PATCH o DELETE) desde un origen que no es el mismo host del backend ni está en `core.protocolo.cookie.origenes-permitidos`. Con el header `Authorization` no ocurre")
    ORIGEN_NO_PERMITIDO(HttpStatus.FORBIDDEN, "Origen no permitido");

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
