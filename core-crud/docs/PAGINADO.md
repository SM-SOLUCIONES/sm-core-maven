# Sistema de Paginado - Core

## Descripción General

`POST /{controller}/paginar` es el listado genérico del Core: filtros con grupos AND/OR, ordenamiento, navegación por relaciones y agrupación. La lógica está en las clases base (`AbstractService`, `EntidadRepositoryBase` y `MotorDeConsultas`); el endpoint lo escribe el controller de la entidad con la [receta](receta-controller.md) y solo delega.

Desde Core 2.0.2 el paginado:

- **Se arma en JPQL, no en SQL nativo.** Hibernate genera el SQL de cada motor, así que el mismo pedido responde igual en MariaDB y en PostgreSQL. El test `ConsultaDePaginadoTest` (core-kernel) lo verifica contra los dos.
- **Trae solo las columnas del DTO de paginado.** No hace `select *` ni carga entidades: el `SELECT` lista únicamente los campos del `PAGINADODTO` y el Core llena el DTO directamente con lo que devuelve la base (la misma Proyección que `GET /uuid`, ver [obtener.md](obtener.md)). Desde Core 3.0.2 el repositorio devuelve los DTOs con sus `@Coleccion` ya completas.
- **Puede traer datos de entidades relacionadas.** Un campo del DTO puede salir de otra entidad (`@Columna("categoria.nombre")`) y los filtros y órdenes pueden navegar relaciones (`"campo": "categoria.pais.nombre"`). Los `JOIN` se arman solos.
- **Valida todo lo que manda el front** contra el metamodelo de JPA: no se puede inyectar JPQL ni leer atributos sensibles.

---

## Estructura de Componentes

### 1. Controlador (`{Entidad}Controller`, [receta](receta-controller.md))

**Endpoint**: `POST /{controller}/paginar`

- Recibe el `PedidoDePaginado` desde el cliente.
- Delega en el servicio y responde `ResponseEntity<Pagina<PAGINADODTO>>`. No tiene hooks.

**Seguridad**: la declara el `permiso` del `@Contrato` del método y la hace cumplir `InterceptorDeContrato` antes de ejecutarlo (con `core-identity`, `AutorizadorPorRolesYPermisos`; sin `core-identity`, `AutorizadorDenegarTodo`, que solo deja pasar `Contrato.PUBLICO`). La autorización que depende de los datos va en el `hookPaginarAuth()` del service.

### 2. Servicio (`AbstractService`)

Flujo de `paginar()`:

```
hookPaginarAuth(pedido)                  autorización por datos (vacío por defecto)
hookPaginarFiltrarStatus(pedido)          status = 0 si la entidad tiene borrado lógico
pedido.setClaseDeEntidad(...)                la entidad sale del genérico T
hookPaginarAntesDeConsultar(pedido)              filtros/condiciones propias del service (opcional)
hookPaginarEjecutarConsulta(pedido)          consulta JPQL -> Pagina<PAGINADODTO>, con sus @Coleccion
hookPaginarCompletarResultados(dtos)        escape: campos que no salen de la Proyección ni de una @Coleccion
hookPaginarFiltrarResultados(dtos)              filtrar en memoria (opcional)
hookPaginarCrearPagina(pagina, dtos)     armar la respuesta
```

Firmas de los hooks:

```java
protected void hookPaginarAuth(PedidoDePaginado pedido)
protected PedidoDePaginado hookPaginarFiltrarStatus(PedidoDePaginado pedido)
protected PedidoDePaginado hookPaginarAntesDeConsultar(PedidoDePaginado pedido)
protected Pagina<PAGINADODTO> hookPaginarEjecutarConsulta(PedidoDePaginado pedido)
protected List<PAGINADODTO> hookPaginarCompletarResultados(List<PAGINADODTO> dtos)
protected List<PAGINADODTO> hookPaginarFiltrarResultados(List<PAGINADODTO> dtos)
protected Pagina<PAGINADODTO> hookPaginarCrearPagina(Pagina<PAGINADODTO> pagina, List<PAGINADODTO> dtos)
```

`hookPaginarAntesDeConsultar()` ya no es obligatorio: hasta 2.0.1 había que llamar a `pedido.init("tabla")`; ahora la entidad se toma del genérico del service.

### 3. Repositorio (`EntidadRepositoryBase` → `MotorDeConsultas`)

Todos los repositorios exponen:

