package com.blue.open.Core.Protocolo.Sso;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.EstrategiaDeAutenticacion;
import com.blue.open.Core.Identity.Puertos.PoliticaDeAccesoExterno;
import com.blue.open.Core.Identity.Puertos.SincronizadorDeRolesExternos;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Identity.Service.CookiesDeSesion;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Protocolo.Sso.Excepciones.CodigoDeErrorSso;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static com.blue.open.Core.Kernel.Util.Utils.esTextoValido;

/**
 * Protocolo de autenticacion contra el IDP de Malvinas Argentinas (JWT firmado externamente).
 * Se habilita agregando "malvinas" a auth.protocolos y configurando auth.malvinas.url.
 * Dependencias externas: el JWKS del IDP de Malvinas y la base de datos.
 * Recibe: el header Authorization Bearer con el JWT del IDP o, sin el header, la cookie access_token.
 * Responde: el usuario local sincronizado a partir de los claims, o null si no aplica (sin Bearer ni cookie, o un
 * token cuyo alg no es ES256, como el JWT HS256 de core-protocolo-jwt).
 * Antes de crear o guardar el usuario consulta el puerto PoliticaDeAccesoExterno.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthMalvinasService implements EstrategiaDeAutenticacion {

    // Nombre del contexto externo con el que se sincronizan los roles que declara Malvinas
    private static final String CONTEXTO_EXTERNO = "malvinas";

    private final UsuarioRepositorio usuarioRepositorio;

    private final SincronizadorDeRolesExternos sincronizadorDeRolesExternos;

    // Decide si el legajo puede entrar; por defecto (core-identity) no valida nada
    private final PoliticaDeAccesoExterno politicaDeAccesoExterno;

    // Opcional: sin auth.malvinas.url no se registra el JwtDecoder y este protocolo queda inactivo
    private final ObjectProvider<JwtDecoder> jwtDecoder;

    private final CookiesDeSesion cookiesDeSesion;

    @Value("${auth.malvinas.url:#{null}}")
    private String urlDeMalvinas;

    @Override
    public String getProtocolo() {
        return "malvinas";
    }

    @Override
    public UsuarioBase getUsuario() {
        try {
            if (urlDeMalvinas == null) {
                throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_NO_DISPONIBLE,
                        "No existe auth.malvinas.url en el application.properties");
            }

            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (requestAttributes == null) {
                return null;
            }

            HttpServletRequest request = requestAttributes.getRequest();
            String bearer = request.getHeader("Authorization");
            if (bearer == null) {
                // Sin header Authorization se usa la cookie access_token
                bearer = cookiesDeSesion.accessToken().orElse(null);
                if (bearer == null) {
                    return null;
                }
            } else if (bearer.startsWith("Bearer ")) {
                bearer = bearer.replace("Bearer ", "");
            } else {
                return null;
            }
            if (!esTextoValido(bearer) || !esFormatoJwt(bearer)) {
                return null;
            }
            // Solo los tokens ES256 son de Malvinas: otro alg (el HS256 del JWT propio) lo resuelve otra estrategia
            if (!esAlgoritmoDeMalvinas(bearer)) {
                return null;
            }

            JwtDecoder decoder = jwtDecoder.getIfAvailable();
            if (decoder == null) {
                throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_NO_DISPONIBLE,
                        "No hay JwtDecoder configurado: falta auth.malvinas.url");
            }

            Jwt jwt = decoder.decode(bearer);
            if (!esTextoValido(jwt.getClaimAsString("urn:malvinas:legajo"))) {
                log.error("AuthMalvinasService | getUsuario | Se pudo obtener al usuario, pero el mismo no tiene legajo");
                throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_INTEGRACION_ERROR,
                        "AuthMalvinasService | getUsuario | Se pudo obtener al usuario, pero el mismo no tiene legajo");
            }

            String legajo = jwt.getClaimAsString("urn:malvinas:legajo");
            Optional<UsuarioBase> usuarioVigente = usuarioRepositorio.porMalvinasLegajoActivo(legajo);
            // La politica decide antes de crear o guardar: si rechaza, su ExcepcionDeApi sale tal cual.
            // orElse(null): el puerto recibe null cuando el legajo todavia no tiene usuario local
            politicaDeAccesoExterno.validar(getProtocolo(), legajo, usuarioVigente.orElse(null));
            // Si el legajo todavia no tiene usuario local, se crea uno en el momento
            UsuarioBase usuario = usuarioVigente
                    .orElseGet(() -> {
                        UsuarioBase nuevo = usuarioRepositorio.nuevo();
                        nuevo.setMalvinasLegajo(legajo);
                        return nuevo;
                    });

            if (!esTextoValido(usuario.getNombreDeUsuario())) {
                usuario.setNombreDeUsuario(legajo);
            }
            if (esTextoValido(jwt.getClaimAsString("name"))) {
                usuario.setNombre(jwt.getClaimAsString("name"));
            }
            if (esTextoValido(jwt.getClaimAsString("email"))) {
                usuario.setEmail(jwt.getClaimAsString("email"));
            }
            if (esTextoValido(jwt.getClaimAsString("phone_number"))) {
                usuario.setTelefono(jwt.getClaimAsString("phone_number"));
            }
            if (esTextoValido(jwt.getClaimAsString("urn:malvinas:dep:name"))) {
                usuario.setNombreDeDepartamento(jwt.getClaimAsString("urn:malvinas:dep:name"));
            }
            if (esTextoValido(jwt.getClaimAsString("urn:malvinas:grp:name"))) {
                usuario.setNombreDeGrupo(jwt.getClaimAsString("urn:malvinas:grp:name"));
            }

            usuario = usuarioRepositorio.guardar(usuario);

            sincronizadorDeRolesExternos.sincronizar(usuario, CONTEXTO_EXTERNO, jwt.getClaimAsStringList("role"));

            return usuario;
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorSso.SSO_AUTENTICACION_FALLIDA,
                    "Token de autenticación Malvinas inválido",
                    "AuthMalvinasService | getUsuario | error: " + e.getMessage());
        }
    }

    // Lee la cabecera del token sin verificar la firma y responde si su alg es ES256, el unico que firma Malvinas
    // (ver MalvinasJwtDecoderConfig). Una cabecera ilegible responde false: no es un token de Malvinas
    private boolean esAlgoritmoDeMalvinas(String token) {
        try {
            JWSHeader cabecera = JWSHeader.parse(new Base64URL(token.substring(0, token.indexOf('.'))));
            return JWSAlgorithm.ES256.equals(cabecera.getAlgorithm());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean esFormatoJwt(String token) {
        return token.split("\\.", -1).length == 3;
    }
}
