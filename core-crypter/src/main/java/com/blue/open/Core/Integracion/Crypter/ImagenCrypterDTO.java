package com.blue.open.Core.Integracion.Crypter;

import com.blue.open.Core.Integracion.Crypter.Excepciones.CodigoDeErrorCrypter;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * Respuesta del webservice Crypter al guardar (/document/putFile) u obtener (/document/getFiles) un archivo.
 * Lo usa solo CrypterService: no viaja en ningun endpoint del proyecto.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ImagenCrypterDTO {
    private Long id;
    private Long idLote;
    private Integer operation;
    private String name;
    private String parentKey;
    private String resourceKey;
    private String source;
    private String dest;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/Argentina/Buenos_Aires")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime timeInit;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/Argentina/Buenos_Aires")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime timeEnd;

    private Long idCrypt;
    private Integer result;
    private String errMessage;
    private Long duration;
    private Long fileSize;
    private Integer validation;

    // Serializa el DTO a JSON para incluirlo en el mensaje de log de un error.
    // Responde: el JSON; lanza ExcepcionDeApi con CRYPTER_NO_DISPONIBLE si no se puede serializar.
    public String getComoJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (JacksonException e) {
            throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error al procesar la respuesta del sistema de archivos", "ImagenCrypterDTO | getComoJson | error: " + e.getMessage());
        }
    }
}
