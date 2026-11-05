package com.blue.open.Core.Multiempresa.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeError;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-multiempresa: contexto de empresa, roles y registro publico.
@AllArgsConstructor
public enum CodigoDeErrorMultiempresa implements CodigoDeError {

    @Descripcion("El usuario no pertenece a la empresa indicada en el header `X-Empresa-Uuid`")
    EMPRESA_NO_PERMITIDA(HttpStatus.FORBIDDEN, "Empresa no permitida"),
    @Descripcion("La operación necesita la empresa actual y el request no trae el header `X-Empresa-Uuid`")
    COMPANY_REQUERIDA(HttpStatus.BAD_REQUEST, "Empresa requerida"),
    @Descripcion("El uuid de empresa (del header `X-Empresa-Uuid` o del request) no corresponde a una empresa activa")
    COMPANY_NO_ENCONTRADA(HttpStatus.BAD_REQUEST, "Empresa no encontrada"),
    @Descripcion("Al guardar un rol, ya existe otro con el mismo nombre en la misma empresa")
    ROL_YA_EXISTE(HttpStatus.BAD_REQUEST, "Ya existe un rol con ese nombre para su empresa"),
    @Descripcion("En el registro público en una empresa, esa empresa no tiene configurado un rol por defecto para los usuarios nuevos")
    CONFIGURACION_INCOMPLETA(HttpStatus.BAD_REQUEST, "Configuración incompleta"),
    @Descripcion("En el registro público de usuario y empresa, falta en el sistema el rol ADMIN que se le asigna al usuario. Es un error de configuración del backend")
    CONFIGURACION_DE_SISTEMA_INCOMPLETA(HttpStatus.INTERNAL_SERVER_ERROR, "Configuración incompleta"),
    @Descripcion("En el registro público de un cliente o al vincularse a una empresa, la empresa indicada en el header `X-Empresa-Uuid` es la de sistema, cuyos roles valen en todas las empresas: nadie entra a ella por esas vías, ni siquiera un SYSADMIN. Hay que elegir otra empresa")
    EMPRESA_DE_SISTEMA_NO_PERMITIDA(HttpStatus.BAD_REQUEST, "No es posible registrarse en la empresa de sistema");

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
