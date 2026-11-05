# Endpoint DELETE /borrar/{uuid} - Core

## Descripción General

El endpoint `DELETE /borrar/{uuid}` elimina un registro existente. Cómo lo elimina depende de la clase de la entidad (`EstrategiaDeBorrado`, resuelta por `SelectorDeBorrado`):

- Entidad que extiende `EntidadConBorradoLogico` (caso por defecto): **borrado lógico**. Marca `status = -1` y registra fecha y usuario de eliminación. El resto de este documento describe este caso.
- Entidad que extiende solo `EntidadBase`: **borrado físico**. Ejecuta un `DELETE` real de la fila; no hay columna `status`.

**Método HTTP**: `DELETE`  
**Ruta**: `/{controller}/borrar/{uuid}`  
**Parámetros**: UUID del registro a eliminar (path parameter)  
**Respuesta**: `DTO` del registro eliminado, con la misma Proyección que `GET /uuid`  
**Transaccional**: Sí (modificación de BD)

---

## Concepto de Soft Delete (entidades con borrado lógico)

En una entidad con borrado lógico el registro nunca se elimina físicamente. En su lugar:

1. Marca el registro con `status = -1`
2. Registra `fechaDeBorrado` con timestamp actual
3. Asigna `idUsuarioDeBorrado` con ID del usuario que eliminó
4. El registro permanece en BD para auditoría

**Ventajas**:
- Recuperable si es necesario (con override)
- Mantiene integridad referencial
- Auditoría completa
- Sin pérdida de datos

---

## Estructura del Endpoint

### URL

```
DELETE /api/{entidad}/borrar/{uuid}
```

**Parámetros**:
- `{uuid}` (path parameter, requerido): UUID del registro a eliminar

**Ejemplo**:
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
```

---

## Ejemplos de Requests

### Ejemplo 1: Eliminar Usuario

**Request**:
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
```

**Response (200 OK)**:
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan Pérez",
  "email": "juan@example.com",
  "status": -1,
  "fechaDeCreacion": "2025-01-15T10:30:00",
  "fechaDeActualizacion": "2025-06-28T14:45:30",
  "fechaDeBorrado": "2025-06-29T14:30:45"
}
```

**Nota**: Después de eliminar, el registro tiene `status = -1` y `fechaDeBorrado` establecido.

---

### Ejemplo 2: Intentar Eliminar Registro Inexistente

**Request**:
```
DELETE /api/usuario/borrar/770e8400-e29b-41d4-a716-446655440099
```

**Response (400 Bad Request)**:
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

---

### Ejemplo 3: Intentar Eliminar Dos Veces

**Primer DELETE** (exitoso):
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
Status: 200 OK
```

**Segundo DELETE** (error):
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
Status: 400 Bad Request
{
  "mensajeParaUsuario": "No existe o ya fue eliminado"
}
```

**Razón**: Después del primer borrado, el registro tiene `status = -1`. El servicio busca registros con `status = 0` (activos).

---

## Respuestas

### Exitosa (200 OK)

```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan Pérez",
  "email": "juan@example.com",
  "status": -1,
  "fechaDeCreacion": "2025-01-15T10:30:00",
  "fechaDeActualizacion": "2025-06-28T14:45:30",
  "fechaDeBorrado": "2025-06-29T14:30:45"
}
```

**Características**:
- `status = -1` (eliminado)
- `fechaDeBorrado` contiene el timestamp actual
- `idUsuarioDeBorrado` contiene ID del usuario que eliminó

---

### Errores Posibles

#### 1. Registro no encontrado o ya eliminado (400 Bad Request)

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

**Cuándo ocurre**:
- UUID no existe en BD
- Registro ya está eliminado (status = -1)
- UUID inválido o no encontrado

---

#### 2. Usuario no Autenticado (401 Unauthorized)

```json
{
  "fechaHora": "2026-06-29T14:30:45.000-03:00",
  "codigo": "NO_AUTENTICADO",
  "status": "UNAUTHORIZED",
  "mensajeParaUsuario": "Usuario no identificado",
  "errores": [],
  "traceId": "9f1c2f4e-2f1a-4c9a-9f0e-2b3d4a5c6d7e"
}
```

---

#### 3. Acceso Denegado (403 Forbidden)

**Cuándo ocurre** (`SIN_PERMISO`; una request sin sesión recibe antes `401 NO_AUTENTICADO`):
- Usuario sin permisos para eliminar
- Por defecto solo `SYSADMIN`
- Personalizable en subclases

---

#### 4. Error Interno (500 Internal Server Error)

```json
{
  "fechaHora": "2026-06-29T14:30:45.000-03:00",
  "codigo": "ERROR_INTERNO",
  "status": "INTERNAL_SERVER_ERROR",
  "mensajeParaUsuario": "Error interno del servidor",
  "errores": [],
  "traceId": "9f1c2f4e-2f1a-4c9a-9f0e-2b3d4a5c6d7e"
}
```

---

## Flujo de Ejecución

```
1. Cliente envía DELETE /api/{entidad}/borrar/{uuid}
   ↓
