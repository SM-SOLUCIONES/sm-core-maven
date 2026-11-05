package com.blue.open.Core.Kernel.Proyeccion;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// DTOs de lectura de prueba del Proyector, sobre el modelo PruebaProducto -> PruebaVariante -> PruebaCodigo
public final class DtosDeProyeccion {

    private DtosDeProyeccion() {
    }

    // Producto con relaciones (@Columna), variantes con sus codigos (dos niveles) y etiquetas ordenadas por nombre
    @Getter
    @Setter
    public static class Producto {
        private String uuid;
        private String nombre;
        // @NoProyectable en la entidad: nunca se completa
        private String secreto;
        // Sin atributo en la entidad: queda en su valor por defecto
        private String calculado;
        @Columna("categoria.uuid")
        private String categoriaUuid;
        @Columna("categoria.pais.nombre")
        private String paisNombre;
        @Coleccion("variantes")
        private List<Variante> variantes;
        @Coleccion(value = "etiquetas", orden = "nombre")
        private List<Etiqueta> etiquetas;
    }

    @Getter
    @Setter
    public static class Variante {
        private String uuid;
        private String nombre;
        // @NoProyectable en la entidad
        private String secreto;
        // @JsonIgnore en la entidad
        private String oculto;
        @Coleccion(value = "codigos", orden = "valor")
        private List<Codigo> codigos;
    }

    @Getter
    @Setter
    public static class Codigo {
        private String valor;
    }

    @Getter
    @Setter
    public static class Etiqueta {
        private String nombre;
    }

    // Variantes ordenadas por el atributo orden en vez del id
    @Getter
    @Setter
    public static class ProductoConVariantesPorOrden {
        private String uuid;
        @Coleccion(value = "variantes", orden = "orden")
        private List<Etiqueta> variantes;
    }

    // Ciclo: el DTO se contiene a si mismo
    @Getter
    @Setter
    public static class Ciclo {
        private String uuid;
        @Coleccion("variantes")
        private List<Ciclo> variantes;
    }

    // Coleccion que no existe en la entidad
    @Getter
    @Setter
    public static class ColeccionInexistente {
        private String uuid;
        @Coleccion("noExiste")
        private List<Codigo> codigos;
    }

    // @Coleccion sobre una relacion que no es coleccion
    @Getter
    @Setter
    public static class ColeccionSobreRelacion {
        private String uuid;
        @Coleccion("categoria")
        private List<Codigo> categorias;
    }

    // @Columna con una ruta inexistente
    @Getter
    @Setter
    public static class ColumnaInexistente {
        private String uuid;
        @Columna("categoria.noExiste")
        private String dato;
    }
}