```java
<D> Pagina<D> paginar(PedidoDePaginado pedido, Class<D> claseDto);
```

`MotorDeConsultas` usa `ConsultaDePaginado` (core-kernel, paquete `Paginado`) para armar dos consultas JPQL, una de conteo y otra de la página, y las ejecuta con `setFirstResult`/`setMaxResults`. Si el conteo da 0, no ejecuta la segunda.

---

## DTO de Paginado: qué columnas se traen

El `PAGINADODTO` del service (tercer genérico de `AbstractService`) define el `SELECT`. `ProyeccionDeDto` recorre sus campos, incluidos los heredados (`uuid`, `status` y `fechaDeCreacion` de `EntidadBaseDTO`):

| Campo del DTO | Qué se trae |
|---|---|
| Se llama igual que un atributo simple de la entidad | Ese atributo (`t.nombre`) |
| Anotado con `@Columna("ruta")` | La ruta indicada; puede cruzar relaciones `@ManyToOne` / `@OneToOne` (`LEFT JOIN` automático) |
| Anotado con `@Coleccion(value = "atributo", orden = "...")` sobre un `List<SubDTO>` | Los elementos de la `@OneToMany` / `@ManyToMany`, con una consulta aparte por anotación para toda la página (ver [obtener.md](obtener.md)) |
| Anotado con `@NoProyectable` | Nada: queda en su valor por defecto |
| Sin atributo equivalente ni anotación (ej: un dato calculado) | Nada: se puede completar en `hookPaginarCompletarResultados` |
| Coincide con un atributo `@NoProyectable` o `@JsonIgnore` de la entidad | Nada (ej: `contrasena`, `id`) |

```java
@Getter
@Setter
public class VehiculoPaginateDTO extends EntidadBaseDTO {
    private String patente;                       // t.patente
    private Integer anio;                         // t.anio

    @Columna("tipoVehiculo.uuid")
    private String tipoVehiculoUuid;              // LEFT JOIN t.tipoVehiculo -> .uuid

    @Columna("tipoVehiculo.nombre")
    private String tipoVehiculoNombre;            // reutiliza el mismo JOIN

    @Columna("empresa.pais.nombre")
    private String paisNombre;                    // dos niveles de relación

    @Coleccion(value = "etiquetas", orden = "nombre")
    private List<EtiquetaDTO> etiquetas;          // consulta aparte, para toda la página
}
```

JPQL generado:

```sql
SELECT t.patente, t.anio, t_j1.uuid, t_j1.nombre, t_j3.nombre, t.uuid, t.status, t.fechaDeCreacion
FROM Vehiculo t
LEFT JOIN t.tipoVehiculo t_j1
LEFT JOIN t.empresa t_j2
LEFT JOIN t_j2.pais t_j3
WHERE (t.status = :pg_0)
ORDER BY t.id ASC
```

Reglas:

- Las rutas usan **nombres JPA** (atributos de la clase), no nombres de columna ni de tabla.
- Desde Core 3.5.0, filtros, órdenes y `group` aceptan también el **nombre de un campo del DTO anotado con `@Columna`**: se traduce a su ruta antes de validarla (ver "Campos del DTO con `@Columna`").
- Solo se navegan asociaciones mapeadas en la entidad (`@ManyToOne`, `@OneToOne`, `@Embedded`). Una tabla sin asociación JPA no se puede proyectar: se agrega la asociación, o se completa el campo en `hookPaginarCompletarResultados`.
- Las colecciones (`@OneToMany`, `@ManyToMany`) no entran en la consulta de la página: una fila por registro es lo que permite paginar bien. Si el listado necesita una lista, se declara con `@Coleccion` y el Core la completa con **una consulta por anotación para toda la página** (`IN` sobre los ids, en lotes de 500), nunca una por registro (ver `UsuarioBaseMultiempresaDTO`). Una `@Coleccion` anidada profunda multiplica filas en la consulta del último nivel: es responsabilidad de quien diseña el DTO.
- El DTO necesita un constructor sin argumentos.
- Si el tipo del campo no coincide con el del atributo (ej: `int` contra `Integer`), se convierte solo.

### Atributos protegidos: `@NoProyectable` y `@JsonIgnore`

