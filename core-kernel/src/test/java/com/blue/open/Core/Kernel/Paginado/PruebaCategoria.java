package com.blue.open.Core.Kernel.Paginado;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Entidad de prueba del paginado: categoria de un PruebaProducto, con un PruebaPais
@Getter
@Setter
@Entity
@Table(name = "prueba_categoria")
public class PruebaCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uuid;

    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    private PruebaPais pais;
}
