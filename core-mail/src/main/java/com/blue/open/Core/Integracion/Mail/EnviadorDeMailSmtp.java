package com.blue.open.Core.Integracion.Mail;

import com.blue.open.Core.Kernel.Puertos.EnviadorDeMail;

import com.blue.open.Core.Integracion.Mail.Excepciones.CodigoDeErrorMail;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

// Implementacion SMTP del puerto EnviadorDeMail, con la configuracion spring.mail.* del application.properties
@Service
@Slf4j
public class EnviadorDeMailSmtp implements EnviadorDeMail {

    @Value("${spring.mail.host:#{null}}")
    private String servidor;

    @Value("${spring.mail.port:#{null}}")
    private Integer puerto;

    @Value("${spring.mail.username:#{null}}")
    private String usuario;

    @Value("${spring.mail.password:#{null}}")
    private String contrasena;

    @Value("${spring.mail.protocol:smtp}")
    private String protocolo;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private String auth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private String starttls;

    @Value("${spring.mail.properties.mail.debug:false}")
    private String debug;

    @Value("${mail.opcion:smtp}")
    private String opcion;

    private void validarConfiguracion() {
        if (estaVacio(servidor) || puerto == null || estaVacio(usuario) || estaVacio(contrasena)) {
            log.error(
                    "Configuracion incompleta para el servicio de mail. spring.mail.host={}, spring.mail.port={}, spring.mail.username={}, spring.mail.password={}",
                    valorONull(servidor),
                    puerto,
                    valorONull(usuario),
                    enmascararSecreto(contrasena)
            );
            throw new ExcepcionDeApi(CodigoDeErrorMail.MAIL_NO_DISPONIBLE, "No existe spring.mail.host, spring.mail.port, spring.mail.username o spring.mail.password en el application.properties");
        }
    }

    // Usado por el smoke test de arranque para saber si la integracion esta configurada, sin lanzar excepcion
    @Override
    public String getNombreIntegracion() {
        return "Mail";
    }

    @Override
    public boolean estaConfigurada() {
        // No usa validarConfiguracion(): esa loguea ERROR, y en el arranque la falta de configuracion la reporta el smoke test como WARN
        return !(estaVacio(servidor) || puerto == null || estaVacio(usuario) || estaVacio(contrasena));
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

    private JavaMailSenderImpl crearMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(servidor);
        mailSender.setPort(puerto);
        mailSender.setUsername(usuario);
        mailSender.setPassword(contrasena);

        Properties propiedades = mailSender.getJavaMailProperties();
        propiedades.put("mail.transport.protocol", protocolo);
        propiedades.put("mail.smtp.auth", auth);
        propiedades.put("mail.smtp.starttls.enable", starttls);
        propiedades.put("mail.debug", debug);

        return mailSender;
    }

    /**
     * Envia un correo electronico con configuracion desde application.properties
     *
     * @param para     Destinatario
     * @param asunto   Asunto
     * @param cuerpo   Cuerpo del mensaje (HTML soportado)
     * @param adjuntos Mapa de archivos adjuntos (Nombre archivo -> Bytes contenido). Puede ser null.
     */
    public void enviar(String para, String asunto, String cuerpo, Map<String, byte[]> adjuntos) throws MessagingException {
        if ("notify".equalsIgnoreCase(opcion)) {
            throw new ExcepcionDeApi(CodigoDeErrorMail.FUNCIONALIDAD_NO_IMPLEMENTADA, "El envio por notificacion (notify) no esta implementado todavia");
        }

        this.validarConfiguracion();

        // Por defecto o si es 'smtp', usamos JavaMailSenderImpl de Spring
        JavaMailSenderImpl mailSender = crearMailSender();

        MimeMessage mensaje = mailSender.createMimeMessage();
        // true = multipart (para adjuntos)
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

        helper.setFrom(usuario);
        helper.setTo(para);
        helper.setSubject(asunto);
        helper.setText(cuerpo, true); // true = es HTML

        if (adjuntos != null && !adjuntos.isEmpty()) {
            for (Map.Entry<String, byte[]> adjunto : adjuntos.entrySet()) {
                if (adjunto.getValue() != null && adjunto.getKey() != null) {
                    helper.addAttachment(adjunto.getKey(), new ByteArrayResource(adjunto.getValue()));
                }
            }
        }

        mailSender.send(mensaje);
    }

    /**
     * Envia un correo electronico sin archivos adjuntos
     *
     * @param para   Destinatario
     * @param asunto Asunto
     * @param cuerpo Cuerpo del mensaje (HTML soportado)
     */
    public void enviar(String para, String asunto, String cuerpo) throws MessagingException {
        enviar(para, asunto, cuerpo, null);
    }
}
