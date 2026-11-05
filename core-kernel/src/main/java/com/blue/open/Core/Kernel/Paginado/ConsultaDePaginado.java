package com.blue.open.Core.Kernel.Paginado;

import com.blue.open.Core.Kernel.DTO.CondicionDePaginado;
import com.blue.open.Core.Kernel.DTO.FiltroDePaginado;
import com.blue.open.Core.Kernel.DTO.OrdenDePaginado;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Proyeccion.CampoProyectado;
import com.blue.open.Core.Kernel.Proyeccion.ColeccionProyectada;
import com.blue.open.Core.Kernel.Proyeccion.NoProyectable;
import com.blue.open.Core.Kernel.Proyeccion.ProyeccionDeDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traduce un PedidoDePaginado a dos consultas JPQL: la de la pagina (solo con las columnas del DTO) y la de conteo.
 * Al ser JPQL, Hibernate genera el SQL de cada motor (MariaDB, PostgreSQL): no hay SQL nativo.
 * - Los campos se escriben con nombres JPA (atributo, o ruta por relaciones: "tipoVehiculo.nombre") o con el nombre de
 *   un campo del DTO de salida anotado con @Columna("ruta"), que se traduce a esa ruta (desde Core 3.5.0: ej. filtrar
 *   u ordenar por "tipoVehiculoUuid"). Si el nombre tambien es un atributo de la entidad, gana el atributo.
 *   Cada ruta se valida contra el metamodelo: un campo inexistente, @NoProyectable o @JsonIgnore se rechaza,
 *   asi que el front no puede inyectar JPQL ni leer columnas sensibles.
 * - Las relaciones @ManyToOne / @OneToOne se resuelven con LEFT JOIN automaticos.
 * - Un filtro sobre una coleccion (@OneToMany / @ManyToMany) se resuelve con EXISTS, para no duplicar filas.
 * - Todos los valores viajan como parametros, convertidos al tipo del atributo.
 * - Si el DTO tiene @Coleccion, el SELECT agrega el id de cada registro al final: con esos ids se resuelven las
 *   colecciones, con una consulta por anotacion armada por deColeccion().
 * Dependencias externas: el metamodelo de JPA.
 * Recibe: el metamodelo, la entidad, el DTO de salida y el PedidoDePaginado.
 * Responde: el JPQL de la pagina, el del conteo, los parametros y los campos proyectados.
 */
public final class ConsultaDePaginado {

    private static final Set<String> OPERADORES = Set.of("=", "!=", "<>", ">", "<", ">=", "<=",
            "like", "not like", "in", "not in", "is null", "is not null");
    private static final String AGRUPAR = "group";
    private static final ObjectMapper JSON = new ObjectMapper();

    // Para que se usa una ruta: define si puede cruzar colecciones y si puede usar atributos @JsonIgnore
    private enum Uso {
        FILTRO(true, false), ORDEN(false, true), GRUPO(false, false), PROYECCION(false, false);

        final boolean permiteColeccion;
        final boolean permiteOculto;

        Uso(boolean permiteColeccion, boolean permiteOculto) {
            this.permiteColeccion = permiteColeccion;
            this.permiteOculto = permiteOculto;
        }
    }

    private final Metamodel metamodel;
    private final EntityType<?> entidad;
    private final Map<String, Object> parametros = new LinkedHashMap<>();
    private final List<CampoProyectado> campos;
    // Ruta de cada campo del DTO con @Columna, por su nombre en el DTO (los filtros, ordenes y agrupaciones lo aceptan)
    private final Map<String, String> rutasDelDto = new HashMap<>();
    // true si el SELECT agrega el id del registro despues de los campos (para resolver las @Coleccion)
    private final boolean conId;
    private int contadorDeAlias = 0;
    private final String consulta;
    private final String consultaDeConteo;

