package com.blue.open.Core.Integracion.Sm3;

import com.blue.open.Core.Kernel.Puertos.IntegracionExterna;

import com.blue.open.Core.Integracion.Sm3.Excepciones.CodigoDeErrorSm3;
import com.blue.open.Core.Kernel.DTO.BorrarArchivoRequest;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Sube y borra archivos publicos (imagenes, etc.) en el servidor SM3.
 * Dependencias externas: el servidor SM3 (sm3.url, sm3.usuario, sm3.contrasena, sm3.proyecto).
 * Recibe: el archivo a subir o la url publica a borrar. Responde: la url publica o si se borro.
 */
@Service
public class SM3Service implements IntegracionExterna {
    @Value("${sm3.url:#{null}}")
    private String url;

    @Value("${sm3.usuario:#{null}}")
    private String usuario;

    @Value("${sm3.contrasena:#{null}}")
    private String contrasena;

    @Value("${sm3.proyecto:#{null}}")
    private String proyecto;

    private void validarConfiguracion() {
        if (url == null || usuario == null || contrasena == null || proyecto == null) {
            throw new ExcepcionDeApi(CodigoDeErrorSm3.SM3_NO_DISPONIBLE, "No existe sm3.url, sm3.usuario, sm3.contrasena o sm3.proyecto en el application.properties");
        }
    }

    // Usado por el smoke test de arranque para saber si la integracion esta configurada, sin lanzar excepcion
    @Override
    public String getNombreIntegracion() {
        return "SM3";
    }

    @Override
    public boolean estaConfigurada() {
        try {
            validarConfiguracion();
            return true;
        } catch (ExcepcionDeApi e) {
            return false;
        }
    }

    private HttpHeaders getEncabezados() {
        HttpHeaders encabezados = new HttpHeaders();
        String auth = usuario + ":" + contrasena;
        byte[] authCodificado = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String encabezadoAuth = "Basic " + new String(authCodificado);
        encabezados.set("Authorization", encabezadoAuth);
        return encabezados;
    }

    // Sube un archivo al servidor, espera un MultipartFile y reciba la url publica como respuesta
    public String subir(MultipartFile archivo) {
        this.validarConfiguracion();

        try {
            RestTemplate restTemplate = new RestTemplate();

            // headers (sin Content-Type)
            HttpHeaders encabezados = getEncabezados();

            // body multipart
            MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();

            ByteArrayResource recursoDelArchivo = new ByteArrayResource(archivo.getBytes()) {
                @Override
                public String getFilename() {
                    return archivo.getOriginalFilename();
                }
            };

            cuerpo.add("file", recursoDelArchivo);
            cuerpo.add("proyecto", proyecto);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(cuerpo, encabezados);

            ResponseEntity<String> response = restTemplate.exchange(
                    url + "/archivo",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            return response.getBody();
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorSm3.ERROR_SUBIENDO_ARCHIVO, "SM3Service | subir | " + e.getMessage());
        }
    }

    // Borra un achivo del servidor. Espera la url publica como paraemtro y responda un true cuando se borro satisfactoriamente
    public Boolean borrar(BorrarArchivoRequest request) {
        this.validarConfiguracion();

        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders encabezados = getEncabezados();
            encabezados.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<BorrarArchivoRequest> requestEntity = new HttpEntity<>(request, encabezados);

            ResponseEntity<Boolean> response = restTemplate.exchange(
                    url + "/archivo",
                    HttpMethod.DELETE,
                    requestEntity,
                    Boolean.class
            );

            return response.getBody();
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorSm3.ERROR_ELIMINANDO_ARCHIVO, "SM3Service | borrar | " + e.getMessage());
        }
    }
}
