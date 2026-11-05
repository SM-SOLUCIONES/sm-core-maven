package com.blue.open.Core.Kernel.Excepciones;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

// Codigos de error propios de core-kernel: paginado, borrado logico, permisos y las
// respuestas genericas del handler (formulario, cuerpo, metodo/media-type no soportados, error interno).
@AllArgsConstructor
public enum CodigoDeErrorKernel implements CodigoDeError {

    @Descripcion("La consulta de un paginado no se puede resolver: un campo de filtro u orden que no existe o no se puede filtrar, un operador inválido o un valor que no corresponde al tipo del campo")
    PAGINADO_INVALIDO(HttpStatus.BAD_REQUEST, "La consulta de paginado no es valida"),
    @Descripcion("Al guardar un registro con un `status` no permitido: reactivar (0) un registro eliminado (-1), o enviar un valor distinto de 0 y -1")
    BORRADO_LOGICO_TRANSICION_INVALIDA(HttpStatus.BAD_REQUEST, "No se puede aplicar ese cambio de estado"),
    @Descripcion("La request no identifica a ningún usuario (no envió credenciales) y el endpoint exige sesión: hay que iniciar sesión")
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Usuario no identificado"),
    @Descripcion("El usuario autenticado no tiene el rol o permiso que exige el endpoint, o no puede operar sobre ese registro o esa empresa en particular")
    SIN_PERMISO(HttpStatus.FORBIDDEN, "No tenes permiso para realizar esta accion"),
    @Descripcion("El body no pasa las validaciones del DTO. `errores` trae un elemento por campo inválido, con su nombre y su mensaje")
    FORMULARIO_INVALIDO(HttpStatus.BAD_REQUEST, "Error en el formulario"),
    @Descripcion("El body no se puede leer: JSON malformado, o un valor cuyo tipo no corresponde al del campo")
    CUERPO_INVALIDO(HttpStatus.BAD_REQUEST, "Error con los datos enviados"),
    @Descripcion("El endpoint existe pero no acepta el método HTTP usado")
    METODO_NO_SOPORTADO(HttpStatus.METHOD_NOT_ALLOWED, "Metodo HTTP no soportado"),
    @Descripcion("El endpoint no acepta el `Content-Type` del request")
    MEDIA_TYPE_NO_SOPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado"),
    @Descripcion("Un archivo del request (o el request multipart completo) supera el tamaño máximo que acepta el servidor")
    ARCHIVO_DEMASIADO_GRANDE(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo permitido"),
    @Descripcion("Error inesperado del servidor, que no corresponde a ningún otro código. El `traceId` de la respuesta permite ubicarlo en los logs")
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");

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
