# Certificado de Malvinas

Los proyectos que usan `core-protocolo-sso` con Malvinas necesitan que el JDK confíe en el certificado del SSO (`sso-malvinas.crt`). Sin él, la validación del JWT falla por SSL.

## Origen del certificado

- El `sso-malvinas.crt` **es el mismo para todos los proyectos de Malvinas**.
- Si no lo tenés, pedilo al líder técnico.
- El archivo no está versionado en el template: cada proyecto lo agrega en su raíz.

## Con Docker (deploy)

1. Copiar `sso-malvinas.crt` en la raíz del proyecto.
2. Usar una plantilla `*-malvinas-sso` del deployer (ver `deploy/doc/02-stacks.md`).

El Dockerfile de la plantilla copia el certificado y lo importa al `cacerts` de la imagen durante el build. No hay que hacer nada más.

## Ejecución local fuera de Docker (IDE con JDK local)

Al ejecutar el código, la consola muestra la ubicación del JDK. Ejemplo de log: `Java Home: <RUTA_DEL_JDK>`.

### Windows (PowerShell)

```powershell
& "<RUTA_DEL_JDK>\bin\keytool.exe" -importcert -trustcacerts -cacerts -storepass changeit -noprompt -alias sso-malvinas -file "<CARPETA_DEL_PROYECTO>\sso-malvinas.crt"
```

### Ubuntu

```bash
sudo <RUTA_DEL_JDK>/bin/keytool -importcert -trustcacerts -cacerts -storepass changeit -noprompt -alias sso-malvinas -file <CARPETA_DEL_PROYECTO>/sso-malvinas.crt
```
