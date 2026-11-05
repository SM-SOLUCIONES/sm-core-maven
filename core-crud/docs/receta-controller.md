# Receta: controller del ABM de una entidad

Desde Core 3.0.0 no existe `AbstractController`: el controller de una entidad se escribe explícito, con esta receta. Cada endpoint lleva su `@Contrato`, que **autoriza** el request (lo hace cumplir `InterceptorDeContrato` antes de ejecutar el método) y alimenta el contrato de la API. La lógica sigue en `AbstractService` (`core-crud`): el controller solo delega.

## Cuándo se usa

Cuando la entidad es **chica** (ver "Entidad chica" en [`docs/rules/entidades-y-dtos.md`](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/entidades-y-dtos.md)): sus casos de uso son un ABM que usa todos sus campos. Los casos de uso que no son ABM llevan su propio `UseCase` y DTO, en el mismo controller (ver [`docs/rules/controllers-y-endpoints.md`](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/controllers-y-endpoints.md)).

## Plantilla

```java
package com.blue.open.Code.Producto;

/**
 * Endpoints de productos: ABM (receta de core-crud/docs/receta-controller.md).
 * Dependencias externas: la base de datos.
 */
@RestController
@RequestMapping("producto")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService service;

    // Obtiene un producto por su uuid. Responde el ProductoDTO
    @GetMapping("/uuid/{uuid}")
    @Contrato(casoDeUso = "CU-PRO-02", resumen = "Obtiene un producto", permiso = "PRODUCTO_VER")
    public ResponseEntity<ProductoDTO> obtenerPorUuid(@Descripcion("Uuid del registro") @PathVariable("uuid") String uuid) {
        return new ResponseEntity<>(service.obtenerPorUuid(uuid), HttpStatus.OK);
    }

    // Lista los productos con filtros y orden. Responde la pagina de ProductoPaginateDTO
    @PostMapping("/paginar")
    @Contrato(casoDeUso = "CU-PRO-01", resumen = "Lista los productos con filtros y orden", permiso = "PRODUCTO_VER")
    public ResponseEntity<Pagina<ProductoPaginateDTO>> paginar(@RequestBody PedidoDePaginado pedido) throws Exception {
        return new ResponseEntity<>(service.paginar(pedido), HttpStatus.OK);
    }

    // Crea (sin uuid) o edita un producto. Responde el producto guardado
    @PostMapping("/guardar")
    @Contrato(casoDeUso = "CU-PRO-03", resumen = "Crea o edita un producto", permiso = "PRODUCTO_ADMINISTRAR")
    public ResponseEntity<ProductoDTO> guardar(@Valid @RequestBody ProductoDTO dto) {
        return new ResponseEntity<>(service.guardar(dto), HttpStatus.OK);
    }

    // Borrado logico (o fisico, segun la entidad) de un producto. Responde el producto eliminado
    @DeleteMapping("/borrar/{uuid}")
    @Contrato(casoDeUso = "CU-PRO-04", resumen = "Elimina un producto", permiso = "PRODUCTO_ADMINISTRAR")
    public ResponseEntity<ProductoDTO> borrar(@Descripcion("Uuid del registro") @PathVariable("uuid") String uuid) {
        return new ResponseEntity<>(service.borrar(uuid), HttpStatus.OK);
    }
}
```

`ProductoService` extiende `AbstractService<Producto, ProductoDTO, ProductoPaginateDTO>` como siempre (ver [README.md](README.md)). Una entidad con ABM completo se escribe con solo cinco archivos: entidad, DTO (y `PaginateDTO` si hace falta), repositorio, service y controller. La lectura (uuid, paginar y la respuesta de guardar y borrar) es una Proyección del DTO armada desde la consulta, y `guardar` aplica el DTO sobre la entidad (ver [obtener.md](obtener.md) y [guardar.md](guardar.md)).

## Reglas

- **Paths y nombres fijos.** `GET /uuid/{uuid}`, `POST /paginar`, `POST /guardar`, `DELETE /borrar/{uuid}`, con los métodos `obtenerPorUuid`, `paginar`, `guardar` y `borrar`. Así el front y `ContratoDeAbmTest` (`core-test`) los encuentran igual en todas las entidades. Ningún endpoint del ABM tiene un único segmento variable (`/{uuid}`): si lo tuviera, cualquier ruta de un segmento que no exista (`/paginate`, `/login`) respondería 405 en vez de 404.
- **Solo lo que se usa.** Un endpoint que la entidad no necesita no se escribe: responde 404. Reemplaza a `@EndpointsHeredados`. Ejemplo: `Code/Autorizacion/PermisoController` solo tiene uuid y paginar.
- **`@Contrato` siempre, con `permiso` explícito.** La aplicación no arranca si a un endpoint le falta (`VerificadorDeContratos`). Valores:
  - `Contrato.PUBLICO`: sin sesión.
  - `Contrato.AUTENTICADO`: con sesión, sin rol ni permiso puntual.
  - Nombres de roles o permisos, como la constante del enum (`"SYSADMIN"`, `"USUARIO_VER"`). Alcanza con tener uno: `permiso = { "PRODUCTO_ADMINISTRAR", "SYSADMIN" }`.
- **Sin `authService.valid(...)`.** El permiso fijo de un endpoint va en `@Contrato` y en ningún otro lado.
- **Autorización que depende de los datos** (alta vs. edición, alcance por empresa, dueño del registro): va en el `hookXxxAuth` del service, con `ProveedorDeUsuarioActual.tienePermisos(...)` (o `AuthService.tienePermisos`) y `throw new ExcepcionDeApi(SIN_PERMISO)`. Ejemplo: `UsuarioBaseService.hookGuardarAuth`, que exige `USUARIO_CREAR` o `USUARIO_EDITAR` según venga o no el `uuid`.
- **Sin `@Trazar`.** Los endpoints del ABM no se trazan, salvo que la entidad sea crítica para el negocio (pagos, facturas, comprobantes, dinero). Ver "Trazabilidad: `@Trazar`" en [`docs/rules/controllers-y-endpoints.md`](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/rules/controllers-y-endpoints.md).
- **`@Descripcion`** en cada `@PathVariable`.
- **Prueba de contrato:** una clase `{Entidad}ContratoTest extends ContratoDeAbmTest` cubre los casos comunes de los 4 endpoints (ver [`docs/arquitectura/testing/README.md`](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/arquitectura/testing/README.md)).

## Ejemplos reales

| Controller | Qué muestra |
|---|---|
| `Code/Empresa/EmpresaController` | ABM completo + un endpoint `PUBLICO` (`/contexto`) |
| `Code/Autorizacion/RolController` | ABM sobre una entidad del Core (`Rol`); el service agrega escalada y alcance por empresa |
| `Code/Autorizacion/PermisoController` | Solo lectura: uuid con `SYSADMIN` y paginar con `PERMISO_VER` |
| `Code/Usuario/UsuarioController` | ABM + casos de uso públicos, `AUTENTICADO` y con captcha |
