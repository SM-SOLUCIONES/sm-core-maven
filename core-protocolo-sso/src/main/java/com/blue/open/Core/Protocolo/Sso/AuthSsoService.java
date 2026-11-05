package com.blue.open.Core.Protocolo.Sso;

import com.blue.open.Core.Kernel.Puertos.IntegracionExterna;

// Ultimo cambio:
// Autor: Taiel Martinez
// Fecha: 17/11/2025
// Descripcion: Se simplifica el archivo para integrarlo en la validacion por authService unificado

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.EstrategiaDeAutenticacion;
import com.blue.open.Core.Identity.Puertos.SincronizadorDeRolesExternos;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Protocolo.Sso.Excepciones.CodigoDeErrorSso;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

import static com.blue.open.Core.Kernel.Util.Utils.esTextoValido;

/**
 * Estrategia de autenticacion "sso": identifica al usuario con el Bearer de la request consultando al SSO
 * y lo crea o actualiza localmente, sincronizando los roles que declara el SSO.
 * Dependencias externas: el SSO (auth.sso.url, auth.sso.clientId) y la base de datos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSsoService implements EstrategiaDeAutenticacion, IntegracionExterna {

    // Nombre del contexto externo con el que se sincronizan los roles que declara el SSO
    private static final String CONTEXTO_EXTERNO = "sso";

    @Override
    public String getProtocolo() {
        return "sso";
    }


    private final UsuarioRepositorio usuarioRepositorio;

    private final SincronizadorDeRolesExternos sincronizadorDeRolesExternos;

    @Value("${auth.sso.url:#{null}}")
    private String urlDelSso;

    @Value("${auth.sso.clientId:#{null}}")
    private String clientIdDelSso;

    private void validarConfiguracion() {
        if (urlDelSso == null || clientIdDelSso == null) {
            throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_NO_DISPONIBLE,
                    "No existe auth.sso.url o auth.sso.clientId en el application.properties");
        }
    }

    // Usado por el smoke test de arranque para saber si la integracion esta configurada, sin lanzar excepcion
    @Override
    public String getNombreIntegracion() {
        return "SSO (auth.sso.*)";
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

    // Obtiene el usuario actualmente conectado usando desde el servicio de SSO
    @Override
    public UsuarioBase getUsuario() {
        try {
            validarConfiguracion();

            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                String bearer = request.getHeader("Authorization");
                if (bearer == null || !bearer.startsWith("Bearer ")) {
                    return null;
                }
                bearer = bearer.replace("Bearer ", "");
                UsuarioSSO usuarioSSO = this.obtenerUsuariosAplicacion(bearer).getBody();
                if (usuarioSSO == null) {
                    throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_AUTENTICACION_FALLIDA,
                            "AuthSsoService | getUsuario | SSO no devolvió usuario para el token recibido");
                }
                return usuarioDesdeSso(usuarioSSO);
            }
            return null;
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_INTEGRACION_ERROR,
                    "AuthSsoService | getUsuario | error: " + e.getMessage());
        }
    }

    // Obitiene un usuario segun un token. No necesesita ser el actualmente
    // conectado
    private ResponseEntity<UsuarioSSO> obtenerUsuariosAplicacion(String accessToken) {
        try {
            validarConfiguracion();

            String url = this.urlDelSso + "/usuarios/aplicacion";

            HttpHeaders encabezados = new HttpHeaders();
            encabezados.setContentType(MediaType.APPLICATION_JSON);
            encabezados.set("Authorization", "Bearer " + accessToken);

            Map<String, String> cuerpoDelRequest = new HashMap<>();
            cuerpoDelRequest.put("clientId", this.clientIdDelSso);
            cuerpoDelRequest.put("accessToken", accessToken);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(cuerpoDelRequest, encabezados);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<UsuarioSSO> response;
            try {
                response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        requestEntity,
                        UsuarioSSO.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    return response;
                } else {
                    throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_INTEGRACION_ERROR,
                            "AuthSsoService | obtenerUsuariosAplicacion | code: " + response.getStatusCode()
                                    + " | body: " + response.getBody());
                }
            } catch (ExcepcionDeApi e) {
                throw e;
            } catch (HttpClientErrorException ex) {
                System.out.println("Error en la llamada al SSO " + ex.getStatusCode() + ex.getResponseBodyAsString());
                throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_INTEGRACION_ERROR,
                        "AuthSsoService | obtenerUsuariosAplicacion | code: " + ex.getStatusCode() + " | body: "
                                + ex.getResponseBodyAsString() + " message: " + ex.getMessage());
            } catch (Exception ex) {
                System.out.println("Error al obtener la informacion del SSO " + ex.getMessage());
                throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_INTEGRACION_ERROR,
                        "AuthSsoService | obtenerUsuariosAplicacion | error: " + ex.getMessage());
            }
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_INTEGRACION_ERROR,
                    "AuthSsoService | obtenerUsuariosAplicacion | error: " + e.getMessage());
        }
    }

    // Busca el usuario local por el uuid del SSO (o crea uno nuevo), le copia nombre de usuario, email y
    // telefono, lo guarda y sincroniza sus roles con los del SSO. Dependencias externas: la base de datos.
    // Recibe: el usuario que respondio el SSO. Responde: el usuario local guardado.
    public UsuarioBase usuarioDesdeSso(UsuarioSSO usuarioSSO) {
        // Si el uuid del SSO todavia no tiene usuario local, se crea uno en el momento
        UsuarioBase usuario = usuarioRepositorio.porSsoUuidActivo(usuarioSSO.getUuid())
                .orElseGet(() -> {
                    UsuarioBase nuevo = usuarioRepositorio.nuevo();
                    if (esTextoValido(usuarioSSO.getUuid()))
                        nuevo.setSsoUuid(usuarioSSO.getUuid());
                    return nuevo;
                });

        if (esTextoValido(usuarioSSO.getUsername()))
            usuario.setNombreDeUsuario(usuarioSSO.getUsername());
        if (esTextoValido(usuarioSSO.getEmail()))
            usuario.setEmail(usuarioSSO.getEmail());
        if (esTextoValido(usuarioSSO.getTelefono()))
            usuario.setTelefono(usuarioSSO.getTelefono());

        usuario = usuarioRepositorio.guardar(usuario);

        // Los roles que declara el SSO se escriben segun el modulo de scoping del proyecto
        sincronizadorDeRolesExternos.sincronizar(usuario, CONTEXTO_EXTERNO, usuarioSSO.getRoles());

        return usuario;
    }

}
