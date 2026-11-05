package com.blue.open.Core.Kernel.Paginado;

import com.blue.open.Core.Kernel.Proyeccion.AplicaDto;
import com.blue.open.Core.Kernel.Proyeccion.NoProyectable;
import com.blue.open.Core.Kernel.Proyeccion.PruebaProductoGuardarDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Entidad de prueba del paginado y de la Proyeccion. Se relaciona con PruebaCategoria (N-a-1), PruebaEtiqueta (N-a-N)
// y PruebaVariante (1-a-N, que a su vez tiene PruebaCodigo).
// No extiende EntidadBase: sus columnDefinition son de MariaDB y el test tambien corre en PostgreSQL.
@Getter
@Setter
@Entity
@Table(name = "prueba_producto")
public class PruebaProducto implements AplicaDto<PruebaProductoGuardarDTO> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    private String uuid;

    private String nombre;

    private BigDecimal precio;

    private Integer stock;

    private Boolean activo;

    private LocalDateTime fechaAlta;

    // Dato sensible: no se puede filtrar, ordenar ni proyectar
    @NoProyectable
    private String secreto;

    @ManyToOne(fetch = FetchType.LAZY)
    private PruebaCategoria categoria;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "prueba_producto_etiqueta")
    private List<PruebaEtiqueta> etiquetas = new ArrayList<>();

    @OneToMany(mappedBy = "producto", fetch = FetchType.LAZY)
    private List<PruebaVariante> variantes = new ArrayList<>();

    // Unidades vendidas: solo cambia por aplicar(dto), nunca por la copia generica
    @Setter(AccessLevel.NONE)
    private Integer vendidos = 0;

    // Nombre que tenia la entidad al llamarse aplicar(dto): prueba que se llama despues de la copia
    @Transient
    @Setter(AccessLevel.NONE)
    private String nombreAlAplicar;

    // Completa los vendidos (nunca bajan) despues de la copia automatica del DTO
    @Override
    public void aplicar(PruebaProductoGuardarDTO dto) {
        this.nombreAlAplicar = this.nombre;
        if (dto.getVendidos() != null && dto.getVendidos() > this.vendidos) {
            this.vendidos = dto.getVendidos();
        }
    }
}
