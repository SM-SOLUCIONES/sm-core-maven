package com.blue.open.Core.Protocolo.ApiKey;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Token de acceso personal (entidad AccessToken). Lo usan los endpoints de crear, listar y eliminar
 * tokens del usuario de la sesion (AccessTokenController).
 */
@Setter
@Getter
public class AccessTokenDTO extends EntidadBaseDTO {
    @Descripcion("Uuid del usuario dueño del token (solo lectura)")
    private String usuarioUuid;
    @Descripcion("Nombre con el que el usuario identifica el token. Único entre sus tokens vigentes")
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    @Descripcion("Valor secreto del token, que se envía como Bearer en el header Authorization. Se genera al crear "
            + "el token (solo lectura)")
    private String codigo;
    @Descripcion("1 si el token está habilitado, 0 si fue desactivado o eliminado (solo lectura)")
    private int activo;

    // Constructor que usa Jackson al recibir el DTO. Explicito: sin @JsonCreator, Jackson 3 toma el de la Proyeccion
    // como creator y un "activo" ausente llega como null a un int (400)
    @JsonCreator
    public AccessTokenDTO() {
    }

    // Proyeccion de AccessTokenRepository: arma el DTO desde la consulta (el usuarioUuid lo completa AuthTokenService)
    public AccessTokenDTO(String uuid, Integer status, Timestamp fechaDeCreacion, String nombre, String codigo, int activo) {
        this.uuid = uuid;
        this.status = status;
        this.fechaDeCreacion = fechaDeCreacion;
        this.nombre = nombre;
        this.codigo = codigo;
        this.activo = activo;
    }
}
