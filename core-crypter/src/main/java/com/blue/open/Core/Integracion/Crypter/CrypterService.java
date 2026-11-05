package com.blue.open.Core.Integracion.Crypter;

import com.blue.open.Core.Kernel.Puertos.IntegracionExterna;

import com.blue.open.Core.Integracion.Crypter.Excepciones.CodigoDeErrorCrypter;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Guarda y obtiene archivos privados (opcionalmente encriptados) en el webservice Crypter.
 * Dependencias externas: el webservice Crypter (crypter.url, crypter.usuario, crypter.contrasena,
 * crypter.tipo.documento). Recibe: archivos en base64 o sus resourceKeys. Responde: el resourceKey
 * de un archivo guardado, o el contenido de los archivos pedidos.
 */
@Service
@Slf4j
public class CrypterService implements IntegracionExterna {
    @Value("${crypter.url:#{null}}")
    private String url;

    @Value("${crypter.usuario:#{null}}")
    private String usuario;

    @Value("${crypter.contrasena:#{null}}")
    private String contrasena;

    @Value("${crypter.tipo.documento:#{null}}")
    private String tipoDocumento;

    private void validarConfiguracion() {
        if (estaVacio(url) || estaVacio(usuario) || estaVacio(contrasena) || estaVacio(tipoDocumento)) {
            log.error(
                    "Configuracion incompleta para el servicio Crypter. crypter.url={}, crypter.usuario={}, crypter.contrasena={}, crypter.tipo.documento={}",
                    valorONull(url),
                    valorONull(usuario),
                    enmascararSecreto(contrasena),
                    valorONull(tipoDocumento)
            );
            throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Faltan configuraciones necesarias para el servicio S3", "No existe crypter.url, crypter.usuario, crypter.contrasena o crypter.tipo.documento en el application.properties");
        }
    }

    // Usado por el smoke test de arranque para saber si la integracion esta configurada, sin lanzar excepcion
    @Override
    public String getNombreIntegracion() {
        return "Crypter";
    }

    @Override
    public boolean estaConfigurada() {
        // No usa validarConfiguracion(): esa loguea ERROR, y en el arranque la falta de configuracion la reporta el smoke test como WARN
        return !(estaVacio(url) || estaVacio(usuario) || estaVacio(contrasena) || estaVacio(tipoDocumento));
    }

    private boolean estaVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    private String valorONull(String valor) {
        return estaVacio(valor) ? null : valor;
    }

    private String enmascararSecreto(String valor) {
        return estaVacio(valor) ? null : "********";
    }

    private HttpHeaders getEncabezados() {
        try {
            HttpHeaders encabezados = new HttpHeaders();
            encabezados.set("Content-Type", "application/json");
            String auth = usuario + ":" + contrasena;
            byte[] authCodificado = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String encabezadoAuth = "Basic " + new String(authCodificado);
            encabezados.set("Authorization", encabezadoAuth);
            return encabezados;
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error al intentar obtener o gurdar el archivo", "CrypterService | getEncabezados | error: " + e.getMessage());
        }
    }