    // Arma la consulta de la pagina y la de conteo de un paginado. Recibe el metamodelo, la entidad, el DTO de salida y
    // el pedido. Lanza PAGINADO_INVALIDO si un campo, operacion o direccion del pedido no es valido
    public ConsultaDePaginado(Metamodel metamodel, Class<?> claseDeEntidad, Class<?> claseDto, PedidoDePaginado pedido) {
        this.metamodel = metamodel;
        this.entidad = metamodel.entity(claseDeEntidad);
        this.campos = ProyeccionDeDto.de(claseDto, entidad);
        this.conId = !ProyeccionDeDto.colecciones(claseDto, entidad).isEmpty();
        for (CampoProyectado campo : campos) {
            if (!campo.ruta().equals(campo.campo().getName())) {
                rutasDelDto.put(campo.campo().getName(), campo.ruta());
            }
        }
        String nombre = entidad.getName();

        List<List<FiltroDePaginado>> grupos = new ArrayList<>();
        List<FiltroDePaginado> agrupadores = new ArrayList<>();
        for (List<FiltroDePaginado> grupo : pedido.getFiltros()) {
            List<FiltroDePaginado> condiciones = new ArrayList<>();
            for (FiltroDePaginado filtro : grupo) {
                if (AGRUPAR.equals(normalizar(filtro.getOperacion()))) {
                    agrupadores.add(filtro);
                } else {
                    condiciones.add(filtro);
                }
            }
            if (!condiciones.isEmpty()) {
                grupos.add(condiciones);
            }
        }

        // Ambito "t": filtros, condiciones del backend y agrupacion
        Ambito filtros = new Ambito("t", entidad, false);
        String clausulaDeCondiciones = clausulaDeCondiciones(filtros, grupos, pedido.getCondiciones());
        int joinsDeFiltros = filtros.joins.size();

        if (agrupadores.isEmpty()) {
            String columnas = proyeccion(filtros);
            String clausulaDeOrden = clausulaDeOrden(filtros, pedido.getOrdenes());
            this.consulta = "SELECT " + columnas + " FROM " + nombre + " t" + filtros.joins(filtros.joins.size())
                    + clausulaDeCondiciones + clausulaDeOrden;
            this.consultaDeConteo = "SELECT COUNT(t) FROM " + nombre + " t" + filtros.joins(joinsDeFiltros)
                    + clausulaDeCondiciones;
        } else {
            // Un registro por grupo: el de mayor id. La pagina se arma sobre esos ids, con su propio ambito "o".
            String columnasDeAgrupacion = agrupadores.stream()
                    .map(filtro -> filtros.resolver(ruta(filtro.getCampo()), Uso.GRUPO).expresion())
                    .collect(Collectors.joining(", "));
            String subconsulta = "SELECT MAX(t.id) FROM " + nombre + " t" + filtros.joins(filtros.joins.size())
                    + clausulaDeCondiciones + " GROUP BY " + columnasDeAgrupacion;
            Ambito salida = new Ambito("o", entidad, false);
            String columnas = proyeccion(salida);
            String clausulaDeOrden = clausulaDeOrden(salida, pedido.getOrdenes());
            this.consulta = "SELECT " + columnas + " FROM " + nombre + " o" + salida.joins(salida.joins.size())
                    + " WHERE o.id IN (" + subconsulta + ")" + clausulaDeOrden;
            this.consultaDeConteo = "SELECT COUNT(o) FROM " + nombre + " o WHERE o.id IN (" + subconsulta + ")";
        }
    }

    /*
     * Consulta de una @Coleccion: trae los campos del SubDTO de los elementos de todos los padres del lote, mas el id
     * del elemento y el del padre (en ese orden, despues de los campos). Si los elementos tienen borrado logico, solo
     * los vigentes. Orden: el atributo declarado y despues el id del elemento. El parametro :ids son los ids padre.
     */
    public static ConsultaDePaginado deColeccion(Metamodel metamodel, EntityType<?> padre, ColeccionProyectada coleccion) {
        return new ConsultaDePaginado(metamodel, padre, coleccion);
    }

    private ConsultaDePaginado(Metamodel metamodel, EntityType<?> padre, ColeccionProyectada coleccion) {
        this.metamodel = metamodel;
        this.entidad = coleccion.elemento();
        this.campos = ProyeccionDeDto.de(coleccion.subDto(), entidad);
        this.conId = true;
        Ambito elementos = new Ambito("e", entidad, false);
        String columnas = proyeccion(elementos);
        String orden = coleccion.orden().isBlank() ? ""
                : elementos.resolver(coleccion.orden(), Uso.ORDEN).expresion() + " ASC NULLS FIRST, ";
        String vigentes = EntidadConBorradoLogico.class.isAssignableFrom(entidad.getJavaType()) ? " AND e.status = 0" : "";
        this.consulta = "SELECT " + columnas + ", p.id FROM " + padre.getName() + " p JOIN p." + coleccion.atributo()
                + " e" + elementos.joins(elementos.joins.size()) + " WHERE p.id IN (:ids)" + vigentes
                + " ORDER BY " + orden + "e.id ASC";
        this.consultaDeConteo = null;
    }

    // JPQL de la pagina (o de la @Coleccion)
    public String getConsulta() {
        return consulta;
    }

