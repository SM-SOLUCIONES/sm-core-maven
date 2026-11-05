# Endpoint POST /guardar - Core

## Descripción General

`POST /{controller}/guardar` crea un registro nuevo o actualiza uno existente con una única operación. Decide por la presencia del `uuid` en el DTO. La lógica está en `AbstractService.guardar`; el endpoint lo escribe el controller de la entidad con la [receta](receta-controller.md) y solo delega.

| | |
|---|---|
| **Método / Ruta** | `POST /{controller}/guardar` (el `/api` lo agrega `server.servlet.context-path`; el controller no lo repite) |
| **Body** | El `DTO` de la entidad (segundo genérico del service), validado con `@Valid` |
| **Respuesta** | El `DTO` del registro guardado: la misma Proyección que `GET /uuid` |
| **Transaccional** | Sí |
| **Autorización** | El `permiso` del `@Contrato` del método (ver "Seguridad") |

```
POST /api/producto/guardar
{
  "codigo": "P-001",
  "nombre": "Producto de ejemplo"
}
```

---

## Crear vs. Actualizar

| El DTO trae | Resultado |
|---|---|
| `uuid` nulo o vacío | **CREATE**: entidad nueva, `uuid` generado por el Core |
| `uuid` de un registro vigente | **UPDATE**: el DTO reemplaza los campos que se copian al aplicarlo (ver "Aplicar el DTO") |
| `uuid` que no existe | **CREATE** (con un `uuid` nuevo, no el enviado) |
| `uuid` de un registro eliminado (borrado lógico) | Ver "No se puede reactivar" |

"Vigente" lo decide la `EstrategiaDeBorrado` de la entidad, no el service:

- Borrado lógico (`EntidadConBorradoLogico`): vigente es `status = 0`.
- Borrado físico (`EntidadBase`): vigente es cualquier registro que exista en la tabla.

Un registro eliminado **no** lo encuentra `hookGuardarBuscarExistente`, así que un `uuid` de un eliminado se trata como "no existe" y el flujo intenta un CREATE. La protección contra reactivar es la de la sección siguiente.

### No se puede reactivar un registro eliminado

Solo aplica a entidades con borrado lógico. La regla vive en `EntidadConBorradoLogico.aplicarCamposBase(dto)`, que valida las transiciones de `status` del DTO:

| `status` actual | `status` del DTO | Resultado |
|---|---|---|
| `0` | `0` | Sin cambios |
| `0` | `-1` | Pasa a `-1` (eliminado) |
| `-1` | `0` | `ExcepcionDeApi("No se puede activar un elemento que fue eliminado")` |
| cualquiera | otro valor distinto de `0` y `-1` | `ExcepcionDeApi("Error status")` |

Esta validación es automática: `hookGuardarAplicarDto` llama a `modelo.aplicarCamposBase(dto)` en todas las entidades, después de copiar los campos del DTO (ver "Aplicar el DTO").

---

## Aplicar el DTO

Desde Core 3.0.2 no hay una clase de conversión por entidad: `hookGuardarAplicarDto(dto, modelo)` llama a `proyector.aplicar(dto, modelo, CodigoDeErrorCrud.RELACION_NO_ENCONTRADA)`, que sobre la entidad cargada (o recién creada):

1. **Copia los campos simples** del DTO que se llaman igual que un atributo básico de la entidad, por su setter público. Los campos de control de `EntidadBaseDTO` (`uuid`, `status`, `fechaDeCreacion`) no se copian.
2. **Asigna las relaciones** `@ManyToOne` / `@OneToOne` declaradas con `@Columna("relacion.uuid")`: busca la entidad relacionada vigente por ese uuid. Si no existe, responde `400` `RELACION_NO_ENCONTRADA` con `datos.campo` = nombre del campo del DTO. Un uuid `null` desasocia.
3. **Saltea**: rutas más largas que `relacion.uuid`, campos `@Coleccion` (son de solo lectura), atributos sin setter público (`@Setter(AccessLevel.NONE)`) y atributos o campos `@NoProyectable` o `@JsonIgnore`.
4. Primero resuelve todas las relaciones y recién después modifica la entidad: si una relación no existe, la entidad queda intacta.
5. Llama a `modelo.aplicarCamposBase(dto)` (transiciones de `status`, ver arriba).
6. Si la entidad implementa `AplicaDto<DTO>` y el DTO es de ese tipo, llama a `modelo.aplicar(dto)`: ahí la entidad completa lo que no se copia solo (campos que cambian por un método de negocio, lógica propia). No recibe dependencias.

