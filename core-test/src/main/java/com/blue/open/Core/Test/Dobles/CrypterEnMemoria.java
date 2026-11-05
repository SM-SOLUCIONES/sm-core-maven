package com.blue.open.Core.Test.Dobles;

import com.blue.open.Core.Integracion.Crypter.CrypterService;
import com.blue.open.Core.Integracion.Crypter.Excepciones.CodigoDeErrorCrypter;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doble de CrypterService para las pruebas: guarda los archivos en un mapa (resourceKey -> archivo) en vez de
 * subirlos al webservice, y devuelve el base64 al pedirlos. Reemplaza (@Primary) al de core-crypter con el profile
 * "test". Permite assertear cuantos archivos se subieron y a que carpeta.
 */
public class CrypterEnMemoria extends CrypterService implements DobleEnMemoria {

    private final Map<String, ArchivoGuardado> archivos = new ConcurrentHashMap<>();

    @Override
    public String guardarArchivo(String base64) {
        return guardarArchivo(base64, true, null, null, null);
    }

    @Override
    public String guardarArchivo(String base64, boolean encriptar) {
        return guardarArchivo(base64, encriptar, null, null, null);
    }

    @Override
    public String guardarArchivo(String base64, boolean encriptar, String nombreCarpeta) {
        return guardarArchivo(base64, encriptar, nombreCarpeta, null, null);
    }

    @Override
    public String guardarArchivo(String base64, boolean encriptar, String nombreCarpeta, String nombreColeccion) {
        return guardarArchivo(base64, encriptar, nombreCarpeta, nombreColeccion, null);
    }

    // Guarda el archivo y responde un resourceKey nuevo
    @Override
    public String guardarArchivo(String base64, boolean encriptar, String nombreCarpeta, String nombreColeccion,
            String nombreArchivo) {
        String resourceKey = "test-" + UUID.randomUUID();
        archivos.put(resourceKey, new ArchivoGuardado(resourceKey, base64, encriptar, nombreCarpeta, nombreColeccion,
                nombreArchivo));
        return resourceKey;
    }

    // Responde el base64 de cada resourceKey. Lanza ExcepcionDeApi CRYPTER_NO_DISPONIBLE (igual que el webservice
    // real) si alguno no existe
    @Override
    public List<String> getArchivos(List<String> resourceKeys) {
        List<String> respuesta = new ArrayList<>();
        for (String resourceKey : resourceKeys) {
            ArchivoGuardado archivo = archivos.get(resourceKey);
            if (archivo == null) {
                throw new ExcepcionDeApi(CodigoDeErrorCrypter.CRYPTER_NO_DISPONIBLE,
                        "Error en la respuesta del servidor de archivos",
                        "CrypterEnMemoria | getArchivos | resourceKey inexistente: " + resourceKey);
            }
            respuesta.add(archivo.base64());
        }
        return respuesta;
    }

    @Override
    public String getArchivo(String resourceKey) {
        return getArchivos(List.of(resourceKey)).get(0);
    }

    @Override
    public String getNombreIntegracion() {
        return "Crypter (en memoria)";
    }

    @Override
    public boolean estaConfigurada() {
        return true;
    }

    // Archivos guardados desde el ultimo limpiar()
    public List<ArchivoGuardado> archivos() {
        return List.copyOf(archivos.values());
    }

    // Archivos guardados en la carpeta recibida (parentKey)
    public List<ArchivoGuardado> archivosEnCarpeta(String carpeta) {
        return archivos.values().stream().filter(a -> carpeta.equals(a.carpeta())).toList();
    }

    @Override
    public void limpiar() {
        archivos.clear();
    }

    // Un archivo guardado con los datos con los que se lo subio
    public record ArchivoGuardado(String resourceKey, String base64, boolean encriptado, String carpeta,
            String coleccion, String nombre) {
    }
}
