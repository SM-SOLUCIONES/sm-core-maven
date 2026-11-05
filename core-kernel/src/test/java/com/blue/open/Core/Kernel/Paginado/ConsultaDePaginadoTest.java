package com.blue.open.Core.Kernel.Paginado;

import com.blue.open.Core.Kernel.DTO.FiltroDePaginado;
import com.blue.open.Core.Kernel.DTO.OrdenDePaginado;
import com.blue.open.Core.Kernel.DTO.Pagina;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Proyeccion.Columna;
import com.blue.open.Core.Kernel.Repository.MotorDeConsultas;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test del motor de paginado JPQL contra una base real: el mismo PedidoDePaginado tiene que responder lo mismo
// en cada motor. Siempre corre sobre H2 en modo MariaDB y en modo PostgreSQL (con el dialecto de cada uno).
// Para correrlo ademas contra MariaDB y PostgreSQL reales, definir las variables de entorno
// PAGINADO_MARIADB_URL y/o PAGINADO_POSTGRES_URL con la URL JDBC completa (incluyendo user y password).
class ConsultaDePaginadoTest {

    private static final Map<String, EntityManagerFactory> BASES = new LinkedHashMap<>();

    @BeforeAll
    static void levantarBases() {
        BASES.put("h2-mariadb", crear("h2-mariadb",
                "jdbc:h2:mem:paginado_maria;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "org.hibernate.dialect.MariaDBDialect"));
        BASES.put("h2-postgres", crear("h2-postgres",
                "jdbc:h2:mem:paginado_pg;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "org.hibernate.dialect.PostgreSQLDialect"));
        String mariadb = System.getenv("PAGINADO_MARIADB_URL");
        if (mariadb != null && !mariadb.isBlank()) {
            BASES.put("mariadb", crear("mariadb", mariadb, null));
        }
        String postgres = System.getenv("PAGINADO_POSTGRES_URL");
        if (postgres != null && !postgres.isBlank()) {
            BASES.put("postgres", crear("postgres", postgres, null));
        }
        BASES.values().forEach(ConsultaDePaginadoTest::cargarDatos);
    }

    @AfterAll
    static void cerrarBases() {
        BASES.values().forEach(emf -> emf.close());
    }

    static List<String> bases() {
        return new ArrayList<>(BASES.keySet());
    }

    // ---------- Proyeccion ----------

    @ParameterizedTest
    @MethodSource("bases")
    void proyecta_camposPropios_yDeRelaciones_conLeftJoin(String base) {
        Pagina<PruebaProductoDTO> pagina = paginar(base, pedido(1, 20));

        assertEquals(7, pagina.getTotalDeElementos());
        PruebaProductoDTO yerba = porNombre(pagina, "Yerba Mate");
        assertEquals("p1", yerba.getUuid());
        assertEquals(0, new BigDecimal("10.50").compareTo(yerba.getPrecio()));
        assertEquals(5, yerba.getStock());
        assertTrue(yerba.isActivo());
        assertEquals(LocalDateTime.of(2026, 1, 10, 10, 0), yerba.getFechaAlta());
        assertEquals("cat-beb", yerba.getCategoriaUuid());
        assertEquals("Argentina", yerba.getPaisNombre());
        assertNull(yerba.getSecreto(), "un atributo @NoProyectable nunca se proyecta");
        assertNull(yerba.getId(), "un campo @NoProyectable del DTO no se completa");
        assertNull(yerba.getEtiquetas(), "las colecciones no se proyectan");

        PruebaProductoDTO sinCategoria = porNombre(pagina, "Sin categoria");
        assertNull(sinCategoria.getCategoriaUuid());
        assertNull(sinCategoria.getPaisNombre());
    }

    @ParameterizedTest
    @MethodSource("bases")
    void pagina_respetaTamanoYTotal(String base) {
        PedidoDePaginado pedido = pedido(2, 3);
        pedido.getOrdenes().add(new OrdenDePaginado("nombre", "asc"));

        Pagina<PruebaProductoDTO> pagina = paginar(base, pedido);

        assertEquals(7, pagina.getTotalDeElementos());
        assertEquals(3, pagina.getTotalDePaginas());
        // Arroz, Cafe, Fideos | Mate cocido, Sin categoria, Te | Yerba Mate
        assertEquals(List.of("Mate cocido", "Sin categoria", "Te"), nombres(pagina));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void sinResultados_respondePaginaVacia(String base) {
        PedidoDePaginado pedido = pedido(1, 10);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("nombre", "no existe")));

        Pagina<PruebaProductoDTO> pagina = paginar(base, pedido);

        assertEquals(0, pagina.getTotalDeElementos());
        assertTrue(pagina.getContenido().isEmpty());
    }

    // ---------- Filtros ----------

    @ParameterizedTest
    @MethodSource("bases")
    void filtra_porRelacionDeDosNiveles(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("categoria.pais.nombre", "Argentina")));

        assertEquals(List.of("Cafe", "Mate cocido", "Te", "Yerba Mate"), nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void like_noDistingueMayusculas_enNingunMotor(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("nombre", "like", "%MATE%")));

        assertEquals(List.of("Mate cocido", "Yerba Mate"), nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void in_convierteLosValoresAlTipoDelAtributo(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("stock", "in", "[5, \"10\"]")));

        assertEquals(List.of("Cafe", "Fideos", "Mate cocido", "Yerba Mate"), nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void gruposUnidosConAnd_condicionesDelGrupoConOr(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("activo", "1")));
        pedido.agregarFiltro(List.of(new FiltroDePaginado("stock", ">=", "10"), new FiltroDePaginado("precio", "<", "3.5")));

        // activos con stock >= 10 o precio < 3.5: Cafe (stock 10), Arroz (3), Sin categoria (1)
        assertEquals(List.of("Arroz", "Cafe", "Sin categoria"), nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void filtraFechas_conZonaHorariaGmt3(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("fechaAlta", ">=", "2026-01-01T00:00:00.000-03:00")));

        assertEquals(List.of("Arroz", "Mate cocido", "Sin categoria", "Te", "Yerba Mate"),
                nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void isNull_sobreRelacion(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("categoria.uuid", "is null", "")));

        assertEquals(List.of("Sin categoria"), nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void filtroSobreColeccion_usaExists_sinDuplicarFilas(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        // Yerba Mate tiene las dos etiquetas: con un JOIN apareceria dos veces
        pedido.agregarFiltro(List.of(new FiltroDePaginado("etiquetas.nombre", "in", "[\"oferta\", \"nuevo\"]")));

        Pagina<PruebaProductoDTO> pagina = paginar(base, pedido);

        assertEquals(4, pagina.getTotalDeElementos());
        assertEquals(List.of("Arroz", "Cafe", "Mate cocido", "Yerba Mate"), nombresOrdenados(pagina));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void condicionDelBackend_seSumaAlWhere(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("activo", "true")));
        pedido.agregarCondicion("t.stock > :minimo", Map.of("minimo", 5));

        assertEquals(List.of("Arroz", "Cafe"), nombresOrdenados(paginar(base, pedido)));
    }

    // ---------- Orden y agrupacion ----------

    @ParameterizedTest
    @MethodSource("bases")
    void ordenaPorRelacion_conNulosComoMariaDB(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.getOrdenes().add(new OrdenDePaginado("categoria.pais.nombre", "asc"));
        pedido.getOrdenes().add(new OrdenDePaginado("nombre", "desc"));

        // asc: los nulos primero (asi ordena MariaDB); PostgreSQL por defecto los pone al final
        assertEquals(List.of("Sin categoria", "Yerba Mate", "Te", "Mate cocido", "Cafe", "Fideos", "Arroz"),
                nombres(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void ordenaPorId_aunqueSeaJsonIgnore(String base) {
        PedidoDePaginado pedido = pedido(1, 3);
        pedido.getOrdenes().add(new OrdenDePaginado("id", "desc"));

        assertEquals(List.of("Mate cocido", "Sin categoria", "Fideos"), nombres(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void group_devuelveElUltimoRegistroDeCadaGrupo(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("categoria.uuid", "group", "")));
        pedido.getOrdenes().add(new OrdenDePaginado("nombre", "asc"));

        Pagina<PruebaProductoDTO> pagina = paginar(base, pedido);

        // Bebidas -> Mate cocido (id 7), Almacen -> Fideos (id 5), sin categoria -> Sin categoria (id 6)
        assertEquals(3, pagina.getTotalDeElementos());
        assertEquals(List.of("Fideos", "Mate cocido", "Sin categoria"), nombres(pagina));
    }

    // ---------- Campos del DTO con @Columna ----------

    @ParameterizedTest
    @MethodSource("bases")
    void filtra_porElNombreDelDto_deUnCampoConColumna(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("categoriaUuid", "cat-alm")));

        assertEquals(List.of("Arroz", "Fideos"), nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void filtra_porElNombreDelDto_conOperadoresYColecciones(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("paisNombre", "like", "%urug%")));
        pedido.agregarFiltro(List.of(new FiltroDePaginado("etiquetas.nombre", "nuevo")));

        assertEquals(List.of("Arroz"), nombresOrdenados(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void ordena_porElNombreDelDto_igualQuePorLaRuta(String base) {
        PedidoDePaginado porDto = pedido(1, 20);
        porDto.getOrdenes().add(new OrdenDePaginado("paisNombre", "asc"));
        porDto.getOrdenes().add(new OrdenDePaginado("nombre", "desc"));
        PedidoDePaginado porRuta = pedido(1, 20);
        porRuta.getOrdenes().add(new OrdenDePaginado("categoria.pais.nombre", "asc"));
        porRuta.getOrdenes().add(new OrdenDePaginado("nombre", "desc"));

        assertEquals(nombres(paginar(base, porRuta)), nombres(paginar(base, porDto)));
        assertEquals(List.of("Sin categoria", "Yerba Mate", "Te", "Mate cocido", "Cafe", "Fideos", "Arroz"),
                nombres(paginar(base, porDto)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void group_porElNombreDelDto(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("categoriaUuid", "group", "")));
        pedido.getOrdenes().add(new OrdenDePaginado("nombre", "asc"));

        assertEquals(List.of("Fideos", "Mate cocido", "Sin categoria"), nombres(paginar(base, pedido)));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void siElNombreDelDtoTambienEsAtributoDeLaEntidad_ganaElAtributo(String base) {
        PedidoDePaginado pedido = pedido(1, 20);
        pedido.agregarFiltro(List.of(new FiltroDePaginado("nombre", "Cafe")));

        EntityManager em = BASES.get(base).createEntityManager();
        try {
            Pagina<DtoConNombreDeCategoria> pagina = new MotorDeConsultas<>(em).paginar(pedido,
                    DtoConNombreDeCategoria.class);

            // Filtra por el nombre del producto (atributo de la entidad), no por categoria.nombre
            assertEquals(1, pagina.getTotalDeElementos());
            assertEquals("p2", pagina.getContenido().get(0).uuid);
            assertEquals("Bebidas", pagina.getContenido().get(0).nombre);
        } finally {
            em.close();
        }
    }

    @ParameterizedTest
    @MethodSource("bases")
    void unNombreQueNoEsDelDtoNiDeLaEntidad_seRechaza(String base) {
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("categoriaNombre", "Bebidas")));
        PedidoDePaginado orden = pedido(1, 10);
        orden.getOrdenes().add(new OrdenDePaginado("categoriaNombre", "asc"));
        assertRechaza(base, orden);
    }

    // DTO cuyo campo nombre sale de categoria.nombre: el nombre coincide con un atributo de la entidad
    public static class DtoConNombreDeCategoria {
        private String uuid;

        @Columna("categoria.nombre")
        private String nombre;
    }

    // ---------- Seguridad y validaciones ----------

    @ParameterizedTest
    @MethodSource("bases")
    void rechaza_camposSensibles_inexistentes_eInyecciones(String base) {
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("secreto", "s1")));
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("id", "1")));
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("noExiste", "1")));
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("nombre) OR (1=1", "x")));
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("nombre", "= 'x' OR 1=1 OR t.nombre =", "x")));
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("categoria", "1")));
        assertRechaza(base, pedidoConFiltro(new FiltroDePaginado("stock", "no es numero")));

        PedidoDePaginado ordenSensible = pedido(1, 10);
        ordenSensible.getOrdenes().add(new OrdenDePaginado("secreto", "asc"));
        assertRechaza(base, ordenSensible);

        PedidoDePaginado ordenInyectado = pedido(1, 10);
        ordenInyectado.getOrdenes().add(new OrdenDePaginado("nombre", "asc, t.secreto"));
        assertRechaza(base, ordenInyectado);

        PedidoDePaginado ordenPorColeccion = pedido(1, 10);
        ordenPorColeccion.getOrdenes().add(new OrdenDePaginado("etiquetas.nombre", "asc"));
        assertRechaza(base, ordenPorColeccion);

        EntityManager em = BASES.get(base).createEntityManager();
        try {
            PedidoDePaginado pedido = pedido(1, 10);
            assertThrows(ExcepcionDeApi.class,
                    () -> new MotorDeConsultas<>(em).paginar(pedido, DtoConSecreto.class));
        } finally {
            em.close();
        }
    }

    @Test
    void elFrontNoPuedeMandarCondicionesNiEntidad() {
        String json = "{\"pagina\":1,\"tamano\":10,\"claseDeEntidad\":\"java.lang.String\","
                + "\"condiciones\":[{\"jpql\":\"1 = 1\",\"parametros\":{}}],"
                + "\"filtros\":[[{\"campo\":\"nombre\",\"valor\":\"x\"}]]}";

        PedidoDePaginado pedido = new ObjectMapper().readValue(json, PedidoDePaginado.class);

        assertTrue(pedido.getCondiciones().isEmpty());
        assertNull(pedido.getClaseDeEntidad());
        assertEquals("=", pedido.getFiltros().get(0).get(0).getOperacion());
    }

    // DTO que intenta leer un atributo @NoProyectable con @Columna
    public static class DtoConSecreto {
        @Columna("secreto")
        private String secreto;
    }

    // ---------- Utilidades ----------

    private static EntityManagerFactory crear(String nombre, String url, String dialecto) {
        HibernatePersistenceConfiguration configuracion = new HibernatePersistenceConfiguration("paginado-" + nombre);
        configuracion.managedClass(PruebaPais.class);
        configuracion.managedClass(PruebaCategoria.class);
        configuracion.managedClass(PruebaEtiqueta.class);
        configuracion.managedClass(PruebaProducto.class);
        configuracion.managedClass(PruebaVariante.class);
        configuracion.managedClass(PruebaCodigo.class);
        configuracion.property("jakarta.persistence.jdbc.url", url);
        configuracion.property("jakarta.persistence.schema-generation.database.action", "drop-and-create");
        if (dialecto != null) {
            configuracion.property("hibernate.dialect", dialecto);
        }
        return configuracion.createEntityManagerFactory();
    }

    private static void cargarDatos(EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        PruebaPais argentina = pais(em, "Argentina");
        PruebaPais uruguay = pais(em, "Uruguay");
        PruebaCategoria bebidas = categoria(em, "cat-beb", "Bebidas", argentina);
        PruebaCategoria almacen = categoria(em, "cat-alm", "Almacen", uruguay);
        PruebaEtiqueta oferta = etiqueta(em, "oferta");
        PruebaEtiqueta nuevo = etiqueta(em, "nuevo");
        producto(em, "p1", "Yerba Mate", "10.50", 5, true, LocalDateTime.of(2026, 1, 10, 10, 0), bebidas, oferta, nuevo);
        producto(em, "p2", "Cafe", "20", 10, true, LocalDateTime.of(2025, 12, 1, 0, 0), bebidas, oferta);
        producto(em, "p3", "Te", "5", 0, false, LocalDateTime.of(2026, 2, 1, 0, 0), bebidas);
        producto(em, "p4", "Arroz", "3", 7, true, LocalDateTime.of(2026, 3, 1, 0, 0), almacen, nuevo);
        producto(em, "p5", "Fideos", "4", 5, true, LocalDateTime.of(2025, 11, 1, 0, 0), almacen);
        producto(em, "p6", "Sin categoria", "1", 1, true, LocalDateTime.of(2026, 1, 1, 0, 0), null);
        producto(em, "p7", "Mate cocido", "2", 10, false, LocalDateTime.of(2026, 1, 20, 0, 0), bebidas, oferta);
        em.getTransaction().commit();
        em.close();
    }

    private static PruebaPais pais(EntityManager em, String nombre) {
        PruebaPais pais = new PruebaPais();
        pais.setNombre(nombre);
        em.persist(pais);
        return pais;
    }

    private static PruebaCategoria categoria(EntityManager em, String uuid, String nombre, PruebaPais pais) {
        PruebaCategoria categoria = new PruebaCategoria();
        categoria.setUuid(uuid);
        categoria.setNombre(nombre);
        categoria.setPais(pais);
        em.persist(categoria);
        return categoria;
    }

    private static PruebaEtiqueta etiqueta(EntityManager em, String nombre) {
        PruebaEtiqueta etiqueta = new PruebaEtiqueta();
        etiqueta.setNombre(nombre);
        em.persist(etiqueta);
        return etiqueta;
    }

    private static void producto(EntityManager em, String uuid, String nombre, String precio, int stock, boolean activo,
            LocalDateTime fechaAlta, PruebaCategoria categoria, PruebaEtiqueta... etiquetas) {
        PruebaProducto producto = new PruebaProducto();
        producto.setUuid(uuid);
        producto.setNombre(nombre);
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        producto.setActivo(activo);
        producto.setFechaAlta(fechaAlta);
        producto.setSecreto("secreto-" + uuid);
        producto.setCategoria(categoria);
        producto.getEtiquetas().addAll(List.of(etiquetas));
        em.persist(producto);
    }

    private static PedidoDePaginado pedido(int pagina, int tamano) {
        PedidoDePaginado pedido = new PedidoDePaginado();
        pedido.setClaseDeEntidad(PruebaProducto.class);
        pedido.setPagina(pagina);
        pedido.setTamano(tamano);
        return pedido;
    }

    private static PedidoDePaginado pedidoConFiltro(FiltroDePaginado filtro) {
        PedidoDePaginado pedido = pedido(1, 10);
        pedido.agregarFiltro(List.of(filtro));
        return pedido;
    }

    private static Pagina<PruebaProductoDTO> paginar(String base, PedidoDePaginado pedido) {
        EntityManager em = BASES.get(base).createEntityManager();
        try {
            return new MotorDeConsultas<>(em).paginar(pedido, PruebaProductoDTO.class);
        } finally {
            em.close();
        }
    }

    private static void assertRechaza(String base, PedidoDePaginado pedido) {
        assertThrows(ExcepcionDeApi.class, () -> paginar(base, pedido));
    }

    private static PruebaProductoDTO porNombre(Pagina<PruebaProductoDTO> pagina, String nombre) {
        return pagina.getContenido().stream().filter(dto -> nombre.equals(dto.getNombre())).findFirst().orElseThrow();
    }

    private static List<String> nombres(Pagina<PruebaProductoDTO> pagina) {
        return pagina.getContenido().stream().map(dto -> dto.getNombre()).toList();
    }

    private static List<String> nombresOrdenados(Pagina<PruebaProductoDTO> pagina) {
        return nombres(pagina).stream().sorted().toList();
    }
}
