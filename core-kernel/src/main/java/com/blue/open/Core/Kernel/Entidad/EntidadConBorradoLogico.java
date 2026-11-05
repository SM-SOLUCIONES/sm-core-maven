package com.blue.open.Core.Kernel.Entidad;

import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Timestamp;
import java.sql.Types;

/**
 * Entidad con borrado logico: agrega status y la fecha/usuario de borrado sobre {@link EntidadBase}.
 * Una entidad que extiende esta clase nunca se borra fisicamente: el Core la marca con status = -1.
 * Si una entidad NO extiende esta clase, el Core le aplica borrado fisico (DELETE real).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadConBorradoLogico extends EntidadBase {

    // Estado del registro: 0 activo, -1 eliminado (soft delete), -2 temporal (carga en varios pasos)
    @Column(name = "status", nullable = false)
    @JdbcTypeCode(Types.TINYINT)
    protected Integer status = 0;

    @JsonIgnore
    protected Long idUsuarioDeBorrado;

    @Column(name = "fecha_de_borrado")
    protected Timestamp fechaDeBorrado;

    // Antes de insertar: deja el status en 0 (activo) si viene en null
    @PrePersist
    protected void alCrearStatus() {
        if (this.status == null) {
            this.status = 0;
        }
    }

    @Override
    public void aplicarCamposBase(EntidadBaseDTO dto) {
        final int ACTIVE = 0;
        final int DELETED = -1;

        if (this.status == ACTIVE && dto.getStatus() == DELETED) {
            this.status = DELETED;
        } else if (this.status == DELETED && dto.getStatus() == ACTIVE) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.BORRADO_LOGICO_TRANSICION_INVALIDA,
                    "No se puede activar un elemento que fue eliminado",
                    "EntidadConBorradoLogico | aplicarCamposBase | error uuid: " + uuid);
        } else if (dto.getStatus() != ACTIVE && dto.getStatus() != DELETED) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.BORRADO_LOGICO_TRANSICION_INVALIDA, "Error status",
                    "EntidadConBorradoLogico | aplicarCamposBase | error status: " + dto.getStatus());
        }
    }
}
