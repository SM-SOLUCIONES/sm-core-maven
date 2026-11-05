package com.blue.open.Core.Kernel.DTO;

import com.blue.open.Core.Kernel.Contrato.Descripcion;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pedido de paginado que recibe POST /{controller}/paginar. Los campos de filtros y ordenes usan
 * nombres JPA (ver FiltroDePaginado). claseDeEntidad y condiciones los completa el backend: el front no
 * puede mandarlos (@JsonIgnore).
 */
@Getter
@Setter
public class PedidoDePaginado {
    @Descripcion("Número de página pedida, desde 1")
    private int pagina = 0;
    @Descripcion("Cantidad de registros por página")
    private int tamano = 10;

    @JsonIgnore
    private Class<?> claseDeEntidad;
    // Condiciones JPQL agregadas por el backend (ej: scoping por empresa). Nunca vienen del front.
    @JsonIgnore
    private List<CondicionDePaginado> condiciones = new ArrayList<>();

    @Descripcion("Parámetros con nombre que interpreta cada endpoint (opcional)")
    private List<ParametroDePaginado> parametros = new ArrayList<>();
    @Descripcion("Grupos de filtros: los grupos se combinan con AND y los filtros de un mismo grupo con OR")
    private List<List<FiltroDePaginado>> filtros = new ArrayList<>();
    @Descripcion("Criterios de orden, en prioridad")
    private List<OrdenDePaginado> ordenes = new ArrayList<>();

    // Constructor vacio para Jackson
    public PedidoDePaginado() {
    }

    // Pedido armado por el backend sobre la entidad indicada
    public PedidoDePaginado(Class<? extends EntidadBase> claseDeEntidad) {
        this.claseDeEntidad = claseDeEntidad;
    }

    // Busca un parametro por nombre, sin distinguir mayusculas. Responde: el parametro, o null si no viene
    public ParametroDePaginado getParametroPorNombre(String nombre) {
        for (ParametroDePaginado p : this.parametros) {
            if (p.getNombre().equalsIgnoreCase(nombre)) {
                return p;
            }
        }
        return null;
    }

    // Agrega un parametro con nombre al pedido
    public void agregarParametro(ParametroDePaginado parametro) {
        this.parametros.add(parametro);
    }

    // Agrega un grupo de filtros (se combinan con OR entre si y con AND con los demas grupos)
    public void agregarFiltro(List<FiltroDePaginado> filtro) {
        this.filtros.add(filtro);
    }

    // Agrega una condicion JPQL del backend (ej. scoping por empresa) con sus parametros con nombre
    public void agregarCondicion(String jpql, Map<String, Object> parametros) {
        this.condiciones.add(new CondicionDePaginado(jpql, parametros));
    }
}
