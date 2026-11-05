# core-crud - Índice

`core-crud` implementa en `AbstractService` la lógica de los cuatro endpoints del ABM de una entidad. Los endpoints no se heredan: el controller de la entidad los escribe explícitos con la [receta del controller](receta-controller.md), cada uno con su `@Contrato`, y delega en el service. Lo que cambia se personaliza con hooks del service.

Desde Core 3.0.2 no hay una clase de conversión por entidad: toda lectura del ABM (uuid, paginar y la respuesta de guardar y borrar) es una **Proyección** (el DTO se arma desde la consulta JPQL con solo las columnas de sus campos, ver `Proyector`), y `guardar` **aplica el DTO** sobre la entidad (copia los campos simples y asigna las relaciones por uuid, ver [guardar.md](guardar.md)).

Rutas: `/{controller}/...`. El `/api` lo agrega `server.servlet.context-path`; el `@RequestMapping` del controller **no lo repite** (`@RequestMapping("producto")`, no `"/api/producto"`).

| Endpoint | Para qué | Documento | Transaccional |
|---|---|---|---|
| `GET /{controller}/uuid/{uuid}` | Un registro por `uuid` | [obtener.md](obtener.md) | read-only |
| `POST /{controller}/paginar` | Listado con filtros, orden, relaciones y agrupación | [paginado.md](paginado.md) | read-only |
| `POST /{controller}/guardar` | Crear (sin `uuid`) o actualizar (con `uuid`) | [guardar.md](guardar.md) | sí |
| `DELETE /{controller}/borrar/{uuid}` | Borrado lógico o físico, según la entidad | [borrar.md](borrar.md) | sí |

## Comparación

| | uuid | paginar | guardar | borrar |
|---|---|---|---|---|
| Body | No | `PedidoDePaginado` | DTO (`@Valid`) | No |
| Respuesta | DTO | `Pagina<PAGINADODTO>` | DTO | DTO |
| Lectura | Proyección del DTO | Proyección del `PAGINADODTO` | Aplica el DTO; responde la Proyección de uuid | Responde la Proyección de uuid |
| Filtra por vigentes | **No** | Sí (`status = 0` si hay borrado lógico) | Sí (`EstrategiaDeBorrado`) | Sí (`EstrategiaDeBorrado`) |
| Auditoría | No | No | `idUsuarioDeCreacion`, `idUsuarioDeActualizacion` | `idUsuarioDeBorrado`, `fechaDeBorrado` |
| Hook de autorización por datos (service) | `hookObtenerAuth` | `hookPaginarAuth` | `hookGuardarAuth` | `hookBorrarAuth` |

---

## Qué publica cada controller

Solo los endpoints que escribe: uno que la entidad no necesita no se escribe y responde `404`, no `403` (ver [receta-controller.md](receta-controller.md)). Cuándo conviene el ABM y cuándo un caso de uso propio: [controllers-y-endpoints.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/controllers-y-endpoints.md).

## Autorización

El permiso de cada endpoint lo declara su `@Contrato` (`permiso = "SYSADMIN"`, `Contrato.PUBLICO`, `Contrato.AUTENTICADO`, ...) y lo hace cumplir `InterceptorDeContrato` (`core-kernel`) antes de llamar al service, a través del puerto `AutorizadorDeEndpoint`. Sin `core-identity`, el default `AutorizadorDenegarTodo` solo deja pasar los `PUBLICO`. Ver [testing/README.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/arquitectura/testing/README.md).

Los `hookObtenerAuth`, `hookPaginarAuth`, `hookGuardarAuth` y `hookBorrarAuth` del service quedan solo para la autorización que depende de los datos (alta vs. edición, alcance por empresa, dueño del registro), con `tienePermisos(...)` y `throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO)`. Nunca para un permiso fijo: ese va en `@Contrato`.

---

## Estructura de Archivos

