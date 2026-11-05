package com.blue.open.Core.Kernel.Paginado;

import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import org.springframework.core.convert.support.DefaultConversionService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Convierte los valores del paginado al tipo Java del atributo o del campo destino.
 * El front manda todos los valores de los filtros como texto; JPQL, a diferencia del SQL nativo,
 * exige que cada parametro tenga el tipo del atributo con el que se compara.
 * Las fechas se interpretan siempre en GMT-3 (America/Argentina/Buenos_Aires).
 * Dependencias externas: ninguna.
 * Recibe: el valor y el tipo destino. Responde: el valor convertido, o ExcepcionDeApi si no es valido.
 */
public final class ConversorDeValores {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final Map<Class<?>, Class<?>> PRIMITIVOS = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            short.class, Short.class,
            byte.class, Byte.class,
            double.class, Double.class,
            float.class, Float.class,
            boolean.class, Boolean.class,
            char.class, Character.class);

    private ConversorDeValores() {
    }

    // Convierte el texto de un filtro al tipo del atributo. campo solo se usa para el mensaje de error.
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object desdeTexto(String valor, Class<?> tipo, String campo) {
        if (valor == null) {
            return null;
        }
        Class<?> destino = PRIMITIVOS.getOrDefault(tipo, tipo);
        String texto = valor.trim();
        try {
            if (destino == String.class) return valor;
            if (destino == Long.class) return Long.valueOf(texto);
            if (destino == Integer.class) return Integer.valueOf(texto);
            if (destino == Short.class) return Short.valueOf(texto);
            if (destino == Byte.class) return Byte.valueOf(texto);
            if (destino == Double.class) return Double.valueOf(texto);
            if (destino == Float.class) return Float.valueOf(texto);
            if (destino == BigDecimal.class) return new BigDecimal(texto);
            if (destino == BigInteger.class) return new BigInteger(texto);
            if (destino == Boolean.class) return aBoolean(texto, campo);
            if (destino == UUID.class) return UUID.fromString(texto);
            if (destino.isEnum()) return Enum.valueOf((Class<Enum>) destino, texto);
            if (destino == LocalDateTime.class) return aLocalDateTime(texto);
            if (destino == LocalDate.class) return aLocalDate(texto);
            if (destino == Timestamp.class) return Timestamp.valueOf(aLocalDateTime(texto));
            if (destino == java.sql.Date.class) return java.sql.Date.valueOf(aLocalDate(texto));
            if (destino == Date.class) return Date.from(aLocalDateTime(texto).atZone(ZONA).toInstant());
            if (destino == OffsetDateTime.class) return aLocalDateTime(texto).atZone(ZONA).toOffsetDateTime();
            if (destino == ZonedDateTime.class) return aLocalDateTime(texto).atZone(ZONA);
            if (destino == Instant.class) return aLocalDateTime(texto).atZone(ZONA).toInstant();
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                    "El valor '" + valor + "' no es valido para el campo '" + campo + "'",
                    "ConversorDeValores | desdeTexto | " + destino.getSimpleName() + " | " + e.getMessage());
        }
        throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO, "El campo '" + campo + "' no se puede filtrar",
                "ConversorDeValores | desdeTexto | tipo no soportado: " + destino.getName());
    }

    // Adapta el valor que devuelve la base al tipo del campo del DTO (ej: Integer -> int, Timestamp -> LocalDateTime)
    public static Object alTipoDelCampo(Object valor, Class<?> tipo) {
        if (valor == null) {
            return null;
        }
        Class<?> destino = PRIMITIVOS.getOrDefault(tipo, tipo);
        if (destino.isInstance(valor)) {
            return valor;
        }
        return DefaultConversionService.getSharedInstance().convert(valor, destino);
    }

    private static Boolean aBoolean(String texto, String campo) {
        if ("true".equalsIgnoreCase(texto) || "1".equals(texto)) return true;
        if ("false".equalsIgnoreCase(texto) || "0".equals(texto)) return false;
        throw new ExcepcionDeApi(CodigoDeErrorKernel.PAGINADO_INVALIDO,
                "El valor '" + texto + "' no es valido para el campo '" + campo + "'",
                "ConversorDeValores | aBoolean | se esperaba true/false/1/0");
    }

    // Acepta "2026-08-06", "2026-08-06T10:46:00" y "2026-08-06T10:46:00.123-03:00".
    // Si trae zona horaria se pasa a GMT-3.
    private static LocalDateTime aLocalDateTime(String texto) {
        try {
            return OffsetDateTime.parse(texto).atZoneSameInstant(ZONA).toLocalDateTime();
        } catch (DateTimeParseException e) {
            // sin zona horaria: se asume GMT-3
        }
        if (texto.length() == 10) {
            return LocalDate.parse(texto).atStartOfDay();
        }
        return LocalDateTime.parse(texto.replace(' ', 'T'));
    }

    private static LocalDate aLocalDate(String texto) {
        if (texto.length() == 10) {
            return LocalDate.parse(texto);
        }
        return aLocalDateTime(texto).toLocalDate();
    }
}
