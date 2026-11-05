package com.blue.open.Core.Kernel.Proyeccion;

/**
 * La implementa una entidad que necesita completar, al guardar, los campos que no se copian solos: los que
 * cambian por un metodo de negocio (@Setter(AccessLevel.NONE)) o requieren logica propia.
 * El Proyector la llama despues de copiar los campos simples y las relaciones del DTO, solo si el DTO que se
 * aplica es del tipo declarado. No recibe dependencias: lo que necesite un service va en el hook del service.
 * Recibe: el DTO que se esta aplicando. Responde: nada, modifica la propia entidad.
 */
public interface AplicaDto<DTO> {

    // Completa la entidad a partir del DTO, despues de la copia automatica
    void aplicar(DTO dto);
}
