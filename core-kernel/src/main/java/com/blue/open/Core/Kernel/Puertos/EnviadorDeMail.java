package com.blue.open.Core.Kernel.Puertos;

import java.util.Map;

/**
 * Puerto de envio de emails. Lo implementa core-mail; si ese modulo no esta, no hay bean
 * y el codigo que necesite mandar mails debe fallar con un mensaje claro.
 * Recibe: destinatario, asunto, cuerpo HTML y adjuntos opcionales (nombre -> contenido).
 * Responde: nada; lanza excepcion si el envio falla.
 */
public interface EnviadorDeMail extends IntegracionExterna {

    void enviar(String para, String asunto, String cuerpo) throws Exception;

    void enviar(String para, String asunto, String cuerpo, Map<String, byte[]> adjuntos) throws Exception;
}
