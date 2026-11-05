package com.blue.open.Core.Crud.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-crud: el ABM de AbstractService (obtener, paginar, guardar, borrar).
@AllArgsConstructor
public enum CodigoDeErrorCrud implements CodigoDeError {

    @Descripcion("El registro pedido (por el uuid del path u otro identificador del request) no existe o ya fue eliminado")
    REGISTRO_NO_ENCONTRADO(HttpStatus.BAD_REQUEST, "No existe el registro"),

    @Descripcion("Al guardar, el uuid de una relación (campo `{relacion}Uuid` del DTO) no corresponde a un registro vigente. `datos.campo` trae el nombre de ese campo")
    RELACION_NO_ENCONTRADA(HttpStatus.BAD_REQUEST, "No existe el registro relacionado indicado");

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
