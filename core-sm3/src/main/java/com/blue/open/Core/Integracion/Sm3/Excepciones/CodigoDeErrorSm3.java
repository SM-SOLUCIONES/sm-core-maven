package com.blue.open.Core.Integracion.Sm3.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-sm3: subida y borrado de archivos publicos.
@AllArgsConstructor
public enum CodigoDeErrorSm3 implements CodigoDeError {

    @Descripcion("El backend no tiene configurado el servidor de archivos públicos (SM3). Es un error de configuración del backend")
    SM3_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Faltan configuraciones necesarias para el servicio SM3"),
    @Descripcion("Falló la subida del archivo al servidor de archivos públicos (SM3). Se puede reintentar")
    ERROR_SUBIENDO_ARCHIVO(HttpStatus.SERVICE_UNAVAILABLE, "Error subiendo archivo al servidor de imagenes"),
    @Descripcion("Falló el borrado del archivo en el servidor de archivos públicos (SM3). Se puede reintentar")
    ERROR_ELIMINANDO_ARCHIVO(HttpStatus.SERVICE_UNAVAILABLE, "Error eliminando archivo en el servidor de imágenes");

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
