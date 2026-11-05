# Changelog del Core

Historial de cambios por versión del Core.

Desde 2.0.0 el Core es un conjunto de módulos Maven versionados juntos por `core-parent` (propiedad `core.version`
en el `pom.xml` raíz). Desde 4.0.0 vive en este repositorio y se publica en
`https://sm-soluciones.github.io/sm-core-maven`: los proyectos ya no lo copian. La versión vigente se lee en runtime
con `CoreVersion.get()`, que la toma del artefacto y no de un literal escrito a mano.

Hasta 3.7.0 los links de estos archivos a `docs/` apuntan a la documentación del template
(`sm-template/backend-v2/docs/`), que desde 4.0.0 es del proyecto y no del Core.

## Versiones

| Versión | Archivo |
|---------|---------|
| 4.0.0 | [4.0.0.md](4.0.0.md) — el Core pasa a su propio repositorio (`sm-core-maven`) y se publica como repositorio Maven público; `core/` desaparece de los proyectos, la documentación general pasa a `docs/` del proyecto, se elimina `IntegridadDelCoreTest` y `jwt.secreto` deja de tener valor por defecto |
| 3.7.0 | [3.7.0.md](3.7.0.md) — auditoría de cumplimiento: reglas nuevas de logs y de archivos, checklist con id y severidad por regla, comando `/auditar`, registro de excepciones por proyecto, tests `ArquitecturaTest`/`EstiloDeCodigoTest` y manifiesto de integridad del Core (`IntegridadDelCoreTest`); ADR 0006 (Core como artefacto Maven, propuesto) |
| 3.6.1 | [3.6.1.md](3.6.1.md) — la cookie `refresh_token` se limita al `Path` del login (`/api/usuario`) y `cerrar-sesion` la borra con el mismo `Path` |
| 3.6.0 | [3.6.0.md](3.6.0.md) — los protocolos `jwt` y `malvinas` leen el access token de la cookie `access_token` (el header `Authorization` tiene prioridad), el login JWT emite `access_token` y `refresh_token` como cookies `HttpOnly`, `POST /usuario/cerrar-sesion` y `ORIGEN_NO_PERMITIDO` como protección CSRF |
| 3.5.0 | [3.5.0.md](3.5.0.md) — `@Descripcion` en el getter gana sobre la del campo (un DTO hijo redefine un campo heredado), `RolBaseService<D extends RolDTO>` y `RolBaseMultiempresaService<D>` genéricos sobre el DTO, descripciones neutrales de `nombreDeUsuario`, `email` y `telefono`, y paginado por el nombre de un campo `@Columna` del DTO |
| 3.4.1 | [3.4.1.md](3.4.1.md) — `SanitizadorDeLogs` tapa toda clave que contenga una raíz sensible (`contrasenaActual`, `contrasenaNueva`, `clientSecret`), con el mismo criterio en `ExcepcionDeApi.conDato` |
| 3.4.0 | [3.4.0.md](3.4.0.md) — convivencia de JWT propio (`HS256`) y Malvinas (`ES256`) como Bearer, puerto `PoliticaDeAccesoExterno` consultado por Malvinas antes de crear o guardar el usuario, y `renovarToken` rechaza a un usuario que no está en `status = 0` |
| 3.3.2 | [3.3.2.md](3.3.2.md) — un archivo que supera el limite de multipart responde `413 ARCHIVO_DEMASIADO_GRANDE` con el formato de error de la API |
| 3.3.1 | [3.3.1.md](3.3.1.md) — el CORS por defecto expone `Content-Disposition`, `X-Trace-Id` y `API-Version` (`Access-Control-Expose-Headers`) para un front en otro origen |
| 3.3.0 | [3.3.0.md](3.3.0.md) — código y contrato en español (rutas, JSON, tablas, columnas, clases, hooks, propiedades y `X-Empresa-Uuid`); el borrado del ABM pasa a `DELETE /{entidad}/borrar/{uuid}` y las rutas viejas responden 404 |
| 3.2.0 | [3.2.0.md](3.2.0.md) — una request sin sesión responde `401 NO_AUTENTICADO` en vez de `403 SIN_PERMISO`; `SIN_PERMISO` queda para un usuario autenticado sin rol, permiso o alcance |
| 3.1.0 | [3.1.0.md](3.1.0.md) — `@Descripcion` obligatoria en cada constante de un `CodigoDeError` (columna "Cuándo ocurre" del catálogo de errores del contrato), verificada por `CodigosDeErrorTest` |
| 3.0.2 | [3.0.2.md](3.0.2.md) — Proyección por defecto en el ABM (`@Coleccion`, `@NoProyectable`, `AplicaDto`, `Proyector`, `RELACION_NO_ENCONTRADA`); se eliminan `AbstractMapperComplete` y todos los mappers |
| 3.0.1 | [3.0.1.md](3.0.1.md) — `@Trazar` solo en endpoints críticos (dinero, pagos, facturas, comprobantes); se quita de los controllers del Core y de la receta del ABM |
| 3.0.0 | [3.0.0.md](3.0.0.md) — autorización centralizada en `@Contrato` (interceptor + verificación al arrancar); se elimina `AbstractController` (receta de controller) y `authService.valid` |
| 2.5.1 | [2.5.1.md](2.5.1.md) — pruebas de contrato en proyectos multi-empresa con JWT: `@Descripcion` en los DTOs de `core-multiempresa` y `core-protocolo-jwt`, `CompanyContextDTO` genérico y `FORMULARIO_INVALIDO` para `List<@Valid DTO>` |
| 2.5.0 | [2.5.0.md](2.5.0.md) — pruebas de contrato: anotaciones `@Contrato`/`@Descripcion` y módulo `core-test` (Contrato de endpoint en OpenAPI generado desde las pruebas, reemplaza a Bruno) |
| 2.4.1 | [2.4.1.md](2.4.1.md) — completa la migración a `CodigoDeError`/`CustomException` en todos los módulos restantes (2.4.0 solo migró `core-kernel`) |
| 2.4.0 | [2.4.0.md](2.4.0.md) — códigos de error estables (`codigo`), `developerMessage`/`cause` dejan de viajar en la respuesta. Migra `core-kernel`; el resto de los módulos se migra en versiones/commits siguientes |
| 2.3.0 | [2.3.0.md](2.3.0.md) — los buscadores de 0-o-1 registro devuelven `Optional` en vez de `null` |
| 2.2.0 | [2.2.0.md](2.2.0.md) — sin inyección por campo: constructor en clases concretas, setter en clases base |
| 2.1.0 | [2.1.0.md](2.1.0.md) — soporte de PostgreSQL además de MariaDB |
| 2.0.2 | [2.0.2.md](2.0.2.md) — arranque con core-multiempresa, paginado JPQL |
| 2.0.1 | [2.0.1.md](2.0.1.md) — seguridad de altas públicas, isSysAdmin, alta/borrado de Company y User, respuestas de error |
| 2.0.0 | [2.0.0.md](2.0.0.md) — split en módulos, puertos, endpoints opt-in, borrado físico |
| 1.4.0 | [1.4.0.md](1.4.0.md) |
| 1.3.0 | [1.3.0.md](1.3.0.md) |
| 1.1.3 | [1.1.3.md](1.1.3.md) |
| 1.1.2 | [1.1.2.md](1.1.2.md) |

## Parches locales

Hasta 1.4.0 existía `modificaciones-del-core.md`, donde cada proyecto registraba los parches que le
hacía al Core embebido para poder reaplicarlos al actualizar. **Ese archivo se eliminó en 2.0.0.**

El Core ya no se parchea: es una dependencia versionada (y desde 4.0.0 el proyecto ni siquiera tiene su código) y todo lo que antes obligaba a tocarlo tiene
ahora un punto de extensión. Cuando un proyecto no entra en el molde, la salida es, en este orden:

1. Configuración (una propiedad en `application.properties`).
2. Hook (los `hookXxx` de `AbstractService`).
3. Puerto: publicar un bean propio que reemplace al del Core (ver el catálogo en
   [`docs/arquitectura/core/README.md`](https://github.com/SM-SOLUCIONES/sm-template/blob/main/backend-v2/docs/arquitectura/core/README.md)).
4. No usar el módulo: borrar su dependencia del `pom.xml` y su changelog del master de Liquibase.
5. Issue al Core, que sale en la versión siguiente.

Si hace falta un sexto paso, es un bug del Core: falta un punto de extensión.