    // Obtiene una lista de archivos, buscando por sus resourceKeys
    public List<String> getArchivos(List<String> resourceKeys) {
        validarConfiguracion();
        try {
            RestTemplate restTemplate = new RestTemplate();
            List<String> respuesta = new ArrayList<>();

            // Construir el cuerpo de la solicitud
            List<Map<String, String>> cuerposDelRequest = new ArrayList<>();
            for (String resourceKey : resourceKeys) {
                Map<String, String> elemento = new HashMap<>();
                elemento.put("resourceKey", resourceKey);
                cuerposDelRequest.add(elemento);
            }

            // Convertir el cuerpo a JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String cuerpoDelRequest;
            try {
                cuerpoDelRequest = objectMapper.writeValueAsString(cuerposDelRequest);
            } catch (JacksonException e) {
                throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error al construir el cuerpo de la solicitud",
                        "CrypterService | getArchivos | error: " + e.getMessage());
            }

            if (!cuerpoDelRequest.isEmpty()) {
                // Crear entidad con cuerpo y encabezados
                HttpEntity<String> entity = new HttpEntity<>(cuerpoDelRequest, this.getEncabezados());

                // Hacer la solicitud POST
                ResponseEntity<List<ImagenCrypterDTO>> response;
                try {
                    response = restTemplate.exchange(this.url + "/document/getFiles", HttpMethod.POST, entity, new ParameterizedTypeReference<List<ImagenCrypterDTO>>() {
                    });
                } catch (RuntimeException e) {
                    System.out.println("Error en la respuesta del servidor de imagenes: " + e.getMessage());
                    throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de imagenes", e.getMessage());
                }

                List<ImagenCrypterDTO> cuerpo = response.getBody();
                for (ImagenCrypterDTO dto: cuerpo) {
                    if (dto == null) {
                        throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de archivos", "Body es null | CrypterService | getArchivos");
                    }

                    if (dto.getDest().isEmpty()) {
                        throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de archivos", "Falta dest | CrypterService | guardarArchivoEnCrypter | body : " + dto.getComoJson());
                    }

                    respuesta.add(dto.getDest());
                }
            }

            return respuesta;
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error al intentar obtener el archivo", "CrypterService | getArchivos | error: " + e.getMessage());
        }
    }

    // Obtiene un unico archivo buscando por su resourceKey
    public String getArchivo(String resourceKey) {
        try {
            return this.getArchivos(Collections.singletonList(resourceKey)).get(0);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error al intentar obtener el archivo", "CrypterService | getArchivo | error: " + e.getMessage());
        }
    }


    // Guarda un archivo de manera segura y encriptada, recibiendo unicamente el base64 del archivo
    public String guardarArchivo(String base64) {
        return guardarArchivoEnCrypter(base64, true, null, null, null);
    }

    // Espera como paraemtro si se debe o no encriptar. El archivo seguira siendo privado
    // Util para informacion no confidencial o previews difuminadas
    // Los archivos no encriptadas son mas ligeros y rapidos de consultar
    public String guardarArchivo(String base64, boolean encriptar) {
        return guardarArchivoEnCrypter(base64, encriptar, null, null, null);
    }

    // El nombre de la carpeta se usa para debug
    // Puede ser el uuid, numero de un expediente, numero de una caja, dni de un paciente, numero de una causa, etc.
    // Las carpetas no deberian contener mas de 2.000 archivos en la misma carpeta, mas o menos
    public String guardarArchivo(String base64, boolean encriptar, String nombreCarpeta) {
        return guardarArchivoEnCrypter(base64, encriptar, nombreCarpeta, null, null);
    }

    // La coleccion se usa para debug
    // Son similares a las carpetas, pero para grupos mas grandes
    // Dentro de una colleccion existen muchas carpetas
    // Una coleccion puede ser el anio de emision de un expediente, el area u oficina de donde proviene, etc.
    public String guardarArchivo(String base64, boolean encriptar, String nombreCarpeta, String nombreColeccion) {
        return guardarArchivoEnCrypter(base64, encriptar, nombreCarpeta, nombreColeccion, null);
    }

    // El nombre del archivo se usa para debug
    // El nombre del archivo es el nombre que tenia el archivo original
    // Ej. Imagen.png, cargaDeDatos.pdf, etc.
    public String guardarArchivo(String base64, boolean encriptar, String nombreCarpeta, String nombreColeccion, String nombreArchivo) {
        return guardarArchivoEnCrypter(base64, encriptar, nombreCarpeta, nombreColeccion, nombreArchivo);
    }

    // Recibe un archivo en base64, lo sube al webservice crypter y responde un resourceKey, con el cual luego se puede obtener
    private String guardarArchivoEnCrypter(String base64, boolean encriptar, String nombreCarpeta, String nombreColeccion, String nombreArchivo) {
        validarConfiguracion();
        try {
            RestTemplate restTemplate = new RestTemplate();
            LocalDate hoy = LocalDate.now();

            if (nombreCarpeta == null || nombreCarpeta.isEmpty()) {
                nombreCarpeta = hoy.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }

            if (nombreColeccion == null || nombreColeccion.isEmpty()) {
                nombreColeccion = hoy.format(DateTimeFormatter.ofPattern("yyyy"));
            }

            if (nombreArchivo == null || nombreArchivo.isEmpty()) {
                nombreArchivo = "";
            }

            // Crear el cuerpo de la solicitud
            Map<String, Object> cuerpoDelRequest = new HashMap<>();
            if (encriptar) {
                cuerpoDelRequest.put("crypt", 1);
            } else {
                cuerpoDelRequest.put("crypt", 0);
            }
            cuerpoDelRequest.put("validation", 1);
            cuerpoDelRequest.put("parentKey", nombreCarpeta);
            cuerpoDelRequest.put("collection", nombreColeccion);
            cuerpoDelRequest.put("tipoDocumento", this.tipoDocumento);
            cuerpoDelRequest.put("name", nombreArchivo);
            cuerpoDelRequest.put("dataSource", base64);

            // Crear la entidad con cuerpo y encabezados
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(cuerpoDelRequest, this.getEncabezados());

            try {
                ResponseEntity<ImagenCrypterDTO> response = restTemplate.exchange(
                        this.url + "/document/putFile",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<>() {
                        }
                );

                ImagenCrypterDTO cuerpo = response.getBody();

                if (cuerpo == null) {
                    throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de archivos", "Body es null | CrypterService | guardarArchivoEnCrypter");
                }

                if (cuerpo.getResourceKey().isEmpty()) {
                    throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de archivos", "Falta resource key | CrypterService | guardarArchivoEnCrypter | body : " + cuerpo.getComoJson());
                }

                if (cuerpo.getDest().isEmpty()) {
                    throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de archivos", "Falta dest | CrypterService | guardarArchivoEnCrypter | body : " + cuerpo.getComoJson());
                }

                if (!cuerpo.getErrMessage().isEmpty()) {
                    throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de archivos", "Existe un mensaje de error | CrypterService | guardarArchivoEnCrypter | body : " + cuerpo.getComoJson());
                }

                if (cuerpo.getResult() != 1) {
                    throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error en la respuesta del servidor de archivos", "Result es distinto a 1 | CrypterService | guardarArchivoEnCrypter | body : " + cuerpo.getComoJson());
                }

                return cuerpo.getResourceKey();
            } catch (ExcepcionDeApi e) {
                throw e;
            } catch (Exception e) {
                throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error al llamar al servidor de archivos", "CrypterService | guardarArchivoEnCrypter | response error: " + e.getMessage());
            }
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE, "Error al intentar guardar la imagen", "CrypterService | guardarArchivoEnCrypter | error: " + e.getMessage());
        }
    }
}