```
sm-core-maven/
├── core-kernel/src/main/java/com/blue/open/Core/Kernel/
│   ├── Entidad/
│   │   ├── EntidadBase.java               (id, uuid, fechas y auditoría; borrado físico)
│   │   └── EntidadConBorradoLogico.java   (agrega status, fecha_de_borrado, id_usuario_de_borrado)
│   ├── DTO/
│   │   ├── EntidadBaseDTO.java
│   │   ├── PedidoDePaginado.java / FiltroDePaginado.java / OrdenDePaginado.java
│   │   └── CondicionDePaginado.java / ParametroDePaginado.java
│   ├── Paginado/
│   │   ├── ConsultaDePaginado.java       (traduce el PedidoDePaginado a JPQL)
│   │   └── ConversorDeValores.java
│   ├── Proyeccion/
│   │   ├── Proyector.java                (lee el DTO desde la consulta y aplica el DTO al guardar)
│   │   ├── ProyeccionDeDto.java          (qué campos del DTO se traen y de dónde)
│   │   ├── Columna.java / Coleccion.java / NoProyectable.java
│   │   └── AplicaDto.java                (la entidad completa lo que no se copia solo)
│   ├── Repository/
│   │   ├── EntidadRepository.java                (búsqueda por uuid/id; base de todo repositorio)
│   │   ├── EntidadRepositoryBorradoLogico.java   (agrega los finders con status = 0)
│   │   ├── EntidadRepositoryBase.java            (paginar() para todos los repositorios)
│   │   └── MotorDeConsultas.java                  (ejecuta la Proyección y completa las @Coleccion)
│   └── Excepciones/ExcepcionDeApi.java
└── core-crud/
    ├── src/main/java/com/blue/open/Core/Crud/
    │   ├── AbstractService.java         (implementa la lógica de los endpoints)
    │   ├── ValidadorDeProyecciones.java (valida al arrancar los DTO de todos los services del ABM)
    │   ├── Excepciones/                 (CodigoDeErrorCrud)
    │   └── Borrado/                     (EstrategiaDeBorrado: lógico o físico)
    └── docs/
        ├── README.md       (este archivo)
        ├── receta-controller.md (cómo se escribe el controller del ABM)
        ├── obtener.md       (GET /uuid/{uuid})
        ├── paginado.md      (POST /paginar)
        ├── guardar.md       (POST /guardar)
        └── borrar.md        (DELETE /borrar/{uuid})
```

---

## Cómo Implementar una Entidad

Los pasos completos, con la ubicación de cada archivo, están en el checklist de [estructura-de-carpetas.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/estructura-de-carpetas.md). Lo específico de `core-crud`:

1. **Entidad**: `EntidadConBorradoLogico` (borrado lógico, el caso por defecto) o `EntidadBase` (borrado físico, sin columna `status`). Es la única decisión de borrado: el Core resuelve solo cómo borrar según de qué clase hereda.
2. **DTO**: extiende `EntidadBaseDTO`. Si la entidad es grande, además un `{Entidad}PaginateDTO` (ver [entidades-y-dtos.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/entidades-y-dtos.md)). Sus campos definen la Proyección: por nombre de atributo, `@Columna("relacion.atributo")` o `@Coleccion` (ver [obtener.md](obtener.md)). `ValidadorDeProyecciones` corta el arranque si una ruta no existe.
3. **Repository**: `EntidadRepositoryBorradoLogico<T>` o `EntidadRepository<T>`. `paginar()` viene incluido.
4. **Service**: extiende `AbstractService<T, DTO, PAGINADODTO>`, sin constructor ni overrides si no hay lógica propia.
5. **Controller**: clase propia que inyecta el service y escribe los endpoints que la entidad usa, cada uno con su `@Contrato`. Plantilla completa en [receta-controller.md](receta-controller.md).

```java
@Service
public class ProductoService extends AbstractService<Producto, ProductoDTO, ProductoPaginateDTO> {
}
```

`AbstractService` recibe sus dependencias por setter (no por constructor), así que una subclase sin dependencias propias no declara nada. Si las tiene, `@RequiredArgsConstructor` con campos `private final` ([inyeccion-de-dependencias.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/inyeccion-de-dependencias.md)).

