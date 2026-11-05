package com.blue.open.Core.Kernel.Proyeccion;

import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Entidad de prueba con borrado logico: verifica que aplicar un DTO valide las transiciones de status
// (aplicarCamposBase). No se relaciona con otras entidades.
@Getter
@Setter
@Entity
@Table(name = "prueba_eliminable")
public class PruebaEliminable extends EntidadConBorradoLogico {

    private String nombre;
}
