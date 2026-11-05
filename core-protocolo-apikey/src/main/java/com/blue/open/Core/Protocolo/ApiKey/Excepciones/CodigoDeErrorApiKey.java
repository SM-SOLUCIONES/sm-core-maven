package com.blue.open.Core.Protocolo.ApiKey.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-protocolo-apikey: tokens de acceso personal.
@AllArgsConstructor
public enum CodigoDeErrorApiKey implements CodigoDeError {

    @Descripcion("El token de acceso personal del header `Authorization` no existe, fue desactivado o eliminado")
    ACCESS_TOKEN_INVALIDO(HttpStatus.UNAUTHORIZED, "Token de acceso personal inválido"),
    @Descripcion("Al crear un token de acceso personal, el usuario ya tiene otro activo con el mismo nombre")
    TOKEN_NOMBRE_DUPLICADO(HttpStatus.BAD_REQUEST, "Ya existe un token con ese nombre");

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