```java
// DTO: tipoVehiculoUuid asigna la relacion al guardar y se proyecta al leer
@Columna("tipoVehiculo.uuid")
private String tipoVehiculoUuid;
```

Lo que necesita dependencias (un repositorio, un encoder) va en el hook del service sobreescrito: por ejemplo `UsuarioBaseService.hookGuardarAplicarDto` escribe la `contrasena` hasheada, que nunca pasa por la copia genérica porque es `@NoProyectable`.

La respuesta (`hookGuardarProyectarRespuesta`) vuelve a proyectar el registro por uuid con la misma Proyección que `GET /uuid` (un `SELECT` más): `guardar` responde con la misma forma que `uuid`.

---

## Flujo de Ejecución

```
InterceptorDeContrato                       permiso del @Contrato (401 sin sesion, 403 si no lo cumple, antes de @Valid)
{Entidad}Controller.guardar(dto)       @Valid @RequestBody
  service.guardar(dto)
AbstractService.guardar(dto)
  hookGuardarAuth(dto)                         vacío por defecto (autorización por datos)
  hookGuardarBuscarExistente(dto)             registro vigente por uuid -> Optional
  si esta vacio (CREATE):
    hookGuardarCrearEntidadVacia(dto)        entidad vacía por reflexión
    hookGuardarAsignarUsuarioDeCreacion(modelo)           idUsuarioDeCreacion = usuario actual
  hookGuardarAplicarDto(dto, modelo)            proyector.aplicar(dto, modelo, RELACION_NO_ENCONTRADA)
  hookGuardarAsignarUsuarioDeActualizacion(modelo)             idUsuarioDeActualizacion = usuario actual (CREATE y UPDATE)
  hookGuardarEnBase(modelo)               repository.save(modelo)
  hookGuardarProyectarRespuesta(modelo)         proyectar(modelo.getUuid()): misma Proyección que uuid
```

---

## Hooks

El controller no tiene hooks: solo delega. Todos están en el service.

### En el Service (`AbstractService`)

```java
protected void hookGuardarAuth(DTO modelo)
protected Optional<T> hookGuardarBuscarExistente(DTO dto)
protected T hookGuardarCrearEntidadVacia(DTO dto)
protected T hookGuardarAsignarUsuarioDeCreacion(T modelo)
protected T hookGuardarAplicarDto(DTO dto, T modelo)
protected T hookGuardarAsignarUsuarioDeActualizacion(T modelo)
protected T hookGuardarEnBase(T modelo)
protected DTO hookGuardarProyectarRespuesta(T modelo)
```