Si la entidad no usa el CRUD genérico, no necesita extender nada de `core-crud`: el módulo no es obligatorio.

---

## Hooks

Cada hook se sobreescribe en la subclase del service. Todos son `protected`. El controller no tiene hooks: solo delega.

| Endpoint | Service |
|---|---|
| uuid | `hookObtenerAuth`, `hookObtenerBuscarEnBase` |
| paginar | `hookPaginarAuth`, `hookPaginarFiltrarStatus`, `hookPaginarAntesDeConsultar`, `hookPaginarEjecutarConsulta`, `hookPaginarCompletarResultados`, `hookPaginarFiltrarResultados`, `hookPaginarCrearPagina` |
| guardar | `hookGuardarAuth`, `hookGuardarBuscarExistente`, `hookGuardarCrearEntidadVacia`, `hookGuardarAsignarUsuarioDeCreacion`, `hookGuardarAplicarDto`, `hookGuardarAsignarUsuarioDeActualizacion`, `hookGuardarEnBase`, `hookGuardarProyectarRespuesta` |
| borrar | `hookBorrarAuth`, `hookBorrarBuscarPorUuid`, `hookBorrarProyectar`, `hookBorrarAplicarBorrado` |

Firmas, valores por defecto y ejemplos: en el documento de cada endpoint.

Los hooks de autorización del service están vacíos por defecto y corren después de `@Contrato`, que ya validó el permiso fijo: son solo para la autorización que depende de los datos.

Todo método público de `AbstractService` captura las excepciones: una `ExcepcionDeApi` lanzada desde un hook llega intacta al front; cualquier otra se reemplaza por una `ExcepcionDeApi` `400` genérico (`"Error al obtener"`, `"Error al obtener el paginado"`, `"Error al guardar el registro"`, `"Error al borrar el registro"`), con el detalle en `mensajeParaDesarrollador`.

---

## Seguridad

- **Autorización**: ver "Autorización" arriba. `@Contrato` se aplica antes de ejecutar el controller (y antes de `@Valid`); los hooks del service, antes de tocar la base.
- **SQL**: todo va por consultas JPA parametrizadas. En el paginado, cada `campo` se valida contra el metamodelo de JPA y las operaciones contra una lista cerrada: el front no puede inyectar JPQL (ver [paginado.md](paginado.md)).
- **Datos sensibles**: el atributo de la entidad se marca con `@NoProyectable` (o `@JsonIgnore`): no se puede filtrar, ordenar ni proyectar en ningún endpoint, y al guardar no se copia del DTO. Si además el DTO no declara ese campo, nunca viaja en la respuesta.
- **`id` interno**: nunca se responde; el front solo conoce el `uuid`.

---

## Auditoría

| Campo | Cuándo | Aplica a |
|---|---|---|
| `fechaDeCreacion`, `fechaDeActualizacion` | Hibernate, al crear / al modificar | Todas |
| `idUsuarioDeCreacion`, `idUsuarioDeActualizacion` | `hookGuardarAsignarUsuarioDeCreacion` / `hookGuardarAsignarUsuarioDeActualizacion` | Todas |
| `fechaDeBorrado`, `idUsuarioDeBorrado` | Al borrar | Solo borrado lógico |

El usuario sale del puerto `ProveedorDeUsuarioActual`. Si no hay usuario logeado, esos campos quedan sin asignar.

---

## Errores

Todos los errores del Core, incluidos los de `core-crud`, tienen el mismo formato y se documentan en [errores/README.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/arquitectura/errores/README.md). Ejemplo de `throw new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO)`:

```json
{
  "fechaHora": "2026-06-29T14:30:45.000-03:00",
  "codigo": "REGISTRO_NO_ENCONTRADO",
  "status": "BAD_REQUEST",
  "mensajeParaUsuario": "No existe el registro",
  "errores": [],
  "traceId": "9f1c2f4e-2f1a-4c9a-9f0e-2b3d4a5c6d7e"
}
```

