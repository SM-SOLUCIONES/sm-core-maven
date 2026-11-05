package com.blue.open.Core.Test.Dobles;

import com.blue.open.Core.Kernel.Puertos.EnviadorDeMail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Doble del puerto EnviadorDeMail para las pruebas: no conecta a ningun SMTP, acumula los mails enviados para que la
 * prueba los assertee. Reemplaza (@Primary) al de core-mail con el profile "test".
 */
public class EnviadorDeMailEnMemoria implements EnviadorDeMail, DobleEnMemoria {

    private final List<MailEnviado> enviados = Collections.synchronizedList(new ArrayList<>());

    // Acumula el mail con sus adjuntos
    @Override
    public void enviar(String para, String asunto, String cuerpo, Map<String, byte[]> adjuntos) {
        enviados.add(new MailEnviado(para, asunto, cuerpo,
                adjuntos == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(adjuntos))));
    }

    // Acumula el mail sin adjuntos
    @Override
    public void enviar(String para, String asunto, String cuerpo) {
        enviar(para, asunto, cuerpo, Map.of());
    }

    @Override
    public String getNombreIntegracion() {
        return "Mail (en memoria)";
    }

    @Override
    public boolean estaConfigurada() {
        return true;
    }

    // Mails enviados desde el ultimo limpiar(), en orden
    public List<MailEnviado> enviados() {
        return List.copyOf(enviados);
    }

    @Override
    public void limpiar() {
        enviados.clear();
    }

    // Un mail enviado: destinatario, asunto, cuerpo HTML y adjuntos (nombre -> contenido)
    public record MailEnviado(String para, String asunto, String cuerpo, Map<String, byte[]> adjuntos) {
    }
}