2. InterceptorDeContrato              [Permiso del @Contrato del método: 401 sin sesión, 403 si no lo cumple]
   ↓
   {Entidad}Controller.borrar(uuid)    [Receta: solo delega]
   ├─ service.borrar(uuid)
   │  ↓
3. AbstractService.borrar(uuid)
   ├─ hookBorrarAuth(uuid)             [Autorización por datos - vacío por defecto]
   ├─ hookBorrarBuscarPorUuid(uuid)       [Buscar registro vigente]
   │  └─ EstrategiaDeBorrado.buscarVigentePorUuid(repository, uuid)
   │
   ├─ Validar que existe (Optional vacío -> REGISTRO_NO_ENCONTRADO)
   ├─ hookBorrarProyectar(uuid)             [Proyección previa al borrado]
   ├─ hookBorrarAplicarBorrado(modelo)       [status = -1 + fecha + usuario, o DELETE físico]
   ├─ hookBorrarProyectar(uuid)             [Proyección posterior; vacía en borrado físico]
   └─ return la posterior, o la previa si el registro ya no está en la tabla
   │
4. ResponseEntity<DTO> sent to client
```

---

## Estados del Registro

### Antes de Eliminar

```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan Pérez",
  "status": 0,
  "fechaDeCreacion": "2025-01-15T10:30:00",
  "fechaDeActualizacion": "2025-06-28T14:45:30",
  "fechaDeBorrado": null,
  "idUsuarioDeCreacion": 5,
  "idUsuarioDeActualizacion": 5,
  "idUsuarioDeBorrado": null
}
```

### Después de Eliminar

```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan Pérez",
  "status": -1,
  "fechaDeCreacion": "2025-01-15T10:30:00",
  "fechaDeActualizacion": "2025-06-28T14:45:30",
  "fechaDeBorrado": "2025-06-29T14:30:45",
  "idUsuarioDeCreacion": 5,
  "idUsuarioDeActualizacion": 5,
  "idUsuarioDeBorrado": 7
}
```

**Cambios**:
- `status`: `0` → `-1`
- `fechaDeBorrado`: `null` → Timestamp actual
- `idUsuarioDeBorrado`: `null` → ID de usuario actual

---

## Hooks Disponibles

### En el Controlador

No tiene hooks: el controller se escribe con la [receta](receta-controller.md) y solo delega. El permiso fijo del endpoint va en el `permiso` de su `@Contrato`, que hace cumplir `InterceptorDeContrato` antes de ejecutarlo.

### En el Servicio

```java
protected void hookBorrarAuth(String uuid)

protected Optional<T> hookBorrarBuscarPorUuid(String uuid)

protected T hookBorrarAplicarBorrado(T modelo)