| Hook | Default | Cuándo sobreescribirlo |
|---|---|---|
| `hookGuardarAuth(dto)` | Vacío | Autorización que depende de los datos (por ejemplo, alta vs. edición según venga el `uuid`). Nunca un permiso fijo: ese va en `@Contrato` |
| `hookGuardarBuscarExistente(dto)` | Si el DTO trae `uuid`, `borrado().buscarVigentePorUuid(repository, uuid)`; si no, `Optional.empty()` | Casi nunca. Buscar el registro por otra clave |
| `hookGuardarCrearEntidadVacia(dto)` | `claseDeEntidad.getDeclaredConstructor().newInstance()` | La entidad necesita valores iniciales. Falla con `"Error creando la entidad"` si no tiene constructor sin argumentos |
| `hookGuardarAsignarUsuarioDeCreacion(modelo)` | `modelo.setIdUsuarioDeCreacion(id del usuario actual)`; no asigna nada si no hay usuario logeado | Auditoría propia |
| `hookGuardarAplicarDto(dto, modelo)` | `proyector.aplicar(dto, modelo, RELACION_NO_ENCONTRADA)` (ver "Aplicar el DTO"); lanza `ExcepcionDeApi` si el modelo es `null` | Lo que necesita dependencias: encriptar, asignar una colección, validar unicidad |
| `hookGuardarAsignarUsuarioDeActualizacion(modelo)` | `modelo.setIdUsuarioDeActualizacion(id del usuario actual)`; no asigna nada si no hay usuario logeado | Auditoría propia |
| `hookGuardarEnBase(modelo)` | `repository.save(modelo)` | Validar la entidad con el DTO ya aplicado, o guardar entidades dependientes en la misma transacción |
| `hookGuardarProyectarRespuesta(modelo)` | `proyectar(modelo.getUuid())`: la misma Proyección que `GET /uuid` | Casi nunca. Los datos sensibles se excluyen con `@NoProyectable`, no acá |

El id del usuario actual sale del puerto `ProveedorDeUsuarioActual`; con `core-identity` es el usuario logeado.

---

## Campos Automáticos

| Campo | CREATE | UPDATE | Lo asigna |
|---|---|---|---|
| `uuid` | UUIDv7 generado | No cambia | `EntidadBase.alCrear()` (`@PrePersist`) |
| `status` | `0` | Lo controla `aplicarCamposBase` (ver arriba) | `EntidadConBorradoLogico` |
| `idUsuarioDeCreacion` | Usuario actual | No cambia | `hookGuardarAsignarUsuarioDeCreacion` |
| `idUsuarioDeActualizacion` | Usuario actual | Usuario actual | `hookGuardarAsignarUsuarioDeActualizacion` |
| `fechaDeCreacion` | Ahora | No cambia | `@CreationTimestamp` (Hibernate) |
| `fechaDeActualizacion` | Ahora | Ahora | `@UpdateTimestamp` (Hibernate) |
| `fechaDeBorrado`, `idUsuarioDeBorrado` | `null` | No cambian | Solo los toca el borrado (ver [borrar.md](borrar.md)) |

`status`, `fechaDeBorrado` e `idUsuarioDeBorrado` solo existen en entidades con borrado lógico. En una entidad con borrado físico el DTO trae `status` en `0` y se ignora.

---

## Validaciones

- El body se valida con `@Valid`. Cada campo obligatorio del DTO lleva su anotación y su mensaje (ver [entidades-y-dtos.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/entidades-y-dtos.md)).
- Las reglas de negocio que no se expresan con una anotación van en `AplicaDto.aplicar` de la entidad o en un hook, y lanzan `ExcepcionDeApi`.
- Una `ExcepcionDeApi` lanzada desde cualquier hook llega intacta al front. Cualquier otra excepción se reemplaza por `"Error al guardar el registro"`.

---

## Ejemplos de Implementación

Service que encripta antes de guardar:

```java
@Service
@RequiredArgsConstructor
public class UsuarioService extends AbstractService<Usuario, UsuarioDTO, UsuarioPaginateDTO> {

    private final PasswordEncoder passwordEncoder;

    // Encripta la contraseña solo si viene en el DTO; el resto lo copia la aplicacion generica del DTO.
    // contrasena es @NoProyectable en la entidad: no se copia ni se responde
    @Override
    protected Usuario hookGuardarAplicarDto(UsuarioDTO dto, Usuario modelo) {
        modelo = super.hookGuardarAplicarDto(dto, modelo);
        if (dto.getContrasena() != null && !dto.getContrasena().isEmpty()) {
            modelo.setContrasena(passwordEncoder.encode(dto.getContrasena()));
        }
        return modelo;
    }
}
```

Service que evita duplicados antes de persistir:

```java
@Override
protected Producto hookGuardarEnBase(Producto modelo) {
    Producto existente = repository.findFirstByCodigo(modelo.getCodigo());
    if (existente != null && !existente.getId().equals(modelo.getId())) {
        throw new ExcepcionDeApi("El código ya está registrado");
    }
    return super.hookGuardarEnBase(modelo);
}
```