- `@NoProyectable` en un atributo de la entidad: no se puede filtrar, ordenar ni proyectar, y al guardar no se copia del DTO. Se usa en datos sensibles (`UsuarioBase.contrasena`, `codigoDeVerificacion`, `AccessToken.codigo`).
- `@NoProyectable` en un campo del DTO: no se completa aunque exista un atributo con ese nombre.
- Los atributos `@JsonIgnore` de la entidad (`id`, `idUsuarioDeCreacion`, `idUsuarioDeActualizacion`, `idUsuarioDeBorrado`) no se pueden filtrar ni proyectar, pero sí ordenar (`"campo": "id"` para "más nuevos primero").

---

## DTOs de Entrada

### PedidoDePaginado

```java
public class PedidoDePaginado {
    private int pagina = 0;                            // Número de página (empieza en 1)
    private int tamano = 10;                           // Registros por página

    @JsonIgnore
    private Class<?> claseDeEntidad;                   // La completa el service
    @JsonIgnore
    private List<CondicionDePaginado> condiciones;       // Condiciones JPQL del backend

    private List<ParametroDePaginado> parametros;        // Parámetros para hooks propios
    private List<List<FiltroDePaginado>> filtros;        // OR dentro de cada grupo, AND entre grupos
    private List<OrdenDePaginado> ordenes;               // Ordenamiento
}
```

Métodos: `agregarFiltro(List<FiltroDePaginado>)`, `agregarCondicion(String jpql, Map<String, Object> parametros)`, `agregarParametro(...)`, `getParametroPorNombre(...)`.

`pagina` y `tamano` tienen que ser mayores a 0.

### FiltroDePaginado

```java
public class FiltroDePaginado {
    private String campo = "";       // Ruta JPA: "nombre", "categoria.nombre", "etiquetas.nombre"
    private String operacion = "=";  // Ver tabla de operadores
    private String valor = "";       // Siempre texto; se convierte al tipo del atributo
}

new FiltroDePaginado("nombre", "Juan")              // t.nombre = 'Juan'
new FiltroDePaginado("stock", ">", "5")              // t.stock > 5
new FiltroDePaginado("categoria.nombre", "Bebidas")  // LEFT JOIN t.categoria ... = 'Bebidas'
```

### OrdenDePaginado

```java
public class OrdenDePaginado {
    private String campo;       // Ruta JPA
    private String direccion;   // "asc" (por defecto) o "desc"
}
```

### CondicionDePaginado (solo backend)

Condición JPQL libre que agrega el service en `hookPaginarAntesDeConsultar()`. Sirve para lo que un filtro no puede expresar, por ejemplo restringir por una tabla sin asociación JPA. El front no puede mandarla (`@JsonIgnore`). Usa el alias `t` para la entidad paginada; sus parámetros no pueden empezar con `pg_`, que es un prefijo reservado.

```java
@Override
protected PedidoDePaginado hookPaginarAntesDeConsultar(PedidoDePaginado pedido) {
    pedido.agregarCondicion(
            "t.id IN (SELECT ue.idUsuario FROM UsuarioEmpresa ue WHERE ue.idEmpresa = :empresaActual)",
            Map.of("empresaActual", idEmpresa));
    return pedido;
}
```

### ParametroDePaginado

Par `nombre`/`valor` que el front puede mandar para que lo lea un hook propio del service. El motor no lo usa.

---

## Operadores Soportados

| Operador | JPQL | Notas |
|----------|------|-------|
| `=` | `=` | Por defecto si no se manda `operacion` |
| `!=` o `<>` | `<>` | |
| `>`, `<`, `>=`, `<=` | igual | Números, fechas, textos |
| `like` / `not like` | `LOWER(x) LIKE LOWER(:v)` | No distingue mayúsculas en ningún motor. En atributos no textuales aplica `CAST(x AS String)` |
| `in` / `not in` | `IN (:lista)` | `valor` es una lista JSON: `["a","b"]` o `[1,2]`. Lista vacía: `in` no trae nada, `not in` no filtra |
| `is null` / `is not null` | `IS NULL` / `IS NOT NULL` | Ignora `valor` |
| `group` | `GROUP BY` | Ver "Agrupación" |

Cualquier otra operación responde `400` con "La operacion '...' no es valida". Los operadores no distinguen mayúsculas.

### Conversión de valores

JPQL exige que cada parámetro tenga el tipo del atributo con el que se compara, así que `valor` se convierte:

