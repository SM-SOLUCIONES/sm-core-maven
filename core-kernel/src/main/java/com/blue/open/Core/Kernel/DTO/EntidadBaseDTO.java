package com.blue.open.Core.Kernel.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * DTO base de todas las entidades. Expone uuid (nunca el id interno), status y fecha de creacion.
 * En entidades con borrado fisico el status siempre viaja en 0: no existe la columna en la tabla.
 */
@Getter
@Setter
public class EntidadBaseDTO {
    @Descripcion("Identificador público del registro. El id interno nunca se expone")
    protected String uuid;
    @Descripcion("Estado del registro: 0 activo, -1 eliminado, -2 temporal (carga en varios pasos)")
    protected int status;
    @Descripcion("Fecha y hora de creación del registro (solo lectura)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/Argentina/Buenos_Aires")
    protected Timestamp fechaDeCreacion;

    // Copia uuid y fechaDeCreacion desde la entidad al DTO y, si la entidad tiene borrado logico, tambien status.
    // Recibe: la entidad. Responde: nada (modifica este DTO).
    public void aplicarCamposBase(EntidadBase objeto) {
        this.uuid = objeto.getUuid();
        this.fechaDeCreacion = objeto.getFechaDeCreacion();
        if (objeto instanceof EntidadConBorradoLogico conBorradoLogico) {
            this.status = conBorradoLogico.getStatus();
        }
    }
}
