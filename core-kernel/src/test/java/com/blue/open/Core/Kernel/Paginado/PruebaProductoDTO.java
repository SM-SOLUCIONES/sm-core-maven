package com.blue.open.Core.Kernel.Paginado;

import com.blue.open.Core.Kernel.Proyeccion.Columna;
import com.blue.open.Core.Kernel.Proyeccion.NoProyectable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// DTO de paginado de prueba: campos propios por nombre, campos de relaciones con @Columna
@Getter
@Setter
public class PruebaProductoDTO {

    private String uuid;
    private String nombre;
    private BigDecimal precio;
    // int (primitivo) contra Integer de la entidad
    private int stock;
    private boolean activo;
    private LocalDateTime fechaAlta;
    // Existe en la entidad pero es @NoProyectable: nunca se completa
    private String secreto;

    @Columna("categoria.uuid")
    private String categoriaUuid;

    @Columna("categoria.pais.nombre")
    private String paisNombre;

    // Coleccion: no se proyecta
    private List<String> etiquetas;

    // Excluido en el DTO aunque exista el atributo
    @NoProyectable
    private Long id;
}
