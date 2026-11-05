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

// Entidad de prueba de la Proyeccion: codigo de barras de una PruebaVariante (segundo nivel de @OneToMany)
@Getter
@Setter
@Entity
@Table(name = "prueba_codigo")
public class PruebaCodigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String valor;

    @ManyToOne(fetch = FetchType.LAZY)
    private PruebaVariante variante;
}
