package com.blue.open.Core.Kernel.Trazabilidad;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blue.open.Core.Kernel.DTO.Pagina;

import java.util.List;
import java.util.Map;

class SanitizadorDeLogsTest {

    private final SanitizadorDeLogs sanitizador = new SanitizadorDeLogs(new ObjectMapper());

    @Test
    void sanitizarCuerpo_tapaCamposSensiblesAnidadosYListas() {
        String contenido = "{\"nombre\":\"Juan\",\"imagenBase64\":\"dato-grande\","
                + "\"usuario\":{\"password\":\"secreto\"},"
                + "\"archivos\":[{\"contenidoDelArchivo\":\"binario\"}]}";

        String resultado = sanitizador.sanitizarCuerpo(contenido, "application/json");

        assertTrue(resultado.contains("\"nombre\":\"Juan\""));
        assertTrue(resultado.contains("\"imagenBase64\":\"[REDACTED]\""));
        assertTrue(resultado.contains("\"password\":\"[REDACTED]\""));
        assertTrue(resultado.contains("\"contenidoDelArchivo\":\"[REDACTED]\""));
        assertFalse(resultado.contains("dato-grande"));
        assertFalse(resultado.contains("secreto"));
        assertFalse(resultado.contains("binario"));
    }

    // Los DTOs de usuario (UsuarioBaseDTO, UsuarioYContrasenaDTO, CambiarContrasenaDTO) nombran la contrasena en
    // espanol desde el ticket 06 del glosario: el log la sigue tapando
    @Test
    void sanitizarCuerpo_tapaLaContrasenaConElNombreEnEspanol() {
        String contenido = "{\"nombreDeUsuario\":\"demo\",\"contrasena\":\"secreto\","
                + "\"usuario\":{\"codigoDeVerificacion\":\"ABC123\"}}";

        String resultado = sanitizador.sanitizarCuerpo(contenido, "application/json");

        assertTrue(resultado.contains("\"nombreDeUsuario\":\"demo\""));
        assertTrue(resultado.contains("\"contrasena\":\"[REDACTED]\""));
        assertTrue(resultado.contains("\"codigoDeVerificacion\":\"[REDACTED]\""));
        assertFalse(resultado.contains("secreto"));
        assertFalse(resultado.contains("ABC123"));
    }

    // "contenido" es la lista de registros de Pagina<T> (ticket 03): no se tapa. El contenido de un archivo se nombra
    // contenidoDelArchivo y ese si se tapa
    @Test
    void sanitizarValor_noTapaElContenidoDeUnaPaginaYSiElContenidoDelArchivo() {
        Pagina<Map<String, String>> pagina = new Pagina<>(
                List.of(Map.of("nombre", "Juan", "contenidoDelArchivo", "binario")), 0, 10, 1);

        String resultado = sanitizador.sanitizarValor(pagina, new String[0]);

        assertTrue(resultado.contains("\"contenido\":[{"));
        assertTrue(resultado.contains("\"nombre\":\"Juan\""));
        assertTrue(resultado.contains("\"contenidoDelArchivo\":\"[REDACTED]\""));
        assertFalse(resultado.contains("binario"));
    }

    @Test
    void sanitizarCuerpo_omitsNonJsonAndLargePayloads() {
        assertEquals("[payload omitido]",
                sanitizador.sanitizarCuerpo("password=secreto", "application/x-www-form-urlencoded"));
        assertEquals("[payload omitido]",
                sanitizador.sanitizarCuerpo("x".repeat(10_001), "application/json"));
    }

    @Test
    void sanitizarEncabezado_tapaCredenciales() {
        assertEquals("[REDACTED]", sanitizador.sanitizarEncabezado("Authorization", "Bearer secreto"));
        assertEquals("valor", sanitizador.sanitizarEncabezado("X-Empresa-Uuid", "valor"));
    }

    @Test
    void sanitizarValor_aplicaLosCamposExcluidosAdicionales() {
        String resultado = sanitizador.sanitizarValor(Map.of(
                "visible", "valor",
                "datoInterno", "secreto"), new String[]{"datoInterno"});

        assertTrue(resultado.contains("\"visible\":\"valor\""));
        assertTrue(resultado.contains("\"datoInterno\":\"[REDACTED]\""));
        assertFalse(resultado.contains("secreto"));
    }

    // POST /usuario/sesion/cambiar-contrasena manda contrasenaActual y contrasenaNueva: el criterio por raiz las tapa
    // aunque no sean la clave exacta "contrasena"
    @Test
    void sanitizarCuerpo_tapaClavesQueContienenUnaRaizSensible() {
        String contenido = "{\"contrasenaActual\":\"vieja-123\",\"contrasena_nueva\":\"nueva-456\","
                + "\"clientSecret\":\"cs-789\",\"tokenDeSesion\":\"tk-000\"}";

        String resultado = sanitizador.sanitizarCuerpo(contenido, "application/json");

        assertTrue(resultado.contains("\"contrasenaActual\":\"[REDACTED]\""));
        assertTrue(resultado.contains("\"contrasena_nueva\":\"[REDACTED]\""));
        assertTrue(resultado.contains("\"clientSecret\":\"[REDACTED]\""));
        assertTrue(resultado.contains("\"tokenDeSesion\":\"[REDACTED]\""));
        assertFalse(resultado.contains("vieja-123"));
        assertFalse(resultado.contains("nueva-456"));
        assertFalse(resultado.contains("cs-789"));
        assertFalse(resultado.contains("tk-000"));
    }

    // Las claves que no contienen una raiz sensible siguen saliendo en el log, incluido el rol de secretario y las
    // claves de archivo que no son su contenido
    @Test
    void sanitizarValor_noTapaClavesNoSensibles() {
        String resultado = sanitizador.sanitizarValor(Map.of(
                "legajo", "99001",
                "nombreDeUsuario", "demo",
                "secretarioUuid", "uuid-secretario",
                "nombreDelArchivo", "acta.pdf",
                "contenido", "lista"), new String[0]);

        assertTrue(resultado.contains("\"legajo\":\"99001\""));
        assertTrue(resultado.contains("\"nombreDeUsuario\":\"demo\""));
        assertTrue(resultado.contains("\"secretarioUuid\":\"uuid-secretario\""));
        assertTrue(resultado.contains("\"nombreDelArchivo\":\"acta.pdf\""));
        assertTrue(resultado.contains("\"contenido\":\"lista\""));
        assertFalse(resultado.contains("[REDACTED]"));
    }

    // ExcepcionDeApi.conDato usa el mismo criterio: descarta las claves que contienen una raiz sensible
    @Test
    void esClaveSensible_usaElCriterioPorRaiz() {
        assertTrue(SanitizadorDeLogs.esClaveSensible("contrasenaActual"));
        assertTrue(SanitizadorDeLogs.esClaveSensible("contrasenaNueva"));
        assertTrue(SanitizadorDeLogs.esClaveSensible("Refresh-Token"));
        assertFalse(SanitizadorDeLogs.esClaveSensible("secretaria"));
        assertFalse(SanitizadorDeLogs.esClaveSensible("legajo"));
        assertFalse(SanitizadorDeLogs.esClaveSensible(null));
    }
}
