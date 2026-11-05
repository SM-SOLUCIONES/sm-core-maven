# Endpoint GET /uuid/{uuid} - Core

## Descripción General

`GET /{controller}/uuid/{uuid}` devuelve un registro buscándolo por su `uuid`. La lógica está en `AbstractService.obtenerPorUuid`; el endpoint lo escribe el controller de la entidad con la [receta](receta-controller.md) y solo delega.

| | |
|---|---|
| **Método / Ruta** | `GET /{controller}/uuid/{uuid}` (el `/api` lo agrega `server.servlet.context-path`; el controller no lo repite) |
| **Parámetro** | `{uuid}` en el path, requerido |
| **Respuesta** | El `DTO` (segundo genérico del service). No es paginado |
| **Transaccional** | `readOnly = true` |
| **Autorización** | El `permiso` del `@Contrato` del método (ver "Seguridad") |

```
GET /api/producto/uuid/0198c0a4-7f3e-7b21-9c1d-5a2e8f4b6d10
```

---

## Comportamiento

- **No filtra por `status`.** Usa `proyector.porUuid(entidad, uuid, DTO)`: en una entidad con borrado lógico devuelve también los registros eliminados (`status = -1`), con ese `status` en la respuesta. En una entidad con borrado físico un registro borrado simplemente no existe.
- Si no hay registro, responde `400` con `"No existe el registro"`. **Nunca `404`.**
- Cualquier excepción que no sea `ExcepcionDeApi` se reemplaza por `400` con `"Error al obtener"` (el detalle queda en `mensajeParaDesarrollador`).
- El DTO es una **Proyección**: se arma directamente desde la consulta JPQL, con solo las columnas de sus campos, sin cargar la entidad (ver "Proyección del DTO"). Es la misma Proyección que responden `guardar` y `borrar`.
- Un `uuid` con formato inválido no se valida: la búsqueda no encuentra nada y responde igual `"No existe el registro"`.

Si la entidad tiene borrado lógico y el endpoint no debe devolver eliminados, se sobreescribe `hookObtenerBuscarEnBase` (ver "Hooks").

---

## Proyección del DTO

Los campos del DTO deciden qué se trae de la base. Las mismas reglas valen para el DTO de paginado ([paginado.md](paginado.md)) y para los sub-DTO de una `@Coleccion`:

| Campo del DTO | Qué trae |
|---|---|
| Mismo nombre que un atributo básico de la entidad | Ese atributo, solo |
| `@Columna("relacion.atributo")` | El atributo de la entidad relacionada, navegando `@ManyToOne` / `@OneToOne` con `LEFT JOIN` (relación nula → campo `null`) |
| `@Coleccion(value = "atributo", orden = "atributoDelElemento")` sobre un `List<SubDTO>` | Los elementos de una `@OneToMany` / `@ManyToMany`, proyectados con el `SubDTO` |
| `@NoProyectable` en el campo, o atributo de la entidad con `@NoProyectable` / `@JsonIgnore` | Nada: queda en su valor por defecto |
| Sin atributo equivalente ni anotación | Nada: queda en su valor por defecto. Sirve para un dato calculado que se completa en un hook |

`@Coleccion`:

- Se resuelve con **una consulta por anotación para todos los registros** (`IN` sobre los ids del nivel padre, en lotes de 500, `MotorDeConsultas.TAMANO_DE_LOTE`), nunca una por registro.
- Orden: ascendente por `orden`; si se omite, por `id` ascendente.
- Si los elementos tienen borrado lógico, solo trae los vigentes (`status = 0`). Un registro sin elementos responde la lista vacía.
- Sin límite de niveles: un sub-DTO puede tener su propia `@Coleccion`.
- Es de solo lectura: al guardar se ignora.

```java
// Ejemplo real: RolDTO (core-identity)
@Coleccion("permisos")
private List<PermisoDTO> permisos;
```

**Costo.** Cada `@Coleccion` es una consulta más, y una `@Coleccion` anidada profunda multiplica filas en la consulta del último nivel (cada elemento de cada nivel). Es responsabilidad de quien diseña el DTO: si la respuesta no necesita la colección, no se declara.

`ValidadorDeProyecciones` (`core-crud`) valida al arrancar los DTO de todos los `AbstractService`: una ruta de `@Columna` o `@Coleccion` inexistente, una `@Coleccion` que no apunta a una colección de entidades o un ciclo de DTOs cortan el arranque.

Si el caso de uso necesita algo que la Proyección no cubre, no se usa este endpoint: es un caso de uso con consulta propia (`@Query` con `SELECT new` en el repositorio, o inyectando el bean `Proyector`: `porUuid`, `completarColecciones`). Ver [consultas.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/consultas.md).

---

## Flujo de Ejecución

```
InterceptorDeContrato                   permiso del @Contrato (401 sin sesion, 403 si no lo cumple)
{Entidad}Controller.obtenerPorUuid(uuid)
  service.obtenerPorUuid(uuid)
AbstractService.obtenerPorUuid(uuid)
  hookObtenerAuth(uuid)                    vacío por defecto (autorización por datos)
  hookObtenerBuscarEnBase(uuid)            proyector.porUuid(entidad, uuid, DTO) -> Optional<DTO>
  si esta vacio -> ExcepcionDeApi(REGISTRO_NO_ENCONTRADO)
```

---

## Hooks

El controller no tiene hooks: solo delega. Todos están en el service.

### En el Service (`AbstractService`)

```java
protected void hookObtenerAuth(String uuid)
protected Optional<DTO> hookObtenerBuscarEnBase(String uuid)
```

