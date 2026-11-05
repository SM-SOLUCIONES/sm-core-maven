package com.blue.open.Core.Kernel.Paginado;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Entidad de prueba del paginado: pais de una PruebaCategoria (segundo nivel de relacion)
@Getter
@Setter
@Entity
@Table(name = "prueba_pais")
public class PruebaPais {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
}
