package com.blue.open.Core.Kernel.Proyeccion;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

// DTO de guardado de prueba: lo que el Proyector copia, asigna, ignora y deja a AplicaDto
@Getter
@Setter
public class PruebaProductoGuardarDTO {

    private String nombre;
    private BigDecimal precio;
    private int stock;
    private Boolean activo;
    // @NoProyectable en la entidad: no se copia
    private String secreto;
    // @JsonIgnore en la entidad: no se copia
    private Long id;
    // Sin atributo en la entidad: se ignora
    private String calculado;
    // Sin setter publico en la entidad: no se copia, lo completa aplicar(dto)
    private Integer vendidos;

    // Relacion por uuid: se busca y se asigna; null desasocia
    @Columna("categoria.uuid")
    private String categoriaUuid;

    // Ruta mas larga que relacion.uuid: solo lectura, se ignora
    @Columna("categoria.pais.nombre")
    private String paisNombre;

    // Coleccion: solo lectura, se ignora
    @Coleccion("variantes")
    private List<DtosDeProyeccion.Variante> variantes;
}
