package com.blue.open.Core.Protocolo.Jwt;

import com.blue.open.Core.Identity.DTO.UsuarioYContrasenaDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Excepciones.CodigoDeErrorIdentity;
import com.blue.open.Core.Identity.Puertos.EstrategiaDeAutenticacion;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Identity.Service.CookiesDeSesion;
import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Protocolo.Jwt.Excepciones.CodigoDeErrorJwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Protocolo de autenticacion JWT: emite un access token corto y un refresh token persistido.
 * Se habilita agregando "jwt" a la propiedad auth.protocolos.
 * Dependencias externas: la base de datos (usuarios y auth_jwt_refresh_tokens).
 * Recibe: el header Authorization Bearer (o, sin el header, la cookie access_token), o usuario y contrasena en el login.
 * Responde: el usuario autenticado, o el par de tokens. Un Bearer cuyo alg no es HS256 (por ejemplo el ES256 de
 * Malvinas) no es de este protocolo: responde null y lo resuelve otra estrategia.
 */
@Service
@RequiredArgsConstructor
public class AuthJwtService implements EstrategiaDeAutenticacion {

    @Value("${jwt.secreto:defaultSecretKeyThatIsVeryLongAndSecureEnoughForHS256AlgorithmToWorkProperly1234567890}")
    private String secretoJwt;

    private final long vencimientoDelTokenMs = 300000; // 5 minutos
    private final long vencimientoDelRefreshTokenMs = 172800000; // 2 dias

    private final UsuarioRepositorio usuarioRepositorio;

    private final JwtRefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final CookiesDeSesion cookiesDeSesion;

    // Solo lee la cabecera del token (sin verificarla) para saber si el alg es el de este protocolo
    private static final ObjectMapper LECTOR_DE_CABECERA = new ObjectMapper();

    @Override
    public String getProtocolo() {
        return "jwt";
    }

    private Key getClaveDeFirma() {
        return Keys.hmacShaKeyFor(secretoJwt.getBytes(StandardCharsets.UTF_8));
    }

    // Emite el par de tokens del usuario: borra sus refresh tokens anteriores, persiste uno nuevo y firma un access
    // token. Dependencias externas: la base de datos. Recibe el usuario autenticado. Responde token y refreshToken
    @Transactional
    public JwtTokenDTO generarToken(UsuarioBase usuario) {
        // Invalida los refresh tokens previos del usuario
        refreshTokenRepository.deleteByIdUsuario(usuario.getId());

        JwtRefreshToken refreshToken = new JwtRefreshToken();
        refreshToken.setIdUsuario(usuario.getId());
        refreshToken.setFechaDeVencimiento(FechaHoraConfig.ahoraLocalDateTime().plusNanos(vencimientoDelRefreshTokenMs * 1_000_000));
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken = refreshTokenRepository.save(refreshToken);

        JwtTokenDTO jwtTokenDTO = new JwtTokenDTO();
        jwtTokenDTO.setToken(crearAccessToken(usuario.getUuid()));
        jwtTokenDTO.setRefreshToken(refreshToken.getToken());

        return jwtTokenDTO;
    }

