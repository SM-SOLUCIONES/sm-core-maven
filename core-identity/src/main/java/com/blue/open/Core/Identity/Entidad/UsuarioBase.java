package com.blue.open.Core.Identity.Entidad;

import com.blue.open.Core.Kernel.Proyeccion.NoProyectable;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Superclase base de los usuarios del sistema (mapped superclass): el proyecto la extiende con su
 * propia entidad @Entity sobre la tabla auth_usuarios y puede agregarle las columnas que necesite.
 * No declara ninguna relacion: como se vinculan usuario y roles depende del modulo elegido
 * (core-monoempresa: auth_usuarios_roles; core-multiempresa: auth_usuarios_empresas). El Core accede a esa
 * relacion a traves del puerto ResolutorDePermisos, nunca navegando desde esta clase.
 */
@Setter
@Getter
@MappedSuperclass
public abstract class UsuarioBase extends EntidadConBorradoLogico {

    @Column(unique = true)
    private String nombreDeUsuario;
    @NoProyectable
    private String contrasena;
    // UUID del usuario en el sistema de SSO externo (login federado, ver core-protocolo-sso)
    private String ssoUuid;
    // Legajo del usuario en el sistema Malvinas (integracion especifica de autenticacion)
    private String malvinasLegajo;

    private String nombre;
    private String nombreDeDepartamento;
    private String nombreDeGrupo;
    private String email;
    private String telefono;

    // Codigo de verificacion enviado por email (registro, cambio de contrasena, etc.)
    @NoProyectable
    private String codigoDeVerificacion;
    // Vencimiento del codigoDeVerificacion
    @NoProyectable
    private LocalDateTime vencimientoDelCodigoDeVerificacion;
    // Ultimo envio del codigoDeVerificacion, usado para el cooldown de reenvio
    @NoProyectable
    private LocalDateTime ultimoEnvioDelCodigoDeVerificacion;
}
