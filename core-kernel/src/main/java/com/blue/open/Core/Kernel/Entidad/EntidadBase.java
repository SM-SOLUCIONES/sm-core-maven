package com.blue.open.Core.Kernel.Entidad;


import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;

/**
 * Superclase base de todas las entidades del sistema (mapped superclass, no es tabla propia).
 * Aporta id, uuid y auditoria de fecha/usuario de creacion y edicion. No define borrado:
 * una entidad con borrado logico debe extender {@link EntidadConBorradoLogico}; una entidad
 * con borrado fisico extiende directamente esta clase y no tiene columna status.
 * No se relaciona directamente con otras entidades: cada entidad hija hereda estos campos.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    protected Long id;

    // UUID: Gestionado por Java (sin default en BD para evitar errores de exportacion)
    @Column(name = "uuid", length = 36, unique = true, nullable = false, updatable = false)
    @JdbcTypeCode(Types.CHAR)
    protected String uuid;

    @JsonIgnore
    protected Long idUsuarioDeCreacion;
    @JsonIgnore
    protected Long idUsuarioDeActualizacion;

    @CreationTimestamp
    @Column(name = "fecha_de_creacion", updatable = false, nullable = false)
    protected Timestamp fechaDeCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_de_actualizacion", nullable = false)
    protected Timestamp fechaDeActualizacion;

    // Antes de insertar: genera un uuid v7 si la entidad no trae uno
    @PrePersist
    protected void alCrear() {
        if (this.uuid == null || this.uuid.isEmpty()) {
            this.uuid = generarUuidV7().toString();
        }
    }

    /**
     * Aplica al modelo los campos de control que vienen en el DTO.
     * En una entidad sin borrado logico no hay nada que aplicar; lo sobreescribe
     * {@link EntidadConBorradoLogico} para validar las transiciones de status.
     */
    public void aplicarCamposBase(EntidadBaseDTO dto) {
    }

    private static final SecureRandom UUID_V7_ALEATORIO = new SecureRandom();

    // UUIDv7: timestamp en los primeros 48 bits para que el indice unico de uuid
    // reciba valores ordenados y no se degrade con insertos aleatorios (ver RFC 9562)
    public static UUID generarUuidV7() {
        byte[] bytes = new byte[16];
        UUID_V7_ALEATORIO.nextBytes(bytes);

        long milisegundos = System.currentTimeMillis();
        bytes[0] = (byte) (milisegundos >>> 40);
        bytes[1] = (byte) (milisegundos >>> 32);
        bytes[2] = (byte) (milisegundos >>> 24);
        bytes[3] = (byte) (milisegundos >>> 16);
        bytes[4] = (byte) (milisegundos >>> 8);
        bytes[5] = (byte) milisegundos;

        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }
        return new UUID(msb, lsb);
    }
}
