package com.blue.open.Core.Kernel.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Respuesta de POST /{controller}/paginar: los registros de una pagina y los datos de la paginacion.
 * Reemplaza al Page de Spring Data en las firmas del paginado: fija la forma del JSON en español, sin pageable ni
 * sort, y no depende de como serialice Page cada version de Spring Data.
 * Recibe: los registros, el numero (desde 0) y el tamano de la pagina, y el total que cumple los filtros.
 */
@Getter
@Setter
@NoArgsConstructor
public class Pagina<T> {
    @Descripcion("Registros de la página")
    private List<T> contenido = new ArrayList<>();
    @Descripcion("Total de registros que cumplen los filtros")
    private long totalDeElementos;
    @Descripcion("Total de páginas")
    private int totalDePaginas;
    @Descripcion("Tamaño de la página")
    private int tamano;
    @Descripcion("Número de página (desde 0)")
    private int numero;
    @Descripcion("Registros en esta página")
    private int cantidadDeElementos;
    @Descripcion("Es la primera página")
    private boolean primera;
    @Descripcion("Es la última página")
    private boolean ultima;
    @Descripcion("La página no tiene registros")
    private boolean vacia;

    /*
     * Arma la pagina: numero desde 0, tamano mayor a 0 y el total de registros que cumplen los filtros. Igual que el
     * PageImpl de Spring Data, si la pagina es la ultima (el contenido no la llena) el total se recalcula con lo que
     * trae, por si hookPaginarFiltrarResultados descarto registros.
     */
    public Pagina(List<T> contenido, int numero, int tamano, long totalDeElementos) {
        this.contenido = contenido == null ? new ArrayList<>() : contenido;
        this.numero = numero;
        this.tamano = tamano;
        long desplazamiento = (long) numero * tamano;
        this.totalDeElementos = !this.contenido.isEmpty() && desplazamiento + tamano > totalDeElementos
                ? desplazamiento + this.contenido.size() : totalDeElementos;
        this.totalDePaginas = tamano > 0 ? (int) Math.ceil((double) this.totalDeElementos / tamano) : 1;
        this.cantidadDeElementos = this.contenido.size();
        this.primera = numero == 0;
        this.ultima = numero + 1 >= this.totalDePaginas;
        this.vacia = this.contenido.isEmpty();
    }
}
