package com.blue.open.Core.Integracion.Captcha;

import com.blue.open.Core.Kernel.Puertos.IntegracionExterna;

import com.blue.open.Core.Integracion.Captcha.Excepciones.CodigoDeErrorCaptcha;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifica tokens de hCaptcha contra https://hcaptcha.com/siteverify.
 * Dependencias externas: la API de hCaptcha. Se configura con captcha.secreto y captcha.habilitado.
 * Recibe: el token que genera el widget en el front. Responde: nada si es valido, o ExcepcionDeApi.
 */
@Service
@Slf4j
public class CaptchaService implements IntegracionExterna {

    private final RestTemplate restTemplate;

    @Value("${captcha.secreto:#{null}}")
    private String claveSecreta;

    @Value("${captcha.habilitado:true}")
    private boolean habilitado;

    // Arma el RestTemplate con el que se llama a hCaptcha
    public CaptchaService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    private void validarConfiguracion() {
        if (claveSecreta == null || claveSecreta.isBlank()) {
            throw registrarYCrearExcepcion(CodigoDeErrorCaptcha.CAPTCHA_NO_DISPONIBLE, "No existe captcha.secreto en el application.properties");
        }
    }

    // Usado por el smoke test de arranque para saber si la integracion esta configurada, sin lanzar excepcion
    @Override
    public String getNombreIntegracion() {
        return "Captcha";
    }

    @Override
    public boolean estaConfigurada() {
        if (!habilitado) {
            return true;
        }
        try {
            validarConfiguracion();
            return true;
        } catch (ExcepcionDeApi e) {
            return false;
        }
    }

    private ExcepcionDeApi registrarYCrearExcepcion(CodigoDeErrorCaptcha codigo) {
        log.error(codigo.mensajePorDefecto());
        return new ExcepcionDeApi(codigo);
    }

    private ExcepcionDeApi registrarYCrearExcepcion(CodigoDeErrorCaptcha codigo, String mensajeParaDesarrollador) {
        log.error("{} | {}", codigo.mensajePorDefecto(), mensajeParaDesarrollador);
        return new ExcepcionDeApi(codigo, mensajeParaDesarrollador);
    }

    // Verifica un token de hCaptcha. Si captcha.habilitado es false no hace nada.
    // Dependencias externas: la API de hCaptcha (POST siteverify con captcha.secreto).
    // Recibe: el token del widget. Responde: nada si es valido; lanza ExcepcionDeApi con
    // CAPTCHA_NO_DISPONIBLE (falta configuracion), CAPTCHA_NO_COMPLETADO (token vacio) o CAPTCHA_INVALIDO.
    public void verificar(String token) {
        if (!habilitado) {
            return;
        }

        validarConfiguracion();

        if (token == null || token.isBlank()) {
            throw registrarYCrearExcepcion(CodigoDeErrorCaptcha.CAPTCHA_NO_COMPLETADO);
        }

        String url = "https://hcaptcha.com/siteverify";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("secret", claveSecreta);
        map.add("response", token);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> cuerpo = response.getBody();

            if (cuerpo == null || !Boolean.TRUE.equals(cuerpo.get("success"))) {
                throw registrarYCrearExcepcion(CodigoDeErrorCaptcha.CAPTCHA_INVALIDO);
            }
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw registrarYCrearExcepcion(CodigoDeErrorCaptcha.CAPTCHA_INVALIDO, "Error al verificar el captcha: " + e.getMessage());
        }
    }
}