| Status | Cuándo, en los endpoints del ABM |
|---|---|
| `200` | Operación exitosa |
| `400` | Dato inválido (`@Valid`, JSON ilegible), registro inexistente o ya eliminado (`REGISTRO_NO_ENCONTRADO`), relación inexistente al guardar (`RELACION_NO_ENCONTRADA`), error inesperado en un service. **Un registro inexistente es `400`, nunca `404`** |
| `401` | Request sin sesión (`NO_AUTENTICADO`): ningún usuario identificado y el `@Contrato` exige sesión |
| `403` | Usuario autenticado sin permiso (`SIN_PERMISO`: `@Contrato` o hook de autorización del service) |
| `404` | El controller no escribe ese endpoint. Sin body de `ExcepcionDeApi` |
| `500` | Excepción que no pasó por un service (`manejarCualquierExcepcion`) |

El `401` no lo produce `core-crud`: la autenticación la resuelven `InterceptorDeContrato` y `AuthService` (`core-identity`), que responden `401 NO_AUTENTICADO` sin sesión y `403 SIN_PERMISO` sin permiso.

---

## Preguntas Frecuentes

**¿Puedo cambiar qué roles acceden a los endpoints?**
Sí: cambiando el `permiso` del `@Contrato` de cada endpoint en el controller. Si depende de los datos, con el `hookXxxAuth()` del service.

**¿Cómo evito devolver contraseñas?**
Con `@NoProyectable` en el atributo de la entidad: ninguna Proyección lo trae (uuid, paginar, guardar ni borrar) y al guardar no se copia del DTO.

**¿Puedo hacer borrado físico?**
Sí: la entidad extiende `EntidadBase` en vez de `EntidadConBorradoLogico` ([borrar.md](borrar.md)). No hay ninguna configuración adicional.

**¿Puedo reactivar un registro eliminado?**
No con `POST /guardar`: `EntidadConBorradoLogico.aplicarCamposBase` lo rechaza ([guardar.md](guardar.md)). Requiere un caso de uso propio.

**¿Cómo hago JOINs entre tablas en el paginado?**
No hace falta: un `campo` con ruta (`categoria.pais.nombre`) arma los JOIN solo, siempre que la entidad tenga la asociación JPA. Para tablas sin asociación, `pedido.agregarCondicion(...)` en `hookPaginarAntesDeConsultar()` ([paginado.md](paginado.md)).

**¿Qué hago si el endpoint necesita menos campos que el DTO completo?**
No se usa el endpoint del ABM: se crea un caso de uso con su propio DTO ([controllers-y-endpoints.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/controllers-y-endpoints.md)).

---

## Referencias

- [receta-controller.md](receta-controller.md): cómo se escriben los endpoints
- [AbstractService](../src/main/java/com/blue/open/Core/Crud/AbstractService.java): implementa la lógica
- [EstrategiaDeBorrado](../src/main/java/com/blue/open/Core/Crud/Borrado/EstrategiaDeBorrado.java): borrado lógico o físico
- [Contrato](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Contrato/Contrato.java) y [AutorizadorDeEndpoint](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Puertos/AutorizadorDeEndpoint.java): autorización de cada endpoint
- [EntidadBase](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Entidad/EntidadBase.java) y [EntidadConBorradoLogico](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Entidad/EntidadConBorradoLogico.java): entidades base
- [EntidadRepository](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Repository/EntidadRepository.java) y [EntidadRepositoryBorradoLogico](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Repository/EntidadRepositoryBorradoLogico.java): repositorios base
- [MotorDeConsultas](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Repository/MotorDeConsultas.java): construcción de las consultas de paginado y de las `@Coleccion`
- [Proyector](../../core-kernel/src/main/java/com/blue/open/Core/Kernel/Proyeccion/Proyector.java): Proyección por uuid y aplicación del DTO al guardar
- Módulos y puertos del Core: [docs/arquitectura/core/README.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/arquitectura/core/README.md)
