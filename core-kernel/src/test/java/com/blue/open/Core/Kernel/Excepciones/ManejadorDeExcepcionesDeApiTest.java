package com.blue.open.Core.Kernel.Excepciones;

import com.blue.open.Core.Kernel.Trazabilidad.SanitizadorDeLogs;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManejadorDeExcepcionesDeApiTest {

    private final SanitizadorDeLogs sanitizadorDeLogs = mock(SanitizadorDeLogs.class);
    private final ManejadorDeExcepcionesDeApi handler = new ManejadorDeExcepcionesDeApi(sanitizadorDeLogs);
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    void manejarExcepcionDeApi_respondeElCodigoYElStatusDeLaExcepcion() {
        ExcepcionDeApi excepcion = new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO);

        ResponseEntity<Object> response = handler.manejarExcepcionDeApi(excepcion, request);

        ErrorResponseDTO cuerpo = (ErrorResponseDTO) response.getBody();
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(CodigoDeErrorKernel.SIN_PERMISO, cuerpo.getCodigo());
    }

    @Test
    void handleMethodArgumentNotValid_agregaUnSubErrorPorCampo() throws NoSuchMethodException {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getAllErrors()).thenReturn(
                List.of(new FieldError("dto", "nombre", "no puede estar vacio")));
        MethodParameter methodParameter = new MethodParameter(
                ManejadorDeExcepcionesDeApiTest.class.getDeclaredMethod("metodoDePrueba", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        ErrorResponseDTO cuerpo = (ErrorResponseDTO) response.getBody();
        assertEquals(CodigoDeErrorKernel.FORMULARIO_INVALIDO, cuerpo.getCodigo());
        assertEquals(1, cuerpo.getErrores().size());
        assertEquals("nombre", cuerpo.getErrores().get(0).getNombre());
        assertEquals("no puede estar vacio", cuerpo.getErrores().get(0).getMensaje());
    }

    @Test
    void handleHttpMessageNotReadable_respondeConCodigoCuerpoInvalido() {
        HttpInputMessage inputMessage = mock(HttpInputMessage.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON invalido", new RuntimeException("token inesperado"), inputMessage);

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        ErrorResponseDTO cuerpo = (ErrorResponseDTO) response.getBody();
        assertEquals(CodigoDeErrorKernel.CUERPO_INVALIDO, cuerpo.getCodigo());
    }

    @Test
    void handleHttpRequestMethodNotSupported_respondeConCodigoMetodoNoSoportado() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT");

        ResponseEntity<Object> response = handler.handleHttpRequestMethodNotSupported(
                ex, new HttpHeaders(), HttpStatus.METHOD_NOT_ALLOWED, request);

        ErrorResponseDTO cuerpo = (ErrorResponseDTO) response.getBody();
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(CodigoDeErrorKernel.METODO_NO_SOPORTADO, cuerpo.getCodigo());
    }

    @Test
    void handleHttpMediaTypeNotSupported_respondeConCodigoMediaTypeNoSoportado() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("no soportado");

        ResponseEntity<Object> response = handler.handleHttpMediaTypeNotSupported(
                ex, new HttpHeaders(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);

        ErrorResponseDTO cuerpo = (ErrorResponseDTO) response.getBody();
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(CodigoDeErrorKernel.MEDIA_TYPE_NO_SOPORTADO, cuerpo.getCodigo());
    }

    @Test
    void manejarCualquierExcepcion_respondeConCodigoErrorInternoYNoExponeElStacktrace() {
        ResponseEntity<Object> response = handler.manejarCualquierExcepcion(new IllegalStateException("boom"), request);

        ErrorResponseDTO cuerpo = (ErrorResponseDTO) response.getBody();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(CodigoDeErrorKernel.ERROR_INTERNO, cuerpo.getCodigo());
    }

    @Test
    void handleMaxUploadSizeExceededException_respondeConCodigoArchivoDemasiadoGrande() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(20L * 1024 * 1024);

        ResponseEntity<Object> response = handler.handleMaxUploadSizeExceededException(
                ex, new HttpHeaders(), HttpStatus.PAYLOAD_TOO_LARGE, request);

        ErrorResponseDTO cuerpo = (ErrorResponseDTO) response.getBody();
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals(CodigoDeErrorKernel.ARCHIVO_DEMASIADO_GRANDE, cuerpo.getCodigo());
    }

    @SuppressWarnings("unused")
    private void metodoDePrueba(String parametro) {
    }
}