| Tipo del atributo | Formatos aceptados |
|---|---|
| `Long`, `Integer`, `Short`, `Byte`, `Double`, `Float`, `BigDecimal` | `"5"`, `"3.5"` |
| `Boolean` / `boolean` | `"true"`, `"false"`, `"1"`, `"0"` |
| `LocalDateTime`, `Timestamp`, `Date`, `Instant` | `"2026-08-06"`, `"2026-08-06T10:46:00"`, `"2026-08-06T10:46:00.123-03:00"` |
| `LocalDate` | `"2026-08-06"` |
| `enum` | el nombre de la constante |
| `String` | tal cual |

Las fechas con zona horaria se pasan a GMT-3; sin zona, se asumen en GMT-3. Un valor que no se puede convertir responde `400`: "El valor 'x' no es valido para el campo 'y'".

---

## Lógica de Filtros

Los filtros son una lista de grupos: las condiciones dentro de un grupo se unen con **OR** y los grupos entre sí con **AND**.

```json
"filtros": [
  [ { "campo": "estado", "valor": "activo" }, { "campo": "estado", "valor": "pendiente" } ],
  [ { "campo": "prioridad", "operacion": ">", "valor": "5" } ]
]
```

```sql
WHERE (t.estado = :pg_0 OR t.estado = :pg_1) AND (t.prioridad > :pg_2)
```

Las condiciones del backend (`agregarCondicion`) se suman con AND después de los filtros. El filtro de vigencia (`status = 0`) lo agrega `hookPaginarFiltrarStatus()` en las entidades con borrado lógico.

### Filtros sobre relaciones

- **Relación simple** (`@ManyToOne` / `@OneToOne`): `LEFT JOIN` automático. `"campo": "categoria.pais.nombre"` funciona a cualquier profundidad, y dos rutas que pasan por la misma relación comparten el JOIN.
- **Colección** (`@OneToMany` / `@ManyToMany`): se resuelve con `EXISTS`, que significa "al menos un elemento cumple". No duplica filas ni altera el total:

```json
{ "campo": "permisos.nombre", "operacion": "in", "valor": "[\"user.view\",\"role.view\"]" }
```

```sql
WHERE (EXISTS (SELECT 1 FROM t.permisos c1 WHERE c1.nombre IN (:pg_0)))
```

- La ruta tiene que terminar en un atributo simple: `"categoria"` se rechaza, `"categoria.uuid"` es válido.

### Campos del DTO con `@Columna`

Desde Core 3.5.0, un campo del DTO de salida anotado con `@Columna("ruta")` se puede usar por su nombre del DTO en `filtros`, `ordenes` y en la operación `group`: el Core lo traduce a la ruta antes de resolverla. El front usa el mismo nombre que recibe en la respuesta.

```java
@Columna("tipoVehiculo.uuid")
private String tipoVehiculoUuid;
```

```json
{ "filtros": [[{ "campo": "tipoVehiculoUuid", "valor": "a1b2..." }]], "ordenes": [{ "campo": "tipoVehiculoUuid", "direccion": "asc" }] }
```

- La ruta traducida se valida igual que una escrita a mano: `@NoProyectable`, `@JsonIgnore` y las colecciones se siguen rechazando.
- Si el nombre del DTO **también es un atributo de la entidad**, gana el atributo (así un pedido que funcionaba antes de 3.5.0 responde lo mismo).
- Los campos `@NoProyectable` y `@Coleccion` del DTO no se traducen: no salen de la consulta.
- La ruta completa (`"tipoVehiculo.uuid"`) sigue funcionando.
- Hasta 3.4.1 el nombre del DTO respondía `400` "El campo '...' no existe", y los proyectos lo traducían en `hookPaginarAntesDeConsultar`. Esa traducción ya no hace falta.

---

## Ordenamiento

- `"ordenes": [{ "campo": "categoria.nombre", "direccion": "desc" }]`.
- No se puede ordenar por una colección.
- Los nulos se ordenan igual en todos los motores, con el criterio de MariaDB: primero en `asc` y último en `desc` (`NULLS FIRST` / `NULLS LAST`).
- Siempre se agrega `t.id` como último criterio (salvo que ya se ordene por `id`), para que la paginación sea estable y no repita ni saltee filas entre páginas.

---

## Agrupación

Un filtro con `"operacion": "group"` devuelve un solo registro por grupo: el de mayor `id`.

```json
"filtros": [[ { "campo": "categoria.uuid", "operacion": "group" } ]]
```

