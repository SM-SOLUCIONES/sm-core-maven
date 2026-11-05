package com.blue.open.Core.Kernel.Util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

// Procesa plantillas Thymeleaf (ej. el HTML de un mail)
@Component
@RequiredArgsConstructor
public class ThymeleafUtil {

    private final TemplateEngine templateEngine;

    // Procesa una plantilla Thymeleaf con el modelo recibido y devuelve el HTML resultante.
    // Recibe: el modelo de variables y el nombre de la plantilla. Responde: el HTML, o "" si el modelo es null.
    public String procesarHtml(Map<String, Object> modelo, String plantilla) {

        Context context = new Context();

        if (modelo != null) {
            modelo.forEach((key, value) -> context.setVariable(key, value));
            return templateEngine.process(plantilla, context);
        }
        return "";

    }
}