package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Excepciones.CodigoDeErrorIdentity;
import com.blue.open.Core.Identity.Puertos.EstrategiaDeAutenticacion;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Protocolo de autenticacion HTTP Basic (usuario y contrasena en el header Authorization).
 * Se habilita agregando "basic" a la propiedad auth.protocolos.
 * Dependencias externas: la base de datos (usuarios).
 * Recibe: el header Authorization de la request.
 * Responde: el usuario autenticado, o null si la request no trae credenciales Basic.
 */
@Service
@RequiredArgsConstructor
public class AuthBasicService implements EstrategiaDeAutenticacion {

    private final UsuarioRepositorio usuarioRepositorio;

    private final PasswordEncoder passwordEncoder;

    @Override
    public String getProtocolo() {
        return "basic";
    }

    private String[] getAuth() {
        try {
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                String encabezadoAuth = request.getHeader("Authorization");

                if (encabezadoAuth != null && encabezadoAuth.startsWith("Basic ")) {
                    String credencialesBase64 = encabezadoAuth.substring("Basic ".length());
                    byte[] bytesDecodificados = Base64.getDecoder().decode(credencialesBase64);
                    String decodificado = new String(bytesDecodificados, StandardCharsets.UTF_8);

                    // Divide en nombre de usuario y contrasena
                    return decodificado.split(":", 2);
                }
            }
            return new String[0];
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "getAuth: " + e.getMessage());
        }
    }

    @Override
    public UsuarioBase getUsuario() {
        try {
            if (!tieneEncabezadoBasic()) {
                return null;
            }

            String[] auth = this.getAuth();
            if (auth.length < 2) {
                throw new ExcepcionDeApi(CodigoDeErrorIdentity.CREDENCIALES_INVALIDAS,
                        "AuthBasicService | getUsuario | Formato de credenciales incorrecto");
            }

            UsuarioBase usuario = usuarioRepositorio.porNombreDeUsuarioActivo(auth[0])
                    .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorIdentity.CREDENCIALES_INVALIDAS,
                            "AuthBasicService | getUsuario | Usuario no encontrado: " + auth[0]));

            if (!passwordEncoder.matches(auth[1], usuario.getContrasena())) {
                throw new ExcepcionDeApi(CodigoDeErrorIdentity.CREDENCIALES_INVALIDAS,
                        "AuthBasicService | getUsuario | Contraseña incorrecta para usuario: " + auth[0]);
            }

            return usuario;
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "getUsuario: " + e.getMessage());
        }
    }

    private boolean tieneEncabezadoBasic() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes();
        if (requestAttributes == null) {
            return false;
        }
        String encabezadoAuth = requestAttributes.getRequest().getHeader("Authorization");
        return encabezadoAuth != null && encabezadoAuth.startsWith("Basic ");
    }
}