Service con autorización que depende de los datos (el permiso fijo del endpoint ya lo validó `@Contrato`):

```java
// Exige PRODUCTO_CREAR en un alta (sin uuid) y PRODUCTO_EDITAR en una edicion
@Override
protected void hookGuardarAuth(ProductoDTO dto) {
    PermisoEnum requerido = dto.getUuid() == null ? PermisoEnum.PRODUCTO_CREAR : PermisoEnum.PRODUCTO_EDITAR;
    if (!usuarioActual.tienePermisos(List.of(requerido)))
        throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO);
}
```

Si el guardado necesita un subconjunto de campos, o el alta pide menos datos que el DTO completo, no se usa este endpoint: se crea un caso de uso (ver [controllers-y-endpoints.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/controllers-y-endpoints.md)).

---

## Seguridad

La autorización la declara el `permiso` del `@Contrato` del método y la hace cumplir `InterceptorDeContrato` antes de ejecutarlo (y antes de `@Valid`: sin permiso responde `403` aunque el body sea inválido), a través del puerto `AutorizadorDeEndpoint`:

| Proyecto | Autorizador | Efecto |
|---|---|---|
| Con `core-identity` | `AutorizadorPorRolesYPermisos` | Exige alguno de los roles o permisos del `@Contrato`; otro usuario recibe `403` y una request sin sesión `401` |
| Sin `core-identity` | `AutorizadorDenegarTodo` | Solo pasan los endpoints `Contrato.PUBLICO`; el resto responde `401` (`NO_AUTENTICADO`) |

Si el controller no escribe este endpoint, responde `404`, no `403`.

No devolver en la respuesta datos sensibles (contraseñas, tokens): el atributo de la entidad se marca con `@NoProyectable`, así no se proyecta ni se copia del DTO.

---

## Respuestas

### 200 OK

Body: el DTO guardado.

```json
{
  "uuid": "0198c0a4-7f3e-7b21-9c1d-5a2e8f4b6d10",
  "status": 0,
  "fechaDeCreacion": "2026-06-29T14:30:45.123-03:00",
  "codigo": "P-001",
  "nombre": "Producto de ejemplo"
}
```

### Errores

Formato completo del body de error: [errores/README.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/arquitectura/errores/README.md).

| Status | `mensajeParaUsuario` | Cuándo |
|---|---|---|
| `400` | `Error en el formulario` | Falló una validación `@Valid`. El detalle por campo va en `mensajeParaDesarrollador` como `{campo=mensaje}` |
| `400` | `Error con los datos enviados` | JSON ilegible, campo desconocido o tipo de dato incorrecto |
| `400` | `No se puede activar un elemento que fue eliminado` | `aplicarCamposBase` con registro `-1` y DTO en `0`. `mensajeParaDesarrollador`: `EntidadConBorradoLogico \| aplicarCamposBase \| error uuid: ...` |
| `400` | `Error status` | `status` del DTO distinto de `0` y `-1` |
| `400` | `No existe el registro relacionado indicado` (`RELACION_NO_ENCONTRADA`) | Un `@Columna("relacion.uuid")` del DTO trae un uuid que no existe o fue eliminado. `datos.campo`: nombre del campo del DTO |
| `400` | `Error creando la entidad` | La entidad no tiene constructor sin argumentos |
| `400` | `Error al guardar el registro` | Excepción inesperada; el detalle va en `mensajeParaDesarrollador` (`Error en guardar: ...`) |
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
- `EntidadBaseDTO` → `com.blue.open.Core.Kernel.DTO` (core-kernel)
- `Proyector`, `AplicaDto`, `Columna`, `NoProyectable` → `com.blue.open.Core.Kernel.Proyeccion` (core-kernel)
- `CodigoDeErrorCrud` → `com.blue.open.Core.Crud.Excepciones` (core-crud)
- `ProveedorDeUsuarioActual` → `com.blue.open.Core.Kernel.Puertos` (core-kernel)