protected Optional<DTO> hookBorrarProyectar(String uuid)
```

#### Detalles de Hooks

**`hookBorrarAuth(uuid)`**:
- Validación adicional en servicio
- Por defecto vacío
- Se ejecuta después de la validación del `@Contrato`
- Útil para autorización que depende de los datos (ej: un usuario que solo puede eliminarse a sí mismo) y validaciones de negocio. Nunca un permiso fijo: ese va en `@Contrato`

**`hookBorrarBuscarPorUuid(uuid)`**:
- Busca registro existente por UUID
- Con borrado lógico busca SOLO registros activos (`status = 0`); con borrado físico, cualquiera
- Por defecto: `EstrategiaDeBorrado.buscarVigentePorUuid(repository, uuid)`
- Retorna `Optional.empty()` si no existe o está eliminado

**`hookBorrarAplicarBorrado(modelo)`**:
- Aplica el borrado según la estrategia de la entidad
- Borrado lógico: `status = -1`, `fechaDeBorrado = now()`, `idUsuarioDeBorrado` = usuario actual, y `repository.save(modelo)`
- Borrado físico: `repository.delete(modelo)`
- Personalizable para lógica adicional (borrar dependientes, notificar, etc.)

**`hookBorrarProyectar(uuid)`**:
- Proyección del registro por uuid, con la misma forma que `GET /uuid` (sin filtrar por `status`)
- Por defecto: `proyector.porUuid(entidad, uuid, DTO)`
- Se llama antes de borrar y otra vez después: con borrado lógico responde el registro con `status = -1`; con borrado físico (ya no está en la tabla) responde la Proyección previa
- Los datos sensibles no se limpian acá: se excluyen con `@NoProyectable` en la entidad

---

## Cómo Implementar en una Subclase

### Controlador

Receta completa en [receta-controller.md](receta-controller.md). El endpoint declara quién puede llamarlo y delega:

```java
// Elimina un usuario. Responde el usuario eliminado
@DeleteMapping("/borrar/{uuid}")
@Contrato(casoDeUso = "CU-USU-04", resumen = "Elimina un usuario", permiso = Contrato.AUTENTICADO)
public ResponseEntity<UsuarioDTO> borrar(@Descripcion("Uuid del registro") @PathVariable("uuid") String uuid) {
    return new ResponseEntity<>(service.borrar(uuid), HttpStatus.OK);
}
```

Como "solo administradores pueden eliminar otros usuarios; un usuario regular solo a sí mismo" depende de los datos, va en el `hookBorrarAuth` del service:

```java
@Override
protected void hookBorrarAuth(String uuid) {
    UsuarioBase actual = authService.getActualObligatorio();
    boolean esElMismo = this.repository.buscarPorUuid(uuid)
            .map(usuario -> usuario.getId().equals(actual.getId()))
            .orElse(false);
    if (!esElMismo && !authService.tienePermisos(List.of(RolBaseEnum.ADMIN)))
        throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO);
}
```

### Servicio

```java
@Service
@RequiredArgsConstructor
public class UsuarioService extends AbstractService<Usuario, UsuarioDTO, UsuarioDTO> {

    private final OrdenRepository ordenRepository;
    
    // Validar que no hay órdenes pendientes antes de eliminar
    @Override
    protected void hookBorrarAuth(String uuid) {
        this.repository.buscarVigentePorUuid(uuid).ifPresent(usuario -> {
            Long ordenesPendientes = ordenRepository.countByUsuarioIdAndStatusNot(usuario.getId(), -1);
            if (ordenesPendientes > 0) {
                throw new ExcepcionDeApi(
                    "No se puede eliminar usuario con órdenes pendientes",
                    "Usuario " + uuid + " tiene " + ordenesPendientes + " órdenes activas"
                );
            }
        });
    }
    
    // Auditoría detallada al eliminar
    @Override
    protected Usuario hookBorrarAplicarBorrado(Usuario modelo) {
        logger.warn("Usuario eliminado: {} (id: {}) por usuario {}", 
            modelo.getEmail(),
            modelo.getId(),
            authService.getActualONull().getId());
        
        return this.repository.save(modelo);
    }
    
