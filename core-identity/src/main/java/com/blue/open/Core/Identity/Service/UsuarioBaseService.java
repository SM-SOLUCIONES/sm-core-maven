package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Crud.AbstractService;
import com.blue.open.Core.Identity.Auth.PermisoBaseEnum;
import com.blue.open.Core.Identity.DTO.UsuarioBaseDTO;
import com.blue.open.Core.Identity.DTO.CambiarContrasenaDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.CodigoDeSeguridad;
import com.blue.open.Core.Identity.Excepciones.CodigoDeErrorIdentity;
import com.blue.open.Core.Identity.Repository.CodigoDeSeguridadRepository;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import com.blue.open.Core.Crud.Excepciones.CodigoDeErrorCrud;
import com.blue.open.Core.Kernel.Configuracion.FechaHoraConfig;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Notificacion.NotificacionPorMail;
import com.blue.open.Core.Kernel.Puertos.EnviadorDeMail;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service base de usuarios: alta/edicion, encriptado de contrasena, verificacion por codigo y
 * recuperacion de contrasena. No contiene ninguna regla de empresas: todo lo multi-empresa
 * (asignacion de roles por empresa, registro publico con alta de empresa, filtro del paginado por
 * empresa) vive en core-multiempresa, que extiende esta clase.
 * Dependencias externas: la base de datos y, para los codigos, el EnviadorDeMail (core-mail).
 * Recibe: el DTO de usuario del proyecto. Responde: ese mismo DTO ya persistido.
 */
@Transactional
public class UsuarioBaseService<U extends UsuarioBase, DTO extends UsuarioBaseDTO> extends AbstractService<U, DTO, DTO> {

    // Valores persistidos en codigos_de_seguridad.proposito: quedan en ingles [D18]
    public static final String PROPOSITO_RECUPERAR_CONTRASENA = "FORGOT_PASSWORD";
    public static final String PROPOSITO_REGISTRO = "REGISTRATION";

    protected PasswordEncoder passwordEncoder;

    protected CodigoDeSeguridadRepository codigoDeSeguridadRepository;

    protected UsuarioRepositorio usuarioRepositorio;

    protected AuthService authService;

    // Opcional: sin core-mail no hay envio de codigos, y se avisa con un error claro
    protected ObjectProvider<EnviadorDeMail> enviadorDeMail;

    /*
     * Inyeccion por setter, no por campo ni por constructor: es una clase base que extienden
     * otros modulos del Core y los proyectos. Ver core/docs/rules/inyeccion-de-dependencias.md.
     */

    // Recibe el encoder con el que se hashean las contrasenas
    @Autowired
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    // Recibe el repositorio de codigos de seguridad (verificacion y recupero de contrasena)
    @Autowired
    public void setCodigoDeSeguridadRepository(CodigoDeSeguridadRepository codigoDeSeguridadRepository) {
        this.codigoDeSeguridadRepository = codigoDeSeguridadRepository;
    }

    // Recibe el puerto que resuelve la entidad de usuario del proyecto
    @Autowired
    public void setUsuarioRepositorio(UsuarioRepositorio usuarioRepositorio) {
        this.usuarioRepositorio = usuarioRepositorio;
    }

    // Recibe el service de autenticacion, que valida permisos del usuario actual
    @Autowired
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    // Recibe el enviador de mails. ObjectProvider porque core-mail es opcional
    @Autowired
    public void setEnviadorDeMail(ObjectProvider<EnviadorDeMail> enviadorDeMail) {
        this.enviadorDeMail = enviadorDeMail;
    }

