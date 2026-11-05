package com.blue.open.Core.Kernel.Paginado;

import com.blue.open.Core.Kernel.Proyeccion.NoProyectable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Entidad de prueba de la Proyeccion: variante de un PruebaProducto (primer nivel de @OneToMany), con PruebaCodigo
@Getter
@Setter
@Entity
@Table(name = "prueba_variante")
public class PruebaVariante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    private String uuid;

    private String nombre;

    // Posicion de la variante en el listado del producto (orden alternativo al id)
    private Integer orden;

    // Dato sensible: nunca se proyecta
    @NoProyectable
    private String secreto;

    // Dato interno: nunca se proyecta
    @JsonIgnore
    private String oculto;

    @ManyToOne(fetch = FetchType.LAZY)
    private PruebaProducto producto;

    @OneToMany(mappedBy = "variante", fetch = FetchType.LAZY)
    private List<PruebaCodigo> codigos = new ArrayList<>();
}
