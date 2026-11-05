package com.blue.open.Core.Kernel.Excepciones;

import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;
import com.blue.open.Core.Kernel.Trazabilidad.SanitizadorDeLogs;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Excepcion unica para todo error de negocio. El codigo (CodigoDeError) identifica la situacion
// de forma estable; el texto de mensajeParaUsuario/mensajeParaDesarrollador puede cambiar sin romper al cliente.
// mensajeParaDesarrollador y causa nunca se serializan al cliente: son solo para el log (ver ErrorResponseDTO).
@Slf4j
public class ExcepcionDeApi extends RuntimeException {
    // OffsetDateTime y no LocalDateTime: el patron lleva offset y un LocalDateTime no lo tiene,
    // lo que hacia fallar la serializacion de toda respuesta de error (Core <= 2.0.0)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/Argentina/Buenos_Aires")
    private final OffsetDateTime fechaHora;
    private final CodigoDeError codigo;
    private final HttpStatus status;
    private final String mensajeParaUsuario;
    private final String mensajeParaDesarrollador;
    private List<SubError> errores;
    private final Map<String, Object> datos;
    private final String traceId;

    // Error con el mensajeParaUsuario por defecto del codigo y sin detalle para el log
    public ExcepcionDeApi(CodigoDeError codigo) {
        this(codigo, codigo.mensajePorDefecto(), null, null);
    }

    // Error con el mensajeParaUsuario por defecto del codigo. mensajeParaDesarrollador solo va al log
    public ExcepcionDeApi(CodigoDeError codigo, String mensajeParaDesarrollador) {
        this(codigo, codigo.mensajePorDefecto(), mensajeParaDesarrollador, null);
    }

    // Error con el mensajeParaUsuario por defecto del codigo. mensajeParaDesarrollador solo va al log
    // y causa es la excepcion original, tambien solo para el log
    public ExcepcionDeApi(CodigoDeError codigo, String mensajeParaDesarrollador, Throwable causa) {
        this(codigo, codigo.mensajePorDefecto(), mensajeParaDesarrollador, causa);
    }

    // Error con un mensajeParaUsuario propio en lugar del por defecto del codigo. mensajeParaDesarrollador solo va al log
    public ExcepcionDeApi(CodigoDeError codigo, String mensajeParaUsuario, String mensajeParaDesarrollador) {
        this(codigo, mensajeParaUsuario, mensajeParaDesarrollador, null);
    }

    // Constructor comun: toma el status del codigo, la fecha y hora actual en GMT-3 y el traceId del MDC
    private ExcepcionDeApi(CodigoDeError codigo, String mensajeParaUsuario, String mensajeParaDesarrollador, Throwable causa) {
        super(mensajeParaUsuario, causa);
        this.errores = new ArrayList<>();
        this.datos = new LinkedHashMap<>();
        this.fechaHora = FechaHoraConfig.ahoraLocalDateTime().atZone(FechaHoraConfig.ZONA).toOffsetDateTime();
        this.traceId = MDC.get("traceId");
        this.codigo = codigo;
        this.status = codigo.status();
        this.mensajeParaUsuario = mensajeParaUsuario;
        this.mensajeParaDesarrollador = mensajeParaDesarrollador;
    }

    // Agrega un dato que el cliente necesita para resolver el error. Ignora valores null (la
    // clave ausente significa que el backend no lo pudo informar) y descarta, con un warning en
    // el log, cualquier clave sensible segun el criterio de SanitizadorDeLogs.
    public ExcepcionDeApi conDato(String clave, Object valor) {
        if (valor == null) {
            return this;
        }
        if (SanitizadorDeLogs.esClaveSensible(clave)) {
            log.warn("Se descarto un dato sensible pasado a ExcepcionDeApi.conDato | codigo={} | clave={}",
                    codigo.codigo(), clave);
            return this;
        }
        this.datos.put(clave, valor);
        return this;
    }

    // Agrega un error de validacion de un campo a errores, que viaja en la respuesta.
    // Recibe: el nombre del campo y el mensaje para el usuario final.
    public void agregarSubError(String nombre, String mensaje) {
        this.errores.add(new SubError(nombre, mensaje));
    }

    // Fecha y hora del error en GMT-3
    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    // Codigo estable del error
    public CodigoDeError getCodigo() {
        return codigo;
    }

    // Status HTTP, tomado del codigo
    public HttpStatus getStatus() {
        return status;
    }

    // Mensaje para el usuario final
    public String getMensajeParaUsuario() {
        return mensajeParaUsuario;
    }

    // Detalle interno: solo se loguea, nunca viaja en la respuesta
    public String getMensajeParaDesarrollador() {
        return mensajeParaDesarrollador;
    }

    // Errores de validacion por campo
    public List<SubError> getErrores() {
        return errores;
    }

    // Datos que el cliente necesita para resolver el error
    public Map<String, Object> getDatos() {
        return datos;
    }

    // traceId del request en el que ocurrio el error
    public String getTraceId() {
        return traceId;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return null;
    }
}