    // JPQL del conteo total. Null en la consulta de una @Coleccion
    public String getConsultaDeConteo() {
        return consultaDeConteo;
    }

    // Parametros con nombre de las dos consultas (los del pedido "pg_N" y los de las condiciones del backend)
    public Map<String, Object> getParametros() {
        return parametros;
    }

    // Campos del DTO, en el orden de las columnas del SELECT
    public List<CampoProyectado> getCampos() {
        return campos;
    }

    // true si el SELECT agrega el id del registro despues de los campos
    public boolean isConId() {
        return conId;
    }

    private String proyeccion(Ambito ambito) {
        String columnas = campos.stream()
                .map(campo -> ambito.resolver(campo.ruta(), Uso.PROYECCION).expresion())
                .collect(Collectors.joining(", "));
        return conId ? columnas + ", " + ambito.raiz + ".id" : columnas;
    }

    // Clausula WHERE: grupos unidos con AND, condiciones de un mismo grupo unidas con OR
    private String clausulaDeCondiciones(Ambito ambito, List<List<FiltroDePaginado>> grupos, List<CondicionDePaginado> condiciones) {
        List<String> partes = new ArrayList<>();
        for (List<FiltroDePaginado> grupo : grupos) {
            partes.add("(" + grupo.stream().map(filtro -> condicion(ambito, filtro))
                    .collect(Collectors.joining(" OR ")) + ")");
        }
        for (CondicionDePaginado condicion : condiciones) {
            for (Map.Entry<String, Object> parametro : condicion.getParametros().entrySet()) {
                if (parametros.containsKey(parametro.getKey()) || parametro.getKey().startsWith("pg_")) {
                    throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO, "Error al obtener el paginado",
                            "ConsultaDePaginado | parametro repetido o con prefijo reservado pg_: " + parametro.getKey());
                }
                parametros.put(parametro.getKey(), parametro.getValue());
            }
            partes.add("(" + condicion.getJpql() + ")");
        }
        return partes.isEmpty() ? "" : " WHERE " + String.join(" AND ", partes);
    }

    private String condicion(Ambito ambito, FiltroDePaginado filtro) {
        String operacion = normalizar(filtro.getOperacion());
        if (!OPERADORES.contains(operacion)) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                    "La operacion '" + filtro.getOperacion() + "' no es valida",
                    "ConsultaDePaginado | operaciones validas: " + OPERADORES);
        }
        Destino destino = ambito.resolver(ruta(filtro.getCampo()), Uso.FILTRO);
        if (destino.coleccion() == null) {
            return comparacion(destino, operacion, filtro);
        }
        // Filtro sobre una coleccion: "existe al menos un elemento que cumple"
        String alias = "c" + (++contadorDeAlias);
        Ambito elementos = new Ambito(alias, destino.coleccion(), true);
        String interna = comparacion(elementos.resolver(destino.resto(), Uso.FILTRO), operacion, filtro);
        return "EXISTS (SELECT 1 FROM " + destino.expresion() + " " + alias
                + elementos.joins(elementos.joins.size()) + " WHERE " + interna + ")";
    }

    private String comparacion(Destino destino, String operacion, FiltroDePaginado filtro) {
        String expresion = destino.expresion();
        switch (operacion) {
            case "is null":
                return expresion + " IS NULL";
            case "is not null":
                return expresion + " IS NOT NULL";
            case "in":
            case "not in": {
                List<Object> valores = new ArrayList<>();
                for (String valor : lista(filtro)) {
                    valores.add(ConversorDeValores.desdeTexto(valor, destino.tipo(), filtro.getCampo()));
                }
                if (valores.isEmpty()) {
                    return "in".equals(operacion) ? "1 = 0" : "1 = 1";
                }
                return expresion + ("in".equals(operacion) ? " IN (:" : " NOT IN (:") + parametro(valores) + ")";
            }
            case "like":
            case "not like": {
                // LOWER en ambos lados: MariaDB compara sin distinguir mayusculas y PostgreSQL si; asi responden igual
                String texto = destino.tipo() == String.class ? expresion : "CAST(" + expresion + " AS String)";
                String valor = filtro.getValor() == null ? "" : filtro.getValor().toLowerCase(Locale.ROOT);
                return "LOWER(" + texto + ")" + ("like".equals(operacion) ? " LIKE :" : " NOT LIKE :") + parametro(valor);
            }
            default: {
                Object valor = ConversorDeValores.desdeTexto(filtro.getValor(), destino.tipo(), filtro.getCampo());
                return expresion + " " + ("!=".equals(operacion) ? "<>" : operacion) + " :" + parametro(valor);
            }
        }
    }

    // Clausula ORDER BY con los ordenes del pedido y el desempate por id
    private String clausulaDeOrden(Ambito ambito, List<OrdenDePaginado> ordenes) {
        List<String> partes = new ArrayList<>();
        boolean conId = false;
        for (OrdenDePaginado orden : ordenes) {
            String direccion = normalizar(orden.getDireccion());
            if (direccion.isEmpty()) {
                direccion = "asc";
            }
            if (!direccion.equals("asc") && !direccion.equals("desc")) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                        "La direccion '" + orden.getDireccion() + "' no es valida",
                        "ConsultaDePaginado | direcciones validas: asc, desc");
            }
            String expresion = ambito.resolver(ruta(orden.getCampo()), Uso.ORDEN).expresion();
            conId |= expresion.equals(ambito.raiz + ".id");
            // Nulos explicitos con el criterio de MariaDB (primeros en asc, ultimos en desc): PostgreSQL por defecto
            // hace lo contrario, y sin esto la misma pagina devolveria filas distintas segun el motor
            partes.add(expresion + ("asc".equals(direccion) ? " ASC NULLS FIRST" : " DESC NULLS LAST"));
        }
        // Desempate por id: sin un orden total, el motor puede repetir u omitir filas entre paginas
        if (!conId) {
            partes.add(ambito.raiz + ".id ASC");
        }
        return " ORDER BY " + String.join(", ", partes);
    }

    private List<String> lista(FiltroDePaginado filtro) {
        try {
            List<Object> valores = JSON.readValue(filtro.getValor(), new TypeReference<List<Object>>() {
            });
            return valores.stream().map(valor -> valor == null ? null : String.valueOf(valor)).toList();
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                    "El valor del filtro '" + filtro.getCampo() + "' debe ser una lista JSON",
                    "ConsultaDePaginado | lista | ej: [\"a\",\"b\"] | " + e.getMessage());
        }
    }

    /*
     * Ruta JPA del campo que pide el pedido. Si es el nombre de un campo del DTO con @Columna y no es un atributo de la
     * entidad, responde la ruta de la @Columna; si no, el mismo nombre (que despues se valida contra el metamodelo).
     * El atributo de la entidad gana para no cambiar el resultado de los pedidos anteriores a 3.5.0.
     */
    private String ruta(String campo) {
        if (campo == null) {
            return null;
        }
        String ruta = rutasDelDto.get(campo.trim());
        if (ruta == null || esAtributoDeLaEntidad(campo.trim())) {
            return campo;
        }
        return ruta;
    }

    // true si la entidad tiene un atributo (propio o heredado) con ese nombre
    private boolean esAtributoDeLaEntidad(String nombre) {
        try {
            entidad.getAttribute(nombre);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Registra un parametro con nombre propio del paginado ("pg_N") y devuelve ese nombre
    private String parametro(Object valor) {
        String nombre = "pg_" + parametros.size();
        parametros.put(nombre, valor);
        return nombre;
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Resultado de resolver una ruta.
     * expresion: la expresion JPQL del atributo, o de la coleccion si coleccion != null.
     * tipo: tipo Java del atributo final. coleccion: tipo de los elementos si la ruta cruza una coleccion.
     * resto: la parte de la ruta que queda despues de la coleccion.
     */
    private record Destino(String expresion, Class<?> tipo, ManagedType<?> coleccion, String resto) {
    }

    /**
     * Un FROM con su alias raiz y los JOIN que fueron necesitando las rutas.
     * Dos rutas que pasan por la misma relacion reutilizan el mismo JOIN.
     */
    private final class Ambito {
        final String raiz;
        final ManagedType<?> tipoRaiz;
        // En el ambito de un EXISTS las colecciones se pueden unir con JOIN: duplicar filas no afecta
        final boolean coleccionesComoJoin;
        final List<String> joins = new ArrayList<>();
        final Map<String, String> aliasPorRuta = new HashMap<>();

        Ambito(String raiz, ManagedType<?> tipoRaiz, boolean coleccionesComoJoin) {
            this.raiz = raiz;
            this.tipoRaiz = tipoRaiz;
            this.coleccionesComoJoin = coleccionesComoJoin;
        }

        String joins(int cantidad) {
            return joins.subList(0, cantidad).stream().map(join -> " " + join).collect(Collectors.joining());
        }

        Destino resolver(String ruta, Uso uso) {
            if (ruta == null || ruta.isBlank()) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                        "Falta el campo en un filtro u orden del paginado",
                        "ConsultaDePaginado | resolver | campo vacio");
            }
            String[] partes = ruta.trim().split("\\.");
            ManagedType<?> tipo = tipoRaiz;
            String expresion = raiz;
            String recorrida = "";
            for (int i = 0; i < partes.length; i++) {
                String parte = partes[i];
                boolean ultima = i == partes.length - 1;
                Attribute<?, ?> atributo = atributo(tipo, parte, ruta);
                validarAcceso(atributo, uso, ruta);
                recorrida = recorrida.isEmpty() ? parte : recorrida + "." + parte;

                if (atributo.isCollection()) {
                    if (ultima) {
                        throw noTerminaEnAtributo(ruta);
                    }
                    ManagedType<?> elemento = tipoDeElemento((PluralAttribute<?, ?, ?>) atributo, ruta);
                    if (coleccionesComoJoin) {
                        expresion = join("JOIN", expresion, parte, recorrida);
                        tipo = elemento;
                        continue;
                    }
                    if (!uso.permiteColeccion) {
                        throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                                "El campo '" + ruta + "' pasa por una lista y no se puede usar para ordenar ni mostrar",
                                "ConsultaDePaginado | resolver | coleccion en " + uso);
                    }
                    String resto = String.join(".", Arrays.copyOfRange(partes, i + 1, partes.length));
                    return new Destino(expresion + "." + parte, null, elemento, resto);
                }

                switch (atributo.getPersistentAttributeType()) {
                    case BASIC:
                        if (!ultima) {
                            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                                    "El campo '" + parte + "' de '" + ruta + "' no es una relacion",
                                    "ConsultaDePaginado | resolver | atributo simple en medio de la ruta");
                        }
                        return new Destino(expresion + "." + parte, atributo.getJavaType(), null, null);
                    case EMBEDDED:
                        if (ultima) {
                            throw noTerminaEnAtributo(ruta);
                        }
                        expresion = expresion + "." + parte;
                        tipo = metamodel.embeddable(atributo.getJavaType());
                        break;
                    case MANY_TO_ONE:
                    case ONE_TO_ONE:
                        if (ultima) {
                            throw noTerminaEnAtributo(ruta);
                        }
                        expresion = join("LEFT JOIN", expresion, parte, recorrida);
                        tipo = metamodel.managedType(atributo.getJavaType());
                        break;
                    default:
                        throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                                "El campo '" + ruta + "' no se puede usar en el paginado",
                                "ConsultaDePaginado | resolver | tipo " + atributo.getPersistentAttributeType());
                }
            }
            throw noTerminaEnAtributo(ruta);
        }

        private String join(String tipoDeJoin, String expresion, String parte, String recorrida) {
            String alias = aliasPorRuta.get(recorrida);
            if (alias == null) {
                alias = raiz + "_j" + (++contadorDeAlias);
                joins.add(tipoDeJoin + " " + expresion + "." + parte + " " + alias);
                aliasPorRuta.put(recorrida, alias);
            }
            return alias;
        }
    }

    private static Attribute<?, ?> atributo(ManagedType<?> tipo, String nombre, String ruta) {
        try {
            return tipo.getAttribute(nombre);
        } catch (IllegalArgumentException e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO, "El campo '" + ruta + "' no existe",
                    "ConsultaDePaginado | " + tipo.getJavaType().getSimpleName() + " no tiene el atributo '" + nombre + "'");
        }
    }

    private static void validarAcceso(Attribute<?, ?> atributo, Uso uso, String ruta) {
        if (ProyeccionDeDto.tieneAnotacion(atributo, NoProyectable.class)
                || (!uso.permiteOculto && ProyeccionDeDto.tieneAnotacion(atributo, JsonIgnore.class))) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                    "El campo '" + ruta + "' no se puede usar en el paginado",
                    "ConsultaDePaginado | " + atributo.getName() + " es @NoProyectable o @JsonIgnore (uso: " + uso + ")");
        }
    }

    private ManagedType<?> tipoDeElemento(PluralAttribute<?, ?, ?> atributo, String ruta) {
        try {
            return metamodel.managedType(atributo.getElementType().getJavaType());
        } catch (IllegalArgumentException e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                    "El campo '" + ruta + "' no se puede usar en el paginado",
                    "ConsultaDePaginado | la coleccion " + atributo.getName() + " no es de entidades");
        }
    }

    private static ExcepcionDeApi noTerminaEnAtributo(String ruta) {
        return new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                "El campo '" + ruta + "' debe terminar en un atributo simple (ej: 'relacion.nombre')",
                "ConsultaDePaginado | resolver | la ruta termina en una relacion o coleccion");
    }
}