    @Override
    public void hookGuardarAuth(DTO request) {
        boolean esAlta = (request.getUuid() == null);
        PermisoBaseEnum permisoRequerido = esAlta ? PermisoBaseEnum.USUARIO_CREAR : PermisoBaseEnum.USUARIO_EDITAR;
        // Alta y edicion comparten endpoint: el permiso depende del request, no se puede declarar en @Contrato
        if (!authService.tienePermisos(List.of(permisoRequerido))) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "No tienes permiso",
                    "UsuarioBaseService | hookGuardarAuth | falta " + permisoRequerido);
        }
    }

    /*
     * La contrasena es @NoProyectable: la copia generica nunca la escribe. Se escribe solo aca, hasheada, y solo si
     * vino en el DTO; si no vino, se conserva la actual.
     */
    @Override
    protected U hookGuardarAplicarDto(DTO dto, U modelo) {
        modelo = super.hookGuardarAplicarDto(dto, modelo);

        // Encripta la contrasena solo si vino en texto plano
        if (dto.getContrasena() != null && !dto.getContrasena().isEmpty() && !dto.getContrasena().startsWith("$2a$")) {
            modelo.setContrasena(passwordEncoder.encode(dto.getContrasena()));
        }

        return modelo;
    }

    /**
     * Valida el codigo de verificacion de un usuario dado de alta en estado pendiente (-2)
     * y lo pasa a activo (0).
     */
    public void verificarCodigo(String uuid, String codigo) {
        UsuarioBase usuario = usuarioRepositorio.porUuid(uuid)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                        "Usuario no encontrado", "UsuarioBaseService | verificarCodigo | no existe el usuario: " + uuid));

        if (usuario.getStatus() != -2) {
            if (usuario.getStatus() == 0)
                return; // Ya activo
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.USUARIO_NO_PENDIENTE_VERIFICACION,
                    "UsuarioBaseService | verificarCodigo | status no pendiente: " + usuario.getStatus());
        }

        if (usuario.getCodigoDeVerificacion() == null || !usuario.getCodigoDeVerificacion().equals(codigo)) {
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.CODIGO_INVALIDO, "UsuarioBaseService | verificarCodigo | el codigo no coincide");
        }

        if (usuario.getVencimientoDelCodigoDeVerificacion().isBefore(FechaHoraConfig.ahoraLocalDateTime())) {
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.CODIGO_VENCIDO, "UsuarioBaseService | verificarCodigo | el codigo esta vencido");
        }

        usuario.setStatus(0);
        usuario.setCodigoDeVerificacion(null);
        usuario.setVencimientoDelCodigoDeVerificacion(null);
        usuario.setUltimoEnvioDelCodigoDeVerificacion(null);

        usuarioRepositorio.guardar(usuario);
    }

    /**
     * Reenvia el codigo de verificacion respetando un cooldown de 3 minutos.
     */
    public void reenviarCodigo(String uuid, String email) {
        UsuarioBase usuario = usuarioRepositorio.porUuid(uuid)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                        "Usuario no encontrado", "UsuarioBaseService | reenviarCodigo | no existe el usuario: " + uuid));

        if (usuario.getUltimoEnvioDelCodigoDeVerificacion() != null) {
            LocalDateTime finDeLaEspera = usuario.getUltimoEnvioDelCodigoDeVerificacion().plus(3, ChronoUnit.MINUTES);
            if (FechaHoraConfig.ahoraLocalDateTime().isBefore(finDeLaEspera)) {
                throw new ExcepcionDeApi(CodigoDeErrorIdentity.DEMASIADOS_INTENTOS,
                        "Por favor espere unos minutos antes de reenviar el código", "UsuarioBaseService | reenviarCodigo | espera de 3 minutos activa");
            }
        }

        if (email != null && !email.isEmpty() && !email.equals(usuario.getEmail())) {
            usuario.setEmail(email);
        }

        generarCodigoDeVerificacion(usuario);
        usuarioRepositorio.guardar(usuario);

        enviarMailDeVerificacion(usuario);
    }

    // Envia por email el codigo de verificacion de cuenta, con el contenido de hookObtenerNotificacionPorMail.
    // Dependencias externas: el servicio de mail (core-mail). Recibe: el usuario con su codigo ya generado.
    // Lanza ERROR_INTERNO si falla el envio.
    protected void enviarMailDeVerificacion(UsuarioBase usuario) {
        try {
            NotificacionPorMail mail = hookObtenerNotificacionPorMail(PROPOSITO_REGISTRO, usuario, usuario.getCodigoDeVerificacion());
            enviador().enviar(usuario.getEmail(), mail.getAsunto(), mail.getCuerpo());
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, e.getMessage());
        }
    }

    // Responde: el EnviadorDeMail de core-mail; lanza SERVICIO_DE_MAIL_NO_DISPONIBLE si el proyecto no lo declara
    protected EnviadorDeMail enviador() {
        EnviadorDeMail enviador = enviadorDeMail.getIfAvailable();
        if (enviador == null) {
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.SERVICIO_DE_MAIL_NO_DISPONIBLE,
                    "El proyecto no declara la dependencia core-mail, necesaria para enviar codigos por email");
        }
        return enviador;
    }

    // Asigna al usuario un codigo de verificacion de 6 caracteres que vence en 10 minutos y registra
    // la hora del envio. No guarda el usuario.
    protected void generarCodigoDeVerificacion(UsuarioBase usuario) {
        String codigo = generarAlfanumericoAleatorio(6);
        usuario.setCodigoDeVerificacion(codigo);
        usuario.setVencimientoDelCodigoDeVerificacion(FechaHoraConfig.ahoraLocalDateTime().plus(10, ChronoUnit.MINUTES));
        usuario.setUltimoEnvioDelCodigoDeVerificacion(FechaHoraConfig.ahoraLocalDateTime());
    }

    // Recibe: el largo. Responde: un texto aleatorio (SecureRandom) de letras mayusculas y digitos
    protected String generarAlfanumericoAleatorio(int largo) {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom aleatorio = new SecureRandom();
        StringBuilder sb = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) {
            sb.append(caracteres.charAt(aleatorio.nextInt(caracteres.length())));
        }
        return sb.toString();
    }

    /**
     * Genera y envia un codigo de recuperacion de contrasena, con cooldown de 5 minutos.
     * No revela si el email existe o no.
     */
    public void recuperarContrasena(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.DATOS_INCOMPLETOS, "UsuarioBaseService | recuperarContrasena | falta el email");
        }

        // No revela si el email existe: si no hay usuario activo, corta sin error
        Optional<UsuarioBase> encontrado = usuarioRepositorio.porEmailActivo(email);
        if (encontrado.isEmpty()) {
            Logger.getLogger(UsuarioBaseService.class.getName()).info("Usuario no encontrado: " + email);
            return;
        }
        UsuarioBase usuario = encontrado.get();

        Optional<CodigoDeSeguridad> codigoPrevio = codigoDeSeguridadRepository
                .findFirstByIdUsuarioAndStatusOrderByFechaDeEmisionDesc(usuario.getId(), 0);
        if (codigoPrevio.isPresent()) {
            CodigoDeSeguridad codigoAnterior = codigoPrevio.get();
            LocalDateTime haceCincoMinutos = FechaHoraConfig.ahoraLocalDateTime().minus(5, ChronoUnit.MINUTES);
            if (codigoAnterior.getFechaDeEmision().isAfter(haceCincoMinutos)) {
                throw new ExcepcionDeApi(CodigoDeErrorIdentity.DEMASIADOS_INTENTOS,
                        "Ya te enviamos un mail. Por favor, espera 5 minutos para solicitar otro.",
                        "UsuarioBaseService | recuperarContrasena | ya se envio un mail hace menos de 5 minutos");
            }
            codigoAnterior.setStatus(-1);
            codigoAnterior.setFechaDeBorrado(FechaHoraConfig.ahoraTimestamp());
            codigoDeSeguridadRepository.save(codigoAnterior);
        }

        String codigo = generarAlfanumericoAleatorio(32);

        CodigoDeSeguridad codigoDeSeguridad = new CodigoDeSeguridad();
        codigoDeSeguridad.setCodigo(codigo);
        codigoDeSeguridad.setFechaDeEmision(FechaHoraConfig.ahoraLocalDateTime());
        codigoDeSeguridad.setProposito(PROPOSITO_RECUPERAR_CONTRASENA);
        codigoDeSeguridad.setUsado(false);
        codigoDeSeguridad.setIdUsuario(usuario.getId());
        codigoDeSeguridadRepository.save(codigoDeSeguridad);

        enviarMailDeRecuperacion(usuario, codigo);
    }

    /**
     * Cambia la contrasena validando el codigo enviado por email (vigencia de 15 minutos).
     */
    public void cambiarContrasena(CambiarContrasenaDTO dto) {
        UsuarioBase usuario = usuarioRepositorio.porEmailActivo(dto.getEmail())
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                        "No existe el usuario", "No existe el usuario"));

        CodigoDeSeguridad codigoDeSeguridad = codigoDeSeguridadRepository
                .findFirstByCodigoAndIdUsuario(dto.getCodigo(), usuario.getId())
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorIdentity.CODIGO_INVALIDO));

        if (codigoDeSeguridad.getUsado() != null && codigoDeSeguridad.getUsado()) {
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.CODIGO_YA_UTILIZADO);
        }

        LocalDateTime haceQuinceMinutos = FechaHoraConfig.ahoraLocalDateTime().minus(15, ChronoUnit.MINUTES);
        if (codigoDeSeguridad.getFechaDeEmision().isBefore(haceQuinceMinutos)) {
            codigoDeSeguridad.setStatus(-1);
            codigoDeSeguridad.setFechaDeBorrado(FechaHoraConfig.ahoraTimestamp());
            codigoDeSeguridadRepository.save(codigoDeSeguridad);
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.CODIGO_VENCIDO, "El código ya venció");
        }

        usuario.setContrasena(passwordEncoder.encode(dto.getContrasena()));
        usuarioRepositorio.guardar(usuario);

        codigoDeSeguridad.setUsado(true);
        codigoDeSeguridad.setStatus(-1);
        codigoDeSeguridad.setFechaDeBorrado(FechaHoraConfig.ahoraTimestamp());
        codigoDeSeguridadRepository.save(codigoDeSeguridad);
    }

    // Envia por email el codigo de recuperacion de contrasena, con el contenido de hookObtenerNotificacionPorMail.
    // Dependencias externas: el servicio de mail (core-mail). Recibe: el usuario y el codigo.
    // Lanza ERROR_INTERNO si falla el envio.
    protected void enviarMailDeRecuperacion(UsuarioBase usuario, String codigo) {
        try {
            NotificacionPorMail mail = hookObtenerNotificacionPorMail(PROPOSITO_RECUPERAR_CONTRASENA, usuario, codigo);
            enviador().enviar(usuario.getEmail(), mail.getAsunto(), mail.getCuerpo());
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "UsuarioBaseService | enviarMailDeRecuperacion | error: " + e.getMessage());
        }
    }

    /**
     * El proyecto define aqui el contenido de los emails de registro y recuperacion.
     * Recibe: el proposito (PROPOSITO_*), el usuario y el codigo generado.
     * Responde: la notificacion con asunto y cuerpo.
     */
    protected NotificacionPorMail hookObtenerNotificacionPorMail(String proposito, UsuarioBase usuario, String codigo) {
        throw new ExcepcionDeApi(CodigoDeErrorIdentity.HOOK_NO_IMPLEMENTADO,
                "La aplicación debe implementar el hookObtenerNotificacionPorMail para el propósito: " + proposito);
    }
}