    private String crearAccessToken(String uuidUsuario) {
        Date ahora = FechaHoraConfig.ahoraDate();
        Date vencimientoDelToken = new Date(ahora.getTime() + vencimientoDelTokenMs);
        return Jwts.builder()
                .setSubject(uuidUsuario)
                .claim("type", "access")
                .setIssuedAt(ahora)
                .setExpiration(vencimientoDelToken)
                .signWith(getClaveDeFirma(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Canjea un refresh token vigente por un par nuevo (el usado se borra). Dependencias externas: la base de datos.
    // Recibe el refresh token. Responde token y refreshToken. Lanza REFRESH_TOKEN_INVALIDO (token inexistente, o
    // usuario inexistente o que no esta en status 0) o REFRESH_TOKEN_VENCIDO
    @Transactional
    public JwtTokenDTO renovarToken(String refreshTokenRecibido) {
        JwtRefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenRecibido)
                .orElseThrow(
                        () -> new ExcepcionDeApi(CodigoDeErrorJwt.REFRESH_TOKEN_INVALIDO,
                                "AuthJwtService | renovarToken | Token not found: " + refreshTokenRecibido));

        if (refreshToken.getFechaDeVencimiento().compareTo(FechaHoraConfig.ahoraLocalDateTime()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new ExcepcionDeApi(CodigoDeErrorJwt.REFRESH_TOKEN_VENCIDO,
                    "AuthJwtService | renovarToken | verifyExpiration | Token expired");
        }

        // Un usuario dado de baja (o inexistente) no puede renovar su sesion
        UsuarioBase usuario = usuarioRepositorio.porId(refreshToken.getIdUsuario())
                .filter(u -> u.getStatus() != null && u.getStatus() == 0)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorJwt.REFRESH_TOKEN_INVALIDO,
                        "AuthJwtService | renovarToken | Usuario del refresh token inexistente o inactivo: "
                                + refreshToken.getIdUsuario()));
        refreshTokenRepository.delete(refreshToken);

        JwtRefreshToken nuevoRefreshToken = new JwtRefreshToken();
        nuevoRefreshToken.setIdUsuario(usuario.getId());
        nuevoRefreshToken
                .setFechaDeVencimiento(FechaHoraConfig.ahoraLocalDateTime().plusNanos(vencimientoDelRefreshTokenMs * 1_000_000));
        nuevoRefreshToken.setToken(UUID.randomUUID().toString());
        nuevoRefreshToken = refreshTokenRepository.save(nuevoRefreshToken);

        JwtTokenDTO jwtTokenDTO = new JwtTokenDTO();
        jwtTokenDTO.setToken(crearAccessToken(usuario.getUuid()));
        jwtTokenDTO.setRefreshToken(nuevoRefreshToken.getToken());
        return jwtTokenDTO;
    }

    @Override
    public UsuarioBase getUsuario() {
        String token = getTokenDelRequest();
        if (token == null) {
            return null;
        }
        if (!esFormatoJwt(token)) {
            return null;
        }
        // Un token con otro alg (el ES256 de Malvinas) no es de este protocolo
        if (!esAlgoritmoPropio(token)) {
            return null;
        }
        try {
            return resolverUsuarioDelAccessToken(token, "AuthJwtService | getUsuario");
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (ExpiredJwtException e) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_VENCIDO,
                    "AuthJwtService | getUsuario | Token expired");
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_INVALIDO,
                    "AuthJwtService | getUsuario | error: " + e.getMessage());
        }
    }

    // Valida un access token recibido fuera del header Authorization (por ejemplo, en el handshake de WebSocket).
    // Dependencias externas: la base de datos. Recibe el token. Responde el usuario activo del token, o null si el alg
    // de la cabecera no es HS256 (no es un token de este protocolo). Lanza TOKEN_REQUERIDO, TOKEN_VENCIDO o TOKEN_INVALIDO
    public UsuarioBase getUsuarioDelToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_REQUERIDO,
                    "AuthJwtService | getUsuarioDelToken | Token vacío o nulo");
        }
        if (!esAlgoritmoPropio(token)) {
            return null;
        }
        try {
            return resolverUsuarioDelAccessToken(token, "AuthJwtService | getUsuarioDelToken");
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (ExpiredJwtException e) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_VENCIDO,
                    "AuthJwtService | getUsuarioDelToken | Token expired");
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_INVALIDO,
                    "AuthJwtService | getUsuarioDelToken | error: " + e.getMessage());
        }
    }

    private UsuarioBase resolverUsuarioDelAccessToken(String token, String contexto) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getClaveDeFirma())
                .build()
                .parseClaimsJws(token)
                .getBody();

        String tipo = claims.get("type", String.class);
        if (!"access".equals(tipo)) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_INVALIDO,
                    contexto + " | Tipo de token no permitido: " + tipo);
        }

        String uuid = claims.getSubject();
        if (uuid == null || uuid.isEmpty()) {
            throw new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_INVALIDO,
                    contexto + " | Subject ausente en el token");
        }

        return usuarioRepositorio.porUuid(uuid)
                .filter(usuario -> usuario.getStatus() != null && usuario.getStatus() == 0)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorJwt.TOKEN_INVALIDO,
                        "Usuario no encontrado o inactivo", contexto + " | Usuario no encontrado para uuid: " + uuid));
    }

    // Lee la cabecera del token sin verificar la firma y responde si su alg es HS256, el unico con el que este
    // protocolo firma. Una cabecera ilegible responde false: no es un token de este protocolo
    private boolean esAlgoritmoPropio(String token) {
        try {
            int finDeLaCabecera = token.indexOf('.');
            if (finDeLaCabecera <= 0) {
                return false;
            }
            JsonNode cabecera = LECTOR_DE_CABECERA.readTree(Decoders.BASE64URL.decode(token.substring(0, finDeLaCabecera)));
            JsonNode alg = cabecera.get("alg");
            return alg != null && SignatureAlgorithm.HS256.getValue().equals(alg.asString());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean esFormatoJwt(String token) {
        return token.split("\\.", -1).length == 3;
    }

    // Lee el access token del header Authorization Bearer; sin ese header, de la cookie access_token
    private String getTokenDelRequest() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes();
        if (requestAttributes != null) {
            HttpServletRequest request = requestAttributes.getRequest();
            String bearerToken = request.getHeader("Authorization");

            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        }
        return cookiesDeSesion.accessToken().orElse(null);
    }

    // Vigencia del access token, que es tambien la de su cookie
    public Duration getVigenciaDelToken() {
        return Duration.ofMillis(vencimientoDelTokenMs);
    }

    // Vigencia del refresh token, que es tambien la de su cookie
    public Duration getVigenciaDelRefreshToken() {
        return Duration.ofMillis(vencimientoDelRefreshTokenMs);
    }

    // Cierra la sesion: borra el refresh token recibido. Dependencias externas: la base de datos. Recibe el refresh
    // token; si no existe no hace nada (idempotente)
    @Transactional
    public void cerrarSesion(String refreshTokenRecibido) {
        refreshTokenRepository.findByToken(refreshTokenRecibido).ifPresent(refreshTokenRepository::delete);
    }

    // Valida usuario y contrasena del login. Dependencias externas: la base de datos. Recibe el nombre de usuario y la
    // contrasena. Responde el usuario activo. Lanza CREDENCIALES_INVALIDAS sin distinguir usuario inexistente de
    // contrasena incorrecta
    public UsuarioBase autenticarConContrasena(UsuarioYContrasenaDTO cuerpo) {
        UsuarioBase usuario = usuarioRepositorio.porNombreDeUsuarioActivo(cuerpo.getNombreDeUsuario())
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorIdentity.CREDENCIALES_INVALIDAS,
                        "AuthJwtService | autenticarConContrasena | User not found: " + cuerpo.getNombreDeUsuario()));

        if (!passwordEncoder.matches(cuerpo.getContrasena(), usuario.getContrasena())) {
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.CREDENCIALES_INVALIDAS,
                    "AuthJwtService | autenticarConContrasena | Password mismatch for user: " + cuerpo.getNombreDeUsuario());
        }

        return usuario;
    }
}