| Hook | Default | Cuándo sobreescribirlo |
|---|---|---|
| `hookObtenerAuth(uuid)` | Vacío | Autorización que depende de los datos (por ejemplo, un usuario que solo puede leer su propio registro). Nunca un permiso fijo: ese va en `@Contrato` |
| `hookObtenerBuscarEnBase(uuid)` | `proyector.porUuid(...)`: la Proyección del DTO con sus `@Coleccion`, sin filtrar por `status`. Vacío → `REGISTRO_NO_ENCONTRADO` | Rechazar eliminados, o completar un campo calculado que no sale de la Proyección |

---

## Seguridad

La autorización la declara el `permiso` del `@Contrato` del método y la hace cumplir `InterceptorDeContrato` antes de ejecutarlo, a través del puerto `AutorizadorDeEndpoint`:

| Proyecto | Autorizador | Efecto |
|---|---|---|
| Con `core-identity` | `AutorizadorPorRolesYPermisos` | Exige alguno de los roles o permisos del `@Contrato`; otro usuario recibe `403` y una request sin sesión `401` |
| Sin `core-identity` | `AutorizadorDenegarTodo` | Solo pasan los endpoints `Contrato.PUBLICO`; el resto responde `401` (`NO_AUTENTICADO`) |

Si el controller no escribe este endpoint, responde `404`, no `403`.

---

## Ejemplos de Implementación

Endpoint con un permiso propio del proyecto (receta completa en [receta-controller.md](receta-controller.md)):

```java
// Obtiene un producto por su uuid. Responde el ProductoDTO
@GetMapping("/uuid/{uuid}")
@Contrato(casoDeUso = "CU-PRO-02", resumen = "Obtiene un producto", permiso = "PRODUCTO_VER")
public ResponseEntity<ProductoDTO> obtenerPorUuid(@Descripcion("Uuid del registro") @PathVariable("uuid") String uuid) {
    return new ResponseEntity<>(service.obtenerPorUuid(uuid), HttpStatus.OK);
}
```

Service que rechaza registros eliminados y completa un dato derivado:

```java
@Service
public class ProductoService extends AbstractService<Producto, ProductoDTO, ProductoPaginateDTO> {

    // Un producto eliminado responde "No existe el registro". descripcionCompleta no es un atributo de
    // Producto: la Proyeccion la deja en null y se completa aca con datos ya proyectados
    @Override
    protected Optional<ProductoDTO> hookObtenerBuscarEnBase(String uuid) {
        return super.hookObtenerBuscarEnBase(uuid)
                .filter(dto -> dto.getStatus() != -1)
                .map(dto -> {
                    dto.setDescripcionCompleta(dto.getCodigo() + " - " + dto.getNombre());
                    return dto;
                });
    }
}
```

Las dependencias propias del controller (como `service` arriba) o del service entran por constructor, ver [inyeccion-de-dependencias.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/inyeccion-de-dependencias.md).

---

## Respuestas

### 200 OK

```json
{
  "uuid": "0198c0a4-7f3e-7b21-9c1d-5a2e8f4b6d10",
  "status": 0,
  "fechaDeCreacion": "2026-01-15T10:30:00.000-03:00",
  "codigo": "P-001",
  "nombre": "Producto de ejemplo"
}
```

Los campos salen de la Proyección del DTO de la entidad. Las fechas van en GMT-3 (ver [entidades-y-dtos.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/entidades-y-dtos.md)). El `id` interno nunca se responde.

### Errores

Formato completo del body de error: [errores/README.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/arquitectura/errores/README.md).

| Status | `mensajeParaUsuario` | Cuándo |
|---|---|---|
| `400` | `No existe el registro` (`REGISTRO_NO_ENCONTRADO`) | El uuid no existe, o (con borrado lógico y `hookObtenerBuscarEnBase` sobreescrito para vigentes) está eliminado |
| `400` | `Error al obtener` | Excepción inesperada en el service; el detalle va en `mensajeParaDesarrollador` (`Error en obtenerPorUuid: ...`) |
| `401` | `Usuario no identificado` (`NO_AUTENTICADO`) | La request no trae sesión y el `@Contrato` la exige |
| `403` | `No tienes permiso` | El `@Contrato` o el hook de autorización del service rechazó al usuario |
| `404` | (sin body de `ExcepcionDeApi`) | El controller no escribe este endpoint |

La prueba de contrato de este endpoint la da `ContratoDeAbmTest` (`core-test`), que cada entidad extiende en `{Entidad}ContratoTest` (ver [testing.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/testing.md)).

---

## Referencias de Clases

- `AbstractService` → `com.blue.open.Core.Crud` (core-crud)
- `Contrato`, `InterceptorDeContrato` → `com.blue.open.Core.Kernel.Contrato` (core-kernel)
- `AutorizadorDeEndpoint`, `AutorizadorDenegarTodo` → `com.blue.open.Core.Kernel.Puertos` (core-kernel)
- `AutorizadorPorRolesYPermisos` → `com.blue.open.Core.Identity.Service` (core-identity)
- `EstrategiaDeBorrado`, `SelectorDeBorrado` → `com.blue.open.Core.Crud.Borrado` (core-crud)
- `EntidadBase`, `EntidadConBorradoLogico` → `com.blue.open.Core.Kernel.Entidad` (core-kernel)
- `EntidadRepository`, `EntidadRepositoryBorradoLogico` → `com.blue.open.Core.Kernel.Repository` (core-kernel)
- `Proyector`, `Columna`, `Coleccion`, `NoProyectable` → `com.blue.open.Core.Kernel.Proyeccion` (core-kernel)
- `ValidadorDeProyecciones`, `CodigoDeErrorCrud` → `com.blue.open.Core.Crud` (core-crud)
