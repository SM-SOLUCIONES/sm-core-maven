# Motor de Proyección (`core-kernel`)

Paquete `com.blue.open.Core.Kernel.Proyeccion`. Es el camino por defecto para **leer** y **escribir** una entidad a
partir de su DTO, sin una clase por entidad que copie campos. Lo usan el ABM de `core-crud` (`AbstractService`) y
cualquier caso de uso que inyecte el bean `Proyector`. La decisión está en
[ADR 0005](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/adr/0005-proyeccion-en-vez-de-mappers.md).

## Piezas

| Pieza | Qué es |
|---|---|
| `@Columna("ruta")` | De qué atributo sale un campo del DTO cuando no se llama igual o viene de una relación |
| `@Coleccion(value, orden)` | Un campo `List<SubDTO>` que se llena con los elementos de una colección de la entidad |
| `@NoProyectable` | Excluye un atributo (en la entidad) o un campo (en el DTO) de la lectura y de la copia |
| `AplicaDto<DTO>` | Interfaz que implementa una entidad para completar, al guardar, lo que no se copia solo |
| `Proyector` | Bean público: lee por uuid, completa colecciones, aplica un DTO y valida una proyección |
| `ProyeccionDeDto` | Decide, por reflexión y una sola vez por par entidad/DTO, qué campos se leen y cuáles son colecciones |

`ConsultaDePaginado` (paquete `Paginado`) arma el JPQL y `MotorDeConsultas` lo ejecuta: el paginado, la lectura por
uuid y las colecciones pasan por el mismo motor, en MariaDB y en PostgreSQL.

## Lectura: la Proyección

El DTO se arma directamente desde la consulta, con solo las columnas de sus campos. Nunca se carga la entidad.

- **Campo con el nombre de un atributo básico** de la entidad: se trae solo.
- **`@Columna("relacion.atributo")`**: navega `@ManyToOne` / `@OneToOne` con `LEFT JOIN` (una relación nula deja el
  campo en `null`). Ejemplo: `@Columna("tipoVehiculo.uuid") String tipoVehiculoUuid`.
- **`@Coleccion("atributo")`** sobre un `List<SubDTO>`: trae los elementos de una `@OneToMany` / `@ManyToMany`.
- **Campo sin atributo ni anotación**: queda en su valor por defecto. Sirve para un dato calculado que completa un
  hook (`AbstractService.hookPaginarCompletarResultados`).
- **Nunca se leen**: atributos `@NoProyectable` o `@JsonIgnore` de la entidad, y campos `@NoProyectable` del DTO.

### Colecciones

```java
public class RolDTO extends EntidadBaseDTO {
    private String nombre;
    @Coleccion("permisos")
    private List<PermisoDTO> permisos;
}
```

- **Una consulta por anotación**, para todos los registros del nivel padre: `IN` sobre sus ids, partido en lotes de
  `MotorDeConsultas.TAMANO_DE_LOTE` (500) para no superar el límite de parámetros de PostgreSQL. No hay N+1.
- **Sin límite de niveles**: el `SubDTO` sigue las mismas reglas que un DTO raíz (campos por nombre, `@Columna`,
  exclusiones) y puede tener sus propias `@Coleccion`. `usuario → empresas → roles → permisos` son tres consultas,
  sin importar cuántos usuarios tenga la página.
- **Orden**: `orden = "atributo"` del elemento, ascendente; por defecto, id ascendente.
- **Vigentes**: si el elemento tiene borrado lógico (`EntidadConBorradoLogico`), solo trae los de `status = 0`.
- **Lista vacía, nunca `null`**: un registro sin elementos responde `[]`.
- **Solo lectura**: al guardar, una `@Coleccion` se ignora. Una colección se escribe por hook o por caso de uso.
- **Costo**: cada nivel es una consulta más, y una consulta hoja de una `@Coleccion` anidada profunda puede traer
  muchas filas (padres × elementos). Ese costo es responsabilidad de quien diseña el DTO.

## Escritura: aplicar un DTO

