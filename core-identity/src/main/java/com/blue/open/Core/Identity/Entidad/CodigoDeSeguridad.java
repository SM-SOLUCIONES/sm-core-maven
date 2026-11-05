package com.blue.open.Core.Identity.Entidad;

import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Codigo de seguridad de un solo uso (ej. recuperacion de contrasena).
 * Se vincula al usuario por id (columna id_usuario) y no por asociacion JPA: el Core no conoce la
 * clase concreta de usuario del proyecto. Un usuario puede tener varios codigos historicos;
 * solo el ultimo no usado y vigente es valido.
 */
@Entity
@Getter
@Setter
@Table(name = "codigos_de_seguridad")
public class CodigoDeSeguridad extends EntidadConBorradoLogico {

    private String codigo;

    // Fecha en que se emitio el codigo. No se llama fechaDeCreacion para no chocar con la auditoria
    private LocalDateTime fechaDeEmision;

    // Motivo del codigo (ej. "FORGOT_PASSWORD", ver UsuarioBaseService.PROPOSITO_*), distingue para que flujo fue emitido
    private String proposito;

    private Boolean usado = false;

    @Column(name = "id_usuario")
    private Long idUsuario;
}
