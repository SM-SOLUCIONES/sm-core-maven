package com.blue.open.Core.Kernel.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

// Unico objeto que se serializa como body de una respuesta de error. A diferencia de
// ExcepcionDeApi, nunca expone mensajeParaDesarrollador ni cause: eso queda solo en el log.
public class ErrorResponseDTO {
    @Descripcion("Fecha y hora del error")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/Argentina/Buenos_Aires")
    private final OffsetDateTime fechaHora;
    @Descripcion("Código estable del error: el front discrimina por este campo, nunca por mensajeParaUsuario. Ver el catálogo de errores")
    private final CodigoDeError codigo;
    @Descripcion("Status HTTP de la respuesta: código y nombre (ej. 400 BAD_REQUEST)")
    private final HttpStatus status;
    @Descripcion("Mensaje para el usuario final. Puede reformularse sin aviso")
    private final String mensajeParaUsuario;
    @Descripcion("Datos que el cliente necesita para resolver el error (ej. motivo). Se omite si está vacío")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Map<String, Object> datos;
    @Descripcion("Detalle por campo de un error de validación")
    private final List<SubError> errores;
    @Descripcion("Identificador de la traza en el log del servidor, para soporte")
    private final String traceId;

    private ErrorResponseDTO(ExcepcionDeApi excepcion) {
        this.fechaHora = excepcion.getFechaHora();
        this.codigo = excepcion.getCodigo();
        this.status = excepcion.getStatus();
        this.mensajeParaUsuario = excepcion.getMensajeParaUsuario();
        this.datos = excepcion.getDatos();
        this.errores = excepcion.getErrores();
        this.traceId = excepcion.getTraceId();
    }

    // Arma el body de error desde la excepcion, sin mensajeParaDesarrollador ni causa
    public static ErrorResponseDTO desde(ExcepcionDeApi excepcion) {
        return new ErrorResponseDTO(excepcion);
    }

    // Fecha y hora del error en GMT-3
    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    // Codigo estable del error
    public CodigoDeError getCodigo() {
        return codigo;
    }

    // Status HTTP de la respuesta
    public HttpStatus getStatus() {
        return status;
    }

    // Mensaje para el usuario final
    public String getMensajeParaUsuario() {
        return mensajeParaUsuario;
    }

    // Datos que el cliente necesita para resolver el error
    public Map<String, Object> getDatos() {
        return datos;
    }

    // Errores de validacion por campo
    public List<SubError> getErrores() {
        return errores;
    }

    // traceId del request, para buscarlo en el log
    public String getTraceId() {
        return traceId;
    }
}