```sql
SELECT ... FROM Producto o ...
WHERE o.id IN (SELECT MAX(t.id) FROM Producto t LEFT JOIN t.categoria t_j1 WHERE ... GROUP BY t_j1.uuid)
ORDER BY ...
```

El total (`totalDeElementos`) es la cantidad de grupos.

---

## Seguridad

- Cada `campo` (después de traducir el nombre de un campo `@Columna` del DTO a su ruta) se valida contra el metamodelo de JPA. Un campo inexistente (o cualquier texto que no sea una ruta válida, como `"nombre) OR (1=1"`) responde `400` "El campo '...' no existe".
- Las operaciones y direcciones se validan contra una lista cerrada; nunca se concatenan en la consulta.
- Todos los valores viajan como parámetros con nombre (`:pg_N`).
- Los atributos `@NoProyectable` y `@JsonIgnore` quedan fuera de filtros y proyección (ver arriba). Así el front no puede, por ejemplo, adivinar un hash de contraseña con `contrasena like '$2a$...%'`.
- `claseDeEntidad` y `condiciones` son `@JsonIgnore`: el front no puede mandarlos.

---

## Paginación y Respuesta

`pagina` empieza en 1. La respuesta es una `Pagina` del kernel (`com.blue.open.Core.Kernel.DTO.Pagina`), con una
forma fija que no depende de la versión de Spring Data:

```json
{
  "contenido": [ { "uuid": "...", "nombre": "...", "categoriaNombre": "..." } ],
  "totalDeElementos": 45,
  "totalDePaginas": 5,
  "tamano": 10,
  "numero": 0,
  "cantidadDeElementos": 10,
  "primera": true,
  "ultima": false,
  "vacia": false
}
```

`numero` empieza en 0, igual que el `number` del `Page` de Spring que respondía hasta Core 3.2.0. No hay
`pageable` ni `sort`.

---

## Implementación en una Entidad

No hace falta nada más que el service y el DTO:

```java
@Service
public class VehiculoService extends AbstractService<Vehiculo, VehiculoDTO, VehiculoPaginateDTO> {
}
```

Casos opcionales:

```java
@Service
public class VehiculoService extends AbstractService<Vehiculo, VehiculoDTO, VehiculoPaginateDTO> {

    // Filtro fijo del backend: solo vehiculos de la empresa actual
    @Override
    protected PedidoDePaginado hookPaginarAntesDeConsultar(PedidoDePaginado pedido) {
        pedido.agregarFiltro(List.of(new FiltroDePaginado("empresa.uuid", contexto.getUuidEmpresa())));
        return pedido;
    }

    // Lista que no sale de la proyección: UNA consulta para toda la página
    @Override
    protected List<VehiculoPaginateDTO> hookPaginarCompletarResultados(List<VehiculoPaginateDTO> dtos) {
        Map<String, List<String>> etiquetas = etiquetasRepository.nombresPorUuidDeVehiculo(
                dtos.stream().map(VehiculoPaginateDTO::getUuid).toList());
        dtos.forEach(dto -> dto.setEtiquetas(etiquetas.getOrDefault(dto.getUuid(), List.of())));
        return dtos;
    }
}
```

---

## Ejemplos

### Búsqueda simple

```json
POST /api/usuarios/paginar
{
  "pagina": 1,
  "tamano": 10,
  "filtros": [[ { "campo": "nombreDeUsuario", "operacion": "like", "valor": "%juan%" } ]],
  "ordenes": [ { "campo": "nombreDeUsuario", "direccion": "asc" } ]
}
```

### Rango de fechas

```json
"filtros": [
  [ { "campo": "fechaDeCreacion", "operacion": ">=", "valor": "2026-01-01T00:00:00.000-03:00" } ],
  [ { "campo": "fechaDeCreacion", "operacion": "<", "valor": "2026-02-01T00:00:00.000-03:00" } ]
]
```

### Por relación y con IN

```json
"filtros": [
  [ { "campo": "tipoVehiculo.nombre", "valor": "Camión" } ],
  [ { "campo": "anio", "operacion": "in", "valor": "[2024, 2025]" } ]
],
"ordenes": [ { "campo": "id", "direccion": "desc" } ]
```

### Sin relación asignada

```json
"filtros": [[ { "campo": "tipoVehiculo.uuid", "operacion": "is null" } ]]
```

---

## Errores (400)

