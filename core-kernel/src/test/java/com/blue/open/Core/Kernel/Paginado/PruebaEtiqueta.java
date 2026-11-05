package com.blue.open.Core.Kernel.Paginado;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Entidad de prueba del paginado: etiqueta N-a-N de PruebaProducto (filtros sobre colecciones)
@Getter
@Setter
@Entity
@Table(name = "prueba_etiqueta")
public class PruebaEtiqueta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
}