    // Los campos sensibles no se responden: son @NoProyectable en la entidad
}
```

---

## Diferencias con Soft vs Hard Delete

### Soft Delete (Implementado)

```
DELETE → status = -1, fechaDeBorrado = now()
Resultado: Registro permanece en BD, marca como eliminado
```

**Ventajas**:
- Recuperable
- Auditoría completa
- Mantiene integridad referencial
- Historial disponible

**Desventajas**:
- Base de datos crece indefinidamente
- Queries más complejas (siempre filtrar status = 0)
- Requiere espacios disco más grandes

### Hard Delete (implementado: entidad que extiende solo `EntidadBase`)

```
DELETE → Registro se elimina de la BD físicamente (BorradoFisico)
```

**Ventajas**:
- Datos realmente borrados
- Base de datos más compacta

**Desventajas**:
- No recuperable
- Pérdida de auditoría
- Problemas de integridad referencial
- Imposible saber quién lo eliminó y cuándo

---

## Búsqueda en Base de Datos

### Query SQL

`EstrategiaDeBorrado.buscarVigentePorUuid(repository, uuid)`, que devuelve `Optional<T>`. En borrado lógico (`BorradoLogico`) hace `repository.buscarPorUuid(uuid)` y devuelve el registro solo si `status == 0`; en borrado físico (`BorradoFisico`) devuelve lo que encuentre `buscarPorUuid`.

**Características**:
- Con borrado lógico busca SOLO registros activos
- Si status = -1 (ya eliminado), retorna null
- Sin relaciones automáticas

---

## Cascada de Eliminación

Si el registro tiene relaciones con otras entidades:

```
Usuario (id: 1) 
├─ Órdenes (5 registros)
└─ Contactos (3 registros)
```

**Comportamiento actual**:
- Usuario se marca como eliminado (status = -1)
- Órdenes y contactos permanecen sin cambios
- **Importante**: Las relaciones no se eliminan automáticamente

**Si necesitas eliminar relaciones**:

```java
@Override
protected Usuario hookBorrarAplicarBorrado(Usuario modelo) {
    // Eliminar órdenes asociadas
    List<Orden> ordenes = ordenRepository.findByUsuarioId(modelo.getId());
    for (Orden orden : ordenes) {
        orden.setStatus(-1);
        orden.setFechaDeBorrado(FechaHoraConfig.ahoraTimestamp());
        ordenRepository.save(orden);
    }
    
    return this.repository.save(modelo);
}
```

---

## Buenas Prácticas

### 1. **Validar Dependencias Antes de Eliminar**

```java
@Override
protected void hookBorrarAuth(String uuid) {
    this.repository.buscarVigentePorUuid(uuid).ifPresent(usuario -> {
        // Validar que no hay datos críticos asociados
        Long deudas = cuentaRepository.countByUsuarioIdAndSaldoNot(usuario.getId(), 0);
        if (deudas > 0) {
            throw new ExcepcionDeApi(
                "No se puede eliminar usuario con deudas pendientes"
            );
        }
    });
}
```

### 2. **Auditoría Detallada**

```java
@Override
protected Usuario hookBorrarAplicarBorrado(Usuario modelo) {
    UsuarioBase usuarioActual = authService.getActualONull();
    
    logger.info(
        "AUDIT: Usuario {} eliminado por {} en {}",
        modelo.getUuid(),
        usuarioActual.getEmail(),
        FechaHoraConfig.ahoraLocalDateTime()
    );
    
    return this.repository.save(modelo);
}
```

### 3. **Notificaciones al Eliminar**

```java
@Override
protected Usuario hookBorrarAplicarBorrado(Usuario modelo) {
    Usuario saved = this.repository.save(modelo);
    
    // Enviar notificación
    enviadorDeMail.enviar(
        modelo.getEmail(),
        "Tu cuenta ha sido eliminada",
        "Tu cuenta fue eliminada. Contacta a soporte si fue un error."
    );
    
    return saved;
}
```

### 4. **Registrar en Tabla de Auditoría**

```java
@Override
protected Usuario hookBorrarAplicarBorrado(Usuario modelo) {
    Usuario saved = this.repository.save(modelo);
    
    // Registrar en tabla separada
    AuditLog log = new AuditLog();
    log.setAccion("DELETE");
    log.setTabla("usuarios");
    log.setRegistroId(modelo.getId());
    log.setUsuarioId(authService.getActualONull().getId());
    log.setFecha(FechaHoraConfig.ahoraTimestamp());
    
    auditRepository.save(log);
    
    return saved;
}
```

### 5. **No Retornar Datos Sensibles**

```java
// En la entidad: ninguna Proyeccion lo trae (uuid, paginar, guardar ni borrar) y al guardar no se copia
@NoProyectable
private String secretKey;
```

---

## Casos de Uso

### Caso 1: Eliminar Usuario

**Request**:
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
```