| Mensaje | Causa |
|---|---|
| `El campo '{campo}' no existe` | La ruta no es un atributo de la entidad |
| `El campo '{campo}' no se puede usar en el paginado` | Atributo `@NoProyectable`, o `@JsonIgnore` en un filtro |
| `El campo '{campo}' debe terminar en un atributo simple (ej: 'relacion.nombre')` | La ruta termina en una relación o colección |
| `El campo '{campo}' pasa por una lista y no se puede usar para ordenar ni mostrar` | Orden, agrupación o `@Columna` sobre una colección |
| `La operacion '{operacion}' no es valida` | Operación fuera de la lista |
| `La direccion '{direccion}' no es valida` | Dirección distinta de `asc` / `desc` |
| `El valor '{valor}' no es valido para el campo '{campo}'` | El valor no se puede convertir al tipo del atributo |
| `El valor del filtro '{campo}' debe ser una lista JSON` | `in` / `not in` sin lista JSON |
| `La pagina y el tamano del paginado deben ser mayores a 0` | `pagina` o `tamano` menores a 1 |

---

## Logging

`MotorDeConsultas` registra el JPQL de la página y sus parámetros:

```
JPQL paginado: SELECT t.nombreDeUsuario, t.email, t.telefono, t.uuid, t.status, t.fechaDeCreacion FROM User t WHERE (t.status = :pg_0) ORDER BY t.id ASC | parametros: {pg_0=0}
```

Para ver el SQL final de cada motor: `logging.level.org.hibernate.SQL=DEBUG` en local.

---

## Consideraciones de Performance

1. **Índices**: las columnas usadas en filtros, órdenes y JOIN deberían tener índice.
2. **Columnas**: el `SELECT` trae solo los campos del DTO; un DTO de paginado chico es una consulta chica.
3. **`like` con `LOWER`**: garantiza el mismo resultado en MariaDB y PostgreSQL, pero no usa un índice común sobre la columna. En tablas grandes, preferir `=` o un índice funcional.
4. **Colecciones**: `EXISTS` es eficiente en ambos motores; cada `@Coleccion` agrega una consulta por página (y por nivel de anidamiento), nunca una por registro.
5. **GROUP BY**: usa una subconsulta; es más costoso que un listado común.

---

## Migración desde Core 2.0.1

| Antes | Ahora |
|---|---|
| `{ "tabla": "t", "columna": "id_empresa", ... }` | `{ "campo": "idEmpresa", ... }` (nombre JPA) |
| `{ "tabla": "u", "columna": "nombre" }` + `relaciones` | `{ "campo": "usuario.nombre" }` (JOIN automático) |
| `"relaciones": [...]` desde el front | No existe. Las relaciones salen de las asociaciones JPA |
| `pedido.init("tabla")` en `hookPaginarAntesDeConsultar` | No hace falta; borrar el override |
| `PaginateRelacion` en el backend | `pedido.agregarCondicion(jpql, parametros)` |
| `modelToPaginateDto` | No existe. El DTO se arma desde la consulta (`@Columna` para datos de otras entidades, `@Coleccion` para listas) |
| Conversión de `Page<T>` a DTO en un hook | `hookPaginarCompletarResultados(List<PAGINADODTO>)`, solo para lo que no sale de la Proyección |
| `repository.paginate(pedido)` → `Page<T>` | `repository.paginar(pedido, Dto.class)` → `Pagina<Dto>` |

---

## Referencias de Clases

- `AbstractService` → `com.blue.open.Core.Crud` (core-crud)
- `EntidadRepository`, `EntidadRepositoryBase`, `MotorDeConsultas` → `com.blue.open.Core.Kernel.Repository` (core-kernel)
- `ConsultaDePaginado`, `ConversorDeValores` → `com.blue.open.Core.Kernel.Paginado` (core-kernel)
- `Proyector`, `ProyeccionDeDto`, `Columna`, `Coleccion`, `NoProyectable` → `com.blue.open.Core.Kernel.Proyeccion` (core-kernel)
- `PedidoDePaginado`, `FiltroDePaginado`, `OrdenDePaginado`, `CondicionDePaginado`, `ParametroDePaginado` → `com.blue.open.Core.Kernel.DTO` (core-kernel)
- Test: `core-kernel/src/test/java/com/blue/open/Core/Kernel/Paginado/ConsultaDePaginadoTest.java`
