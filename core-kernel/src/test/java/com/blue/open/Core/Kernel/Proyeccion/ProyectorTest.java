package com.blue.open.Core.Kernel.Proyeccion;

import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Paginado.PruebaCategoria;
import com.blue.open.Core.Kernel.Paginado.PruebaCodigo;
import com.blue.open.Core.Kernel.Paginado.PruebaEtiqueta;
import com.blue.open.Core.Kernel.Paginado.PruebaPais;
import com.blue.open.Core.Kernel.Paginado.PruebaProducto;
import com.blue.open.Core.Kernel.Paginado.PruebaVariante;
import com.blue.open.Core.Kernel.Repository.MotorDeConsultas;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test del Proyector (lectura por uuid, @Coleccion y aplicar un DTO) contra una base real, con el modelo
// PruebaProducto -> PruebaVariante -> PruebaCodigo. Corre sobre H2 en modo MariaDB y en modo PostgreSQL, igual que
// ConsultaDePaginadoTest (prior art), y opcionalmente contra las bases reales de PAGINADO_MARIADB_URL / PAGINADO_POSTGRES_URL.
class ProyectorTest {

    private static final Map<String, EntityManagerFactory> BASES = new LinkedHashMap<>();

    @BeforeAll
    static void levantarBases() {
        BASES.put("h2-mariadb", crear("h2-mariadb",
                "jdbc:h2:mem:proyector_maria;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "org.hibernate.dialect.MariaDBDialect"));
        BASES.put("h2-postgres", crear("h2-postgres",
                "jdbc:h2:mem:proyector_pg;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "org.hibernate.dialect.PostgreSQLDialect"));
        String mariadb = System.getenv("PAGINADO_MARIADB_URL");
        if (mariadb != null && !mariadb.isBlank()) {
            BASES.put("mariadb", crear("mariadb", mariadb, null));
        }
        String postgres = System.getenv("PAGINADO_POSTGRES_URL");
        if (postgres != null && !postgres.isBlank()) {
            BASES.put("postgres", crear("postgres", postgres, null));
        }
        BASES.values().forEach(ProyectorTest::cargarDatos);
    }

    @AfterAll
    static void cerrarBases() {
        BASES.values().forEach(emf -> emf.close());
    }

    static List<String> bases() {
        return new ArrayList<>(BASES.keySet());
    }

    // ---------- Lectura por uuid ----------

    @ParameterizedTest
    @MethodSource("bases")
    void porUuid_proyectaCamposRelacionesYColeccionesAnidadas(String base) {
        DtosDeProyeccion.Producto yerba = porUuid(base, "p1", DtosDeProyeccion.Producto.class).orElseThrow();

        assertEquals("p1", yerba.getUuid());
        assertEquals("Yerba Mate", yerba.getNombre());
        assertEquals("cat-beb", yerba.getCategoriaUuid());
        assertEquals("Argentina", yerba.getPaisNombre());
        assertNull(yerba.getSecreto(), "un atributo @NoProyectable nunca se proyecta");
        assertNull(yerba.getCalculado(), "un campo sin atributo queda en su valor por defecto");

        // Variantes por id ascendente (orden por defecto), codigos por valor (orden declarado)
        assertEquals(List.of("Kilo", "Medio"), yerba.getVariantes().stream().map(v -> v.getNombre()).toList());
        DtosDeProyeccion.Variante kilo = yerba.getVariantes().get(0);
        assertEquals("v1", kilo.getUuid());
        assertEquals(List.of("A", "B"), kilo.getCodigos().stream().map(c -> c.getValor()).toList());
        assertEquals(List.of("C"), yerba.getVariantes().get(1).getCodigos().stream().map(c -> c.getValor()).toList());
        assertNull(kilo.getSecreto(), "@NoProyectable tampoco se completa en un sub-DTO");
        assertNull(kilo.getOculto(), "@JsonIgnore tampoco se completa en un sub-DTO");

        assertEquals(List.of("aaa", "nuevo", "oferta"), yerba.getEtiquetas().stream().map(e -> e.getNombre()).toList());
    }

    @ParameterizedTest
    @MethodSource("bases")
    void porUuid_conUnaConsultaPorColeccion_sinCargarLaEntidad(String base) {
        Statistics estadisticas = estadisticas(base);
        estadisticas.clear();

        porUuid(base, "p1", DtosDeProyeccion.Producto.class).orElseThrow();

        // Registro + variantes + codigos + etiquetas
        assertEquals(4, estadisticas.getQueryExecutionCount());
        assertEquals(0, estadisticas.getEntityLoadCount(), "la Proyeccion no carga entidades");
    }

    @ParameterizedTest
    @MethodSource("bases")
    void porUuid_inexistente_respondeVacio(String base) {
        assertTrue(porUuid(base, "no-existe", DtosDeProyeccion.Producto.class).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("bases")
    void coleccionSinElementos_respondeListaVacia(String base) {
        DtosDeProyeccion.Producto te = porUuid(base, "p3", DtosDeProyeccion.Producto.class).orElseThrow();

        assertNull(te.getCategoriaUuid());
        assertEquals(List.of(), te.getVariantes());
        assertEquals(List.of(), te.getEtiquetas());
    }

    @ParameterizedTest
    @MethodSource("bases")
    void coleccion_respetaElOrdenDeclarado(String base) {
        DtosDeProyeccion.ProductoConVariantesPorOrden yerba = porUuid(base, "p1",
                DtosDeProyeccion.ProductoConVariantesPorOrden.class).orElseThrow();

        assertEquals(List.of("Medio", "Kilo"), yerba.getVariantes().stream().map(v -> v.getNombre()).toList());
    }

    // ---------- Paginado ----------

    @ParameterizedTest
    @MethodSource("bases")
    void paginado_completaColecciones_conUnaConsultaPorAnotacion(String base) {
        Statistics estadisticas = estadisticas(base);
        estadisticas.clear();

        List<DtosDeProyeccion.Producto> pagina = paginar(base, null);

        // Conteo + pagina + variantes + codigos + etiquetas, sin importar cuantos productos haya
        assertEquals(5, estadisticas.getQueryExecutionCount());
        assertEquals(6, pagina.size());
        assertEquals(List.of("Kilo", "Medio"), porUuid(pagina, "p1").getVariantes().stream().map(v -> v.getNombre()).toList());
        assertEquals(List.of("Grano"), porUuid(pagina, "p2").getVariantes().stream().map(v -> v.getNombre()).toList());
        assertEquals(List.of(), porUuid(pagina, "p3").getVariantes());
    }

    @ParameterizedTest
    @MethodSource("bases")
    void inPartidoEnLotes_respondeLoMismo(String base) {
        String sinLotes = json(paginar(base, null));
        String conLotesDeUno = json(paginar(base, 1));
        String conLotesDeDos = json(paginar(base, 2));

        assertEquals(sinLotes, conLotesDeUno);
        assertEquals(sinLotes, conLotesDeDos);
    }

    // ---------- Validacion de la proyeccion ----------

    @ParameterizedTest
    @MethodSource("bases")
    void validar_aceptaUnaProyeccionCorrecta(String base) {
        conEntityManager(base, em -> new Proyector(em).validar(PruebaProducto.class, DtosDeProyeccion.Producto.class));
    }

    @ParameterizedTest
    @MethodSource("bases")
    void validar_rechazaUnCicloDeDtos(String base) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> conEntityManager(base,
                em -> new Proyector(em).validar(PruebaProducto.class, DtosDeProyeccion.Ciclo.class)));
        assertTrue(error.getMessage().contains("ciclo"), error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("bases")
    void validar_rechazaRutasYColeccionesInexistentes(String base) {
        for (Class<?> dto : List.of(DtosDeProyeccion.ColeccionInexistente.class,
                DtosDeProyeccion.ColeccionSobreRelacion.class, DtosDeProyeccion.ColumnaInexistente.class)) {
            assertThrows(IllegalStateException.class,
                    () -> conEntityManager(base, em -> new Proyector(em).validar(PruebaProducto.class, dto)),
                    dto.getSimpleName());
        }
    }

    // ---------- Aplicar un DTO ----------

    @ParameterizedTest
    @MethodSource("bases")
    void aplicar_copiaSimples_asignaRelaciones_eIgnoraLoDemas(String base) {
        enTransaccionRevertida(base, em -> {
            PruebaProducto cafe = producto(em, "p2");
            Long id = cafe.getId();
            PruebaProductoGuardarDTO dto = guardar("Cafe molido");
            dto.setCategoriaUuid("cat-beb");

            new Proyector(em).aplicar(dto, cafe, CodigoDeErrorKernel.ERROR_INTERNO);

            assertEquals("Cafe molido", cafe.getNombre());
            assertEquals(0, new BigDecimal("25").compareTo(cafe.getPrecio()));
            assertEquals(3, cafe.getStock());
            assertEquals(false, cafe.getActivo());
            assertEquals("cat-beb", cafe.getCategoria().getUuid(), "la relacion se busca por uuid y se asigna");
            assertEquals("secreto-p2", cafe.getSecreto(), "un atributo @NoProyectable no se copia");
            assertEquals(id, cafe.getId(), "un atributo @JsonIgnore no se copia");
            assertEquals(1, cafe.getVariantes().size(), "una @Coleccion no se escribe");
            assertEquals(7, cafe.getVendidos(), "sin setter publico solo lo completa aplicar(dto)");
            assertEquals("Cafe molido", cafe.getNombreAlAplicar(), "aplicar(dto) se llama despues de la copia");
        });
    }

    @ParameterizedTest
    @MethodSource("bases")
    void aplicar_conUuidNull_desasociaLaRelacion(String base) {
        enTransaccionRevertida(base, em -> {
            PruebaProducto cafe = producto(em, "p2");

            new Proyector(em).aplicar(guardar("Cafe"), cafe, CodigoDeErrorKernel.ERROR_INTERNO);

            assertNull(cafe.getCategoria());
        });
    }

    @ParameterizedTest
    @MethodSource("bases")
    void aplicar_conRelacionInexistente_lanzaElCodigoConElCampo_ySinModificarNada(String base) {
        enTransaccionRevertida(base, em -> {
            PruebaProducto cafe = producto(em, "p2");
            PruebaProductoGuardarDTO dto = guardar("Otro nombre");
            dto.setCategoriaUuid("no-existe");

            ExcepcionDeApi error = assertThrows(ExcepcionDeApi.class,
                    () -> new Proyector(em).aplicar(dto, cafe, CodigoDeErrorKernel.ERROR_INTERNO));

            assertEquals(CodigoDeErrorKernel.ERROR_INTERNO, error.getCodigo());
            assertEquals("categoriaUuid", error.getDatos().get("campo"));
            assertEquals("Cafe", cafe.getNombre());
        });
    }

    @ParameterizedTest
    @MethodSource("bases")
    void aplicar_sobreUnRegistroEliminado_conStatusActivo_noLoReactiva(String base) {
        enTransaccionRevertida(base, em -> {
            PruebaEliminable eliminado = new PruebaEliminable();
            eliminado.setNombre("Eliminado");
            eliminado.setStatus(-1);
            em.persist(eliminado);
            em.flush();
            PruebaEliminableDTO dto = new PruebaEliminableDTO();
            dto.setUuid(eliminado.getUuid());
            dto.setNombre("Reactivado");
            dto.setStatus(0);

            ExcepcionDeApi error = assertThrows(ExcepcionDeApi.class,
                    () -> new Proyector(em).aplicar(dto, eliminado, CodigoDeErrorKernel.ERROR_INTERNO));

            assertEquals(CodigoDeErrorKernel.BORRADO_LOGICO_TRANSICION_INVALIDA, error.getCodigo(),
                    "la transicion -1 -> 0 la rechaza aplicarCamposBase, que llama el Proyector sin ningun mapper");
            assertEquals(-1, eliminado.getStatus());
        });
    }

    // ---------- Utilidades ----------

    private static EntityManagerFactory crear(String nombre, String url, String dialecto) {
        HibernatePersistenceConfiguration configuracion = new HibernatePersistenceConfiguration("proyector-" + nombre);
        configuracion.managedClass(PruebaPais.class);
        configuracion.managedClass(PruebaCategoria.class);
        configuracion.managedClass(PruebaEtiqueta.class);
        configuracion.managedClass(PruebaProducto.class);
        configuracion.managedClass(PruebaVariante.class);
        configuracion.managedClass(PruebaCodigo.class);
        configuracion.managedClass(PruebaEliminable.class);
        configuracion.property("jakarta.persistence.jdbc.url", url);
        configuracion.property("jakarta.persistence.schema-generation.database.action", "drop-and-create");
        configuracion.property("hibernate.generate_statistics", "true");
        if (dialecto != null) {
            configuracion.property("hibernate.dialect", dialecto);
        }
        return configuracion.createEntityManagerFactory();
    }

    private static void cargarDatos(EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        PruebaPais argentina = new PruebaPais();
        argentina.setNombre("Argentina");
        em.persist(argentina);
        PruebaCategoria bebidas = categoria(em, "cat-beb", "Bebidas", argentina);
        PruebaCategoria almacen = categoria(em, "cat-alm", "Almacen", null);
        PruebaEtiqueta oferta = etiqueta(em, "oferta");
        PruebaEtiqueta nuevo = etiqueta(em, "nuevo");
        PruebaEtiqueta aaa = etiqueta(em, "aaa");

        PruebaProducto yerba = producto(em, "p1", "Yerba Mate", bebidas, oferta, nuevo, aaa);
        variante(em, yerba, "v1", "Kilo", 2, "B", "A");
        variante(em, yerba, "v2", "Medio", 1, "C");
        PruebaProducto cafe = producto(em, "p2", "Cafe", almacen, oferta);
        variante(em, cafe, "v3", "Grano", 1);
        producto(em, "p3", "Te", null);
        for (int i = 4; i <= 6; i++) {
            PruebaProducto otro = producto(em, "p" + i, "Producto " + i, almacen, nuevo);
            variante(em, otro, "v" + (i + 10), "Unica " + i, 1, "Z" + i);
        }
        em.getTransaction().commit();
        em.close();
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

    private static PruebaProducto producto(EntityManager em, String uuid, String nombre, PruebaCategoria categoria,
            PruebaEtiqueta... etiquetas) {
        PruebaProducto producto = new PruebaProducto();
        producto.setUuid(uuid);
        producto.setNombre(nombre);
        producto.setPrecio(BigDecimal.TEN);
        producto.setStock(1);
        producto.setActivo(true);
        producto.setSecreto("secreto-" + uuid);
        producto.setCategoria(categoria);
        producto.getEtiquetas().addAll(List.of(etiquetas));
        em.persist(producto);
        return producto;
    }

    private static void variante(EntityManager em, PruebaProducto producto, String uuid, String nombre, int orden,
            String... codigos) {
        PruebaVariante variante = new PruebaVariante();
        variante.setUuid(uuid);
        variante.setNombre(nombre);
        variante.setOrden(orden);
        variante.setSecreto("secreto-" + uuid);
        variante.setOculto("oculto-" + uuid);
        variante.setProducto(producto);
        em.persist(variante);
        for (String valor : codigos) {
            PruebaCodigo codigo = new PruebaCodigo();
            codigo.setValor(valor);
            codigo.setVariante(variante);
            em.persist(codigo);
        }
    }

    private static PruebaProductoGuardarDTO guardar(String nombre) {
        PruebaProductoGuardarDTO dto = new PruebaProductoGuardarDTO();
        dto.setNombre(nombre);
        dto.setPrecio(new BigDecimal("25"));
        dto.setStock(3);
        dto.setActivo(false);
        dto.setSecreto("pisado");
        dto.setId(999L);
        dto.setCalculado("x");
        dto.setVendidos(7);
        dto.setPaisNombre("Uruguay");
        dto.setVariantes(List.of());
        return dto;
    }

    private static PruebaProducto producto(EntityManager em, String uuid) {
        return em.createQuery("SELECT p FROM PruebaProducto p WHERE p.uuid = :uuid", PruebaProducto.class)
                .setParameter("uuid", uuid).getSingleResult();
    }

    private static <D> Optional<D> porUuid(String base, String uuid, Class<D> claseDto) {
        EntityManager em = BASES.get(base).createEntityManager();
        try {
            return new Proyector(em).porUuid(PruebaProducto.class, uuid, claseDto);
        } finally {
            em.close();
        }
    }

    // Pagina completa de productos. lote: tamano del IN de las colecciones, o null para el del Core
    private static List<DtosDeProyeccion.Producto> paginar(String base, Integer lote) {
        EntityManager em = BASES.get(base).createEntityManager();
        try {
            PedidoDePaginado pedido = new PedidoDePaginado();
            pedido.setClaseDeEntidad(PruebaProducto.class);
            pedido.setPagina(1);
            pedido.setTamano(20);
            MotorDeConsultas<?> motor = lote == null ? new MotorDeConsultas<>(em) : new MotorDeConsultas<>(em, lote);
            return motor.paginar(pedido, DtosDeProyeccion.Producto.class).getContenido();
        } finally {
            em.close();
        }
    }

    private static DtosDeProyeccion.Producto porUuid(List<DtosDeProyeccion.Producto> pagina, String uuid) {
        return pagina.stream().filter(dto -> uuid.equals(dto.getUuid())).findFirst().orElseThrow();
    }

    private static String json(Object valor) {
        return new ObjectMapper().writeValueAsString(valor);
    }

    private static Statistics estadisticas(String base) {
        return BASES.get(base).unwrap(SessionFactory.class).getStatistics();
    }

    private static void conEntityManager(String base, Consumer<EntityManager> accion) {
        EntityManager em = BASES.get(base).createEntityManager();
        try {
            accion.accept(em);
        } finally {
            em.close();
        }
    }

    private static void enTransaccionRevertida(String base, Consumer<EntityManager> accion) {
        conEntityManager(base, em -> {
            em.getTransaction().begin();
            try {
                accion.accept(em);
            } finally {
                em.getTransaction().rollback();
            }
        });
    }
}
