package com.blue.open.Core.Protocolo.ApiKey;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.EstrategiaDeAutenticacion;
import com.blue.open.Core.Crud.Excepciones.CodigoDeErrorCrud;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Protocolo.ApiKey.Excepciones.CodigoDeErrorApiKey;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.List;

import static com.blue.open.Core.Kernel.Util.Utils.esTextoValido;

/**
 * Protocolo de autenticacion por token de acceso personal (API key).
 * Se habilita agregando "tokens" a la propiedad auth.protocolos.
 * Dependencias externas: la base de datos (auth_access_tokens).
 * Recibe: el header Authorization Bearer con un token opaco (no JWT).
 * Responde: el usuario duenio del token, o null si la request no trae uno de este tipo.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenService implements EstrategiaDeAutenticacion {

    private final AccessTokenRepository repository;

    private final UsuarioRepositorio usuarioRepositorio;

    private static final String CARACTERES = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Override
    public String getProtocolo() {
        return "tokens";
    }

    @Override
    public UsuarioBase getUsuario() {
        try {
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (requestAttributes == null) {
                return null;
            }

            HttpServletRequest request = requestAttributes.getRequest();
            String bearer = request.getHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")) {
                return null;
            }

            bearer = bearer.replace("Bearer ", "");
            if (!esTextoValido(bearer)) {
                return null;
            }

            if (esFormatoJwt(bearer)) {
                return null;
            }

            // Un token eliminado (status=-1) o desactivado (activo=0) ya no autentica
            AccessToken modelo = repository.findFirstByCodigo(bearer)
                    .filter(t -> t.getIdUsuario() != null && t.getStatus() == 0 && t.getActivo() == 1)
                    .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorApiKey.ACCESS_TOKEN_INVALIDO,
                            "AuthTokenService | getUsuario | Token no encontrado o sin usuario asociado"));

            // orElse(null): el contrato de EstrategiaDeAutenticacion.getUsuario() es devolver null si no resuelve
            return usuarioRepositorio.porId(modelo.getIdUsuario()).orElse(null);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "AuthTokenService | getUsuario | error: " + e.getMessage());
        }
    }

    private boolean esFormatoJwt(String token) {
        return token.split("\\.", -1).length == 3;
    }

    /**
     * Crea un nuevo token de acceso de 128 caracteres para un usuario.
     * Los tokens son permanentes hasta que se desactiven (activo=0) o se eliminen (status=-1).
     */
    public AccessToken crearAccessToken(UsuarioBase usuario, String nombre) {
        try {
            if (repository.findFirstByNombreAndIdUsuarioAndStatus(nombre, usuario.getId(), 0).isPresent()) {
                throw new ExcepcionDeApi(CodigoDeErrorApiKey.TOKEN_NOMBRE_DUPLICADO,
                        "Ya existe un token con el nombre: " + nombre,
                        "Ya existe un token con el nombre: " + nombre);
            }

            AccessToken token = new AccessToken();
            token.setIdUsuario(usuario.getId());
            token.setNombre(nombre);
            token.setCodigo(generarTokenAleatorio(128));
            token.setActivo(1);
            return repository.save(token);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "AuthTokenService | crearAccessToken | error: " + e.getMessage());
        }
    }

    private String generarTokenAleatorio(int largo) {
        StringBuilder sb = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) {
            sb.append(CARACTERES.charAt(ALEATORIO.nextInt(CARACTERES.length())));
        }
        return sb.toString();
    }

    /**
     * Borrado logico de un token, validando que pertenezca al usuario indicado.
     */
    public AccessToken borrarAccessToken(UsuarioBase usuario, String uuid) {
        try {
            AccessToken token = repository.buscarVigentePorUuid(uuid)
                    .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO));

            if (token.getIdUsuario() == null || !token.getIdUsuario().equals(usuario.getId())) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                        "No tienes permisos para eliminar este token");
            }

            token.setStatus(-1);
            token.setActivo(0);
            token.setFechaDeBorrado(FechaHoraConfig.ahoraTimestamp());

            return repository.save(token);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "AuthTokenService | borrarAccessToken | error: " + e.getMessage());
        }
    }

    // Recibe: el usuario. Responde: sus tokens de acceso activos (status 0) como entidades
    public List<AccessToken> getTokensDelUsuario(UsuarioBase usuario) {
        return repository.findAllByIdUsuarioAndStatus(usuario.getId(), 0);
    }

    /**
     * Respuesta de crear o eliminar un token: su Proyeccion (incluido el codigo) con el uuid del usuario duenio.
     * Dependencias externas: la base de datos.
     * Recibe: el usuario duenio y el uuid del token. Responde: el AccessTokenDTO, o REGISTRO_NO_ENCONTRADO.
     */
    public AccessTokenDTO proyectar(UsuarioBase usuario, String uuid) {
        AccessTokenDTO dto = repository.buscarDtoPorUuid(uuid)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                        "AuthTokenService | proyectar | no se encontro el token " + uuid));
        dto.setUsuarioUuid(usuario.getUuid());
        return dto;
    }

    /**
     * Tokens vigentes del usuario, proyectados, con su uuid como duenio.
     * Dependencias externas: la base de datos.
     * Recibe: el usuario. Responde: sus AccessTokenDTO por id ascendente (lista vacia si no tiene).
     */
    public List<AccessTokenDTO> proyectarVigentes(UsuarioBase usuario) {
        List<AccessTokenDTO> tokens = repository.listarDtosVigentesPorUsuario(usuario.getId());
        tokens.forEach(dto -> dto.setUsuarioUuid(usuario.getUuid()));
        return tokens;
    }
}