`Proyector.aplicar(dto, entidad, codigoSiNoExisteLaRelacion)` copia el DTO sobre la entidad ya cargada (o recién
creada). Es lo que hace `AbstractService.hookGuardarAplicarDto` en `POST /guardar`.

1. **Relaciones primero**: por cada campo `@Columna("relacion.uuid")` sobre una `@ManyToOne` / `@OneToOne`, busca la
   entidad relacionada **vigente** por uuid. Si no existe, lanza el código recibido (en el ABM,
   `RELACION_NO_ENCONTRADA`, 400) con `datos.campo` = nombre del campo del DTO, sin haber modificado nada. `null`
   desasocia.
2. **Copia**: asigna los campos simples del DTO con el mismo nombre que un atributo básico de la entidad, por su
   setter público, convertidos al tipo del atributo.
3. **`aplicarCamposBase(dto)`**: valida las transiciones de `status` (no se reactiva un registro eliminado).
4. **`AplicaDto.aplicar(dto)`**: si la entidad lo implementa para el tipo de ese DTO, se llama después de la copia.

Lo que **no** se copia nunca:

- Los campos de control de `EntidadBaseDTO` (`uuid`, `status`, `fechaDeCreacion`): los resuelve `aplicarCamposBase` y el alta.
- Atributos sin setter público (`@Setter(AccessLevel.NONE)`): son los que cambian por un método de negocio.
- Atributos `@NoProyectable` o `@JsonIgnore` de la entidad y campos `@NoProyectable` del DTO. La contraseña de
  `UsuarioBase` es `@NoProyectable`: la escribe `UsuarioBaseService`, hasheada.
- Rutas más largas que `relacion.uuid` y las `@Coleccion`: son de solo lectura.

```java
@Entity
public class Producto extends EntidadConBorradoLogico implements AplicaDto<ProductoDTO> {

    // Solo cambia por metodos de negocio: la copia generica no lo toca
    @Setter(AccessLevel.NONE)
    private Integer stock = 0;

    // Despues de la copia: en el alta, el stock inicial sale del DTO pasando por la regla de la entidad
    @Override
    public void aplicar(ProductoDTO dto) {
        if (getId() == null && dto.getStockInicial() != null) {
            ajustarStock(dto.getStockInicial());
        }
    }
}
```

`aplicar` no recibe dependencias (la entidad no es un bean): lo que necesita un repositorio o un service va en el
hook del service (`hookGuardarAplicarDto`).

## Validación al arrancar

`Proyector.validar(entidad, dto)` arma las consultas sin ejecutarlas y falla con `IllegalStateException` ante una
ruta de `@Columna` o `@Coleccion` inexistente, una `@Coleccion` que no apunta a una colección de entidades, un campo
`@Coleccion` que no es `List<SubDTO>` o un ciclo de DTOs (`A` con `@Coleccion` de `B` y `B` de `A`). En `core-crud`,
`ValidadorDeProyecciones` lo corre para los DTOs de todos los `AbstractService` del contexto: un DTO mal declarado
corta el arranque, no el primer request.

## Desde un caso de uso

```java
@Service
@RequiredArgsConstructor
public class ProductoActualizarStockUseCase {

    private final ProductoRepository repository;

    private final Proyector proyector;

    @Transactional
    public ProductoStockResponseDTO ejecutar(String uuid, ProductoActualizarStockRequestDTO dto) {
        Producto modelo = repository.buscarVigentePorUuid(uuid)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorDelProyecto.REGISTRO_NO_ENCONTRADO));
        modelo.ajustarStock(dto.getStock());
        repository.save(modelo);
        return proyector.porUuid(Producto.class, uuid, ProductoStockResponseDTO.class)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorDelProyecto.REGISTRO_NO_ENCONTRADO));
    }
}
```

Si la lectura no encaja en una Proyección (agregaciones, filtros que no son por uuid, SQL propio del motor), es un
**caso de uso con consulta propia**: escribe su `@Query` con `SELECT new` en el repositorio (ver
[consultas.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/consultas.md)). `Proyector.completarColecciones(entidad, dtos)` completa después las
`@Coleccion` de esos DTOs.
