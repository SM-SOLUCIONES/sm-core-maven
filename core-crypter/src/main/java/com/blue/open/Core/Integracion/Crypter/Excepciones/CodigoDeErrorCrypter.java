package com.blue.open.Core.Integracion.Crypter.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-crypter: guardado/obtencion de archivos encriptados.
@AllArgsConstructor
public enum CodigoDeErrorCrypter implements CodigoDeError {

    @Descripcion("No se pudo guardar u obtener un archivo encriptado: el servicio externo de archivos no está configurado o no responde")
    CRYPTER_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Error con el servicio de archivos encriptados");

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
