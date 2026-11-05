# sm-core-maven

El Core de BlueOpenData: los módulos Maven (`com.blue.open:core-*`) que usan todos los proyectos backend. Se publica
como repositorio Maven público en **https://sm-soluciones.github.io/sm-core-maven**, así que cualquier proyecto lo
descarga sin token.

Acá vive solo el código de los módulos y la documentación para implementarlos. Las reglas de trabajo, la
arquitectura, la auditoría y los ADRs son del proyecto: viven en `docs/` de cada proyecto, con
[`sm-template/backend-v2/docs`](https://github.com/SM-SOLUCIONES/sm-template/tree/main/backend-v2/docs) como referencia.

## Módulos

| Módulo | Para qué | Documentación |
|---|---|---|
| `core-kernel` | Obligatorio. Paginado, entidades base, Proyección, excepciones, fechas GMT-3, trazabilidad, `API-Version`, smoke tests, `@Contrato`, puertos | [`core-kernel/docs/proyeccion.md`](core-kernel/docs/proyeccion.md) |
| `core-crud` | Lógica del ABM de una entidad (`AbstractService`) | [`core-crud/docs/`](core-crud/docs/README.md) (receta del controller, paginado, obtener, guardar, borrar) |
| `core-identity` | Usuarios, roles, permisos, login por contraseña, recuperación de contraseña | |
| `core-monoempresa` | Scoping simple: un usuario tiene roles y punto | |
| `core-multiempresa` | Scoping por empresa (`X-Empresa-Uuid`), registro público con alta de empresa | |
| `core-protocolo-jwt` | Login JWT + refresh. Requiere `jwt.secreto` | |
| `core-protocolo-apikey` | Tokens de acceso personales | |
| `core-protocolo-sso` | SSO externo y Malvinas | [`core-protocolo-sso/docs/certificados-malvinas.md`](core-protocolo-sso/docs/certificados-malvinas.md) |
| `core-mail` | Envío de emails | |
| `core-crypter` | Archivos encriptados | |
| `core-sm3` | Archivos públicos | |
| `core-captcha` | Verificación de hCaptcha | |
| `core-test` | Scope test. Pruebas de contrato (PostgreSQL embebido, MockMvc, emisor del Contrato de endpoint) | |

`core-parent` es el pom padre: versiona todos los módulos juntos y es el `<parent>` del `app/pom.xml` de cada proyecto.

Cómo elegir módulos, el catálogo de puertos y las propiedades: `docs/arquitectura/core/README.md` del proyecto.

## Usarlo desde un proyecto

En `app/pom.xml`:

```xml
<parent>
    <groupId>com.blue.open</groupId>
    <artifactId>core-parent</artifactId>
    <version>4.0.0</version>
    <relativePath/>
</parent>

<repositories>
    <repository>
        <id>sm-core</id>
        <url>https://sm-soluciones.github.io/sm-core-maven</url>
    </repository>
</repositories>

<dependencies>
    <!-- Sin <version>: la trae core-parent -->
    <dependency>
        <groupId>com.blue.open</groupId>
        <artifactId>core-kernel</artifactId>
    </dependency>
</dependencies>
```

Actualizar el Core es cambiar la versión del `<parent>` y seguir los pasos de su [changelog](changelog/README.md).

## Desarrollar el Core

Requiere JDK 25.

```bash
./mvnw verify            # compila y corre los tests de todos los módulos
./mvnw install -DskipTests   # instala la versión actual en ~/.m2 para probarla desde un proyecto local
```

Para probar un cambio desde un proyecto antes de publicarlo: subir la versión a una `-SNAPSHOT`, `./mvnw install`, y
poner esa versión en el `<parent>` del `app/pom.xml` del proyecto.

El Core nunca importa `com.blue.open.Code`, y toda política tiene un default reemplazable sin tocar el Core (ver
"Las dos reglas" en `docs/arquitectura/core/README.md` del proyecto).

## Publicar una versión

Semver: cambio que rompe compatibilidad → major; módulo o puerto nuevo → minor; arreglo → patch.

1. Subir la versión. Tiene que coincidir en el `<version>` y el `core.version` del `pom.xml` raíz, y en el
   `<parent><version>` de cada `core-*/pom.xml`:

   ```bash
   ./mvnw versions:set -DnewVersion=4.1.0 -DprocessAllModules -DgenerateBackupPoms=false
   ./mvnw versions:set-property -Dproperty=core.version -DnewVersion=4.1.0 -DgenerateBackupPoms=false
   ```

2. Escribir `changelog/{version}.md` (qué cambia y cómo actualizar un proyecto) y agregarlo a `changelog/README.md`.
3. Mergear a `main`, crear el tag y pushearlo:

   ```bash
   git tag v4.1.0
   git push origin v4.1.0
   ```

El workflow [`publicar.yml`](.github/workflows/publicar.yml) verifica que el tag coincida con la versión, corre los
tests y publica `core-parent` y todos los módulos (con su jar de fuentes) en la rama `gh-pages`, que GitHub Pages sirve
como repositorio Maven. Una versión publicada no se vuelve a publicar: si hay que corregirla, se publica una nueva.

### Configuración del repositorio (una sola vez)

- El repositorio tiene que ser público, para que GitHub Pages y la descarga funcionen sin token.
- Settings → Pages → Source: "Deploy from a branch", rama `gh-pages`, carpeta `/ (root)`. La rama la crea la primera
  publicación.