**Response (200 OK)**:
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan Pérez",
  "email": "juan@example.com",
  "status": -1,
  "fechaDeBorrado": "2025-06-29T14:30:45",
  "idUsuarioDeBorrado": 1
}
```

---

### Caso 2: Intentar Eliminar Dos Veces

**Primer DELETE**:
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
Status: 200 OK
```

**Segundo DELETE**:
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
Status: 400 Bad Request
{
  "mensajeParaUsuario": "No existe o ya fue eliminado"
}
```

**Razón**: Después del primer borrado, `status = -1`, y el hook busca registros con `status = 0`.

---

### Caso 3: Validar Dependencias Antes de Eliminar

**Implementación**:
```java
@Override
protected void hookBorrarAuth(String uuid) {
    this.repository.buscarVigentePorUuid(uuid).ifPresent(usuario -> {
        Long deudas = cuentaRepository.countByUsuarioIdAndSaldoGreaterThan(usuario.getId(), 0);
        if (deudas > 0) {
            throw new ExcepcionDeApi(
                "No se puede eliminar usuario con deudas",
                "El usuario tiene " + deudas + " cuentas pendientes de pago"
            );
        }
    });
}
```

**Request**:
```
DELETE /api/usuario/borrar/550e8400-e29b-41d4-a716-446655440000
```

**Response (400 Bad Request)**:
```json
{
  "status": "BAD_REQUEST",
  "mensajeParaUsuario": "No se puede eliminar usuario con deudas"
}
```

---

## Diferencias con Otros Endpoints

| Característica | DELETE /borrar/{uuid} | GET /uuid/{uuid} | POST /guardar | POST /paginar |
|---|---|---|---|---|
| **Método HTTP** | DELETE | GET | POST | POST |
| **Modifica datos** | Sí (soft delete) | No | Sí | No |
| **Elimina registros** | Sí (soft) | No | No | No |
| **Body requerido** | No | No | DTO | PedidoDePaginado |
| **Transactional** | Sí | No | Sí | No |
| **Auditoría** | Completa | Ninguna | Completa | Ninguna |
| **Recuperable** | Sí | N/A | N/A | N/A |
| **Status resultante** | `-1` | Cualquiera | `0` o el asignado | Cualquiera activo |

---

## Recuperación de Registros Eliminados

`POST /guardar` **no** reactiva un registro eliminado: un `uuid` de un registro con `status = -1` no lo encuentra `hookGuardarBuscarExistente`, y `EntidadConBorradoLogico.aplicarCamposBase` rechaza el paso de `-1` a `0` con `"No se puede activar un elemento que fue eliminado"` (ver [guardar.md](guardar.md)).

Si el negocio necesita reactivar, no se hace con los endpoints del ABM: se crea un caso de uso propio (ver [controllers-y-endpoints.md](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/controllers-y-endpoints.md)) que cargue el registro eliminado y le vuelva a poner `status = 0`, `fechaDeBorrado = null` e `idUsuarioDeBorrado = null`.

---

## Seguridad

### Autenticación
- La declara el `permiso` del `@Contrato` del método (`Contrato.PUBLICO` no exige sesión)

### Autorización
- Permiso fijo: `@Contrato`, que hace cumplir `InterceptorDeContrato` antes de ejecutar el método
- Por datos: `hookBorrarAuth(uuid)` del service
- Si el controller no escribe este endpoint, responde `404`, no `403`

### Inyección SQL
- Protegida: **Sí** (JPA/Hibernate)

### Irreversibilidad
- Los cambios son inmediatos (transaccionales)
- No hay opción de "undo" automático
- Mantener backups es responsabilidad del sistema

---

## Referencia de Clases

- `AbstractService` → `com.blue.open.Core.Crud` (core-crud)
- `Contrato`, `InterceptorDeContrato` → `com.blue.open.Core.Kernel.Contrato` (core-kernel)
- `EntidadBase` → `com.blue.open.Core.Kernel.Entidad` (core-kernel; `EntidadConBorradoLogico` para borrado lógico)
- `EntidadRepository` → `com.blue.open.Core.Kernel.Repository` (core-kernel; `EntidadRepositoryBorradoLogico` para borrado lógico)
