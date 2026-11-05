package com.blue.open.Core.Kernel.Excepciones;

import com.blue.open.Core.Kernel.Trazabilidad.SanitizadorDeLogs;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

// Convierte toda excepcion de un endpoint en la respuesta de error de la API (ErrorResponseDTO) y la loguea
@Slf4j
@ControllerAdvice
public class ManejadorDeExcepcionesDeApi extends ResponseEntityExceptionHandler {

    private final SanitizadorDeLogs sanitizadorDeLogs;

    // Recibe el SanitizadorDeLogs con el que se tapan los datos sensibles antes de loguear
    public ManejadorDeExcepcionesDeApi(SanitizadorDeLogs sanitizadorDeLogs) {
        this.sanitizadorDeLogs = sanitizadorDeLogs;
    }

    // Convierte una ExcepcionDeApi lanzada por un endpoint en la respuesta de error con su status y codigo.
    // Recibe: la excepcion y el request. Responde: ErrorResponseDTO (y loguea el error sanitizado).
    @ExceptionHandler(ExcepcionDeApi.class)
    public ResponseEntity<Object> manejarExcepcionDeApi(ExcepcionDeApi ex, WebRequest request) {
        return construirRespuestaDeError(ex, new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {

        ExcepcionDeApi excepcionDeApi = new ExcepcionDeApi(CodigoDeErrorKernel.FORMULARIO_INVALIDO);
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String nombreDelCampo = ((FieldError) error).getField();
            excepcionDeApi.agregarSubError(nombreDelCampo, error.getDefaultMessage());
        });
        return construirRespuestaDeError(excepcionDeApi, new HttpHeaders(), request);
    }

    /*
     * Validacion de parametros del metodo (por ejemplo List<@Valid DTO> en el body): Spring no la reporta como
     * MethodArgumentNotValidException. Responde FORMULARIO_INVALIDO con un subError por campo; en un elemento de una
     * lista, el nombre lleva su indice ("[0].value").
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {

        ExcepcionDeApi excepcionDeApi = new ExcepcionDeApi(CodigoDeErrorKernel.FORMULARIO_INVALIDO);
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            String prefijo = resultado.getContainerIndex() != null ? "[" + resultado.getContainerIndex() + "]." : "";
            resultado.getResolvableErrors().forEach(error -> {
                String nombre = error instanceof FieldError fieldError
                        ? prefijo + fieldError.getField()
                        : resultado.getMethodParameter().getParameterName();
                excepcionDeApi.agregarSubError(nombre, error.getDefaultMessage());
            });
        }
        return construirRespuestaDeError(excepcionDeApi, new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ExcepcionDeApi excepcionDeApi = new ExcepcionDeApi(CodigoDeErrorKernel.CUERPO_INVALIDO,
                "ManejadorDeExcepcionesDeApi | handleHttpMessageNotReadable | " + ex.getMostSpecificCause().getMessage());

        return construirRespuestaDeError(excepcionDeApi, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ExcepcionDeApi excepcionDeApi = new ExcepcionDeApi(CodigoDeErrorKernel.METODO_NO_SOPORTADO,
                "ManejadorDeExcepcionesDeApi | handleHttpRequestMethodNotSupported | " + ex.getMessage());

        return construirRespuestaDeError(excepcionDeApi, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ExcepcionDeApi excepcionDeApi = new ExcepcionDeApi(CodigoDeErrorKernel.MEDIA_TYPE_NO_SOPORTADO,
                "ManejadorDeExcepcionesDeApi | handleHttpMediaTypeNotSupported | " + ex.getMessage());

        return construirRespuestaDeError(excepcionDeApi, headers, request);
    }

    // Un archivo supera spring.servlet.multipart.max-file-size o max-request-size: responde ARCHIVO_DEMASIADO_GRANDE (413).
    // Se sobreescribe el metodo de la clase base: un @ExceptionHandler para la misma excepcion seria ambiguo al arrancar.
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ExcepcionDeApi excepcionDeApi = new ExcepcionDeApi(CodigoDeErrorKernel.ARCHIVO_DEMASIADO_GRANDE,
                "ManejadorDeExcepcionesDeApi | handleMaxUploadSizeExceededException | maxUploadSize: " + ex.getMaxUploadSize());

        return construirRespuestaDeError(excepcionDeApi, headers, request);
    }

    // Red de seguridad: cualquier excepcion no prevista responde ERROR_INTERNO, con el detalle solo en el log.
    // Recibe: la excepcion y el request. Responde: ErrorResponseDTO con status 500.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> manejarCualquierExcepcion(Exception ex, WebRequest request) {
        ExcepcionDeApi excepcionDeApi = new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                ExcepcionUtils.construirMensajeParaDesarrollador("ManejadorDeExcepcionesDeApi.manejarCualquierExcepcion", ex));
        return construirRespuestaDeError(excepcionDeApi, new HttpHeaders(), request);
    }

    private ResponseEntity<Object> construirRespuestaDeError(ExcepcionDeApi error, HttpHeaders headers, WebRequest request) {
        HttpStatus status = error.getStatus();
        log.warn("Respuesta de error al front | request={} | payload={} | status={} | codigo={} | mensajeParaUsuario={} | "
                        + "mensajeParaDesarrollador={} | datos={} | errores={}",
                request.getDescription(false),
                getCuerpoDelRequest(request),
                status,
                error.getCodigo().codigo(),
                error.getMensajeParaUsuario(),
                error.getMensajeParaDesarrollador(),
                sanitizadorDeLogs.sanitizarValor(error.getDatos(), null),
                sanitizadorDeLogs.sanitizarValor(error.getErrores(), null));
        return new ResponseEntity<>(ErrorResponseDTO.desde(error), headers, status);
    }

    private String getCuerpoDelRequest(WebRequest webRequest) {
        if (!(webRequest instanceof ServletWebRequest servletWebRequest)) {
            return "";
        }

        HttpServletRequest request = servletWebRequest.getRequest();
        if (!(request instanceof ContentCachingRequestWrapper requestCacheado)) {
            return "";
        }

        byte[] contenido = requestCacheado.getContentAsByteArray();
        if (contenido.length == 0) {
            return "";
        }

        Charset charset = request.getCharacterEncoding() != null
                ? Charset.forName(request.getCharacterEncoding())
                : StandardCharsets.UTF_8;
        String cuerpo = new String(contenido, charset);
        return sanitizadorDeLogs.sanitizarCuerpo(cuerpo, request.getContentType());
    }
}
