package com.blue.open.Core.Kernel.Puertos;

import com.blue.open.Core.Kernel.Contrato.Contrato;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;

import java.util.Arrays;
import java.util.Set;

/**
 * Implementacion por defecto de {@link AutorizadorDeEndpoint} para proyectos sin core-identity.
 * No hay usuario logeado: solo pasan los endpoints PUBLICO, el resto responde 401 NO_AUTENTICADO (ninguna request
 * puede identificar a un usuario). Asi un proyecto sin autenticacion no puede publicar por error un endpoint que
 * declara un permiso.
 * Dependencias externas: ninguna. Recibe: el permiso de @Contrato. Responde: nada, o 401 NO_AUTENTICADO.
 */
public class AutorizadorDenegarTodo implements AutorizadorDeEndpoint {

    @Override
    public void autorizar(String[] permiso) {
        if (Arrays.asList(permiso).contains(Contrato.PUBLICO)) {
            return;
        }
        throw new ExcepcionDeApi(CodigoDeErrorKernel.NO_AUTENTICADO,
                "El proyecto no declara un AutorizadorDeEndpoint (no usa core-identity)");
    }

    @Override
    public Set<String> nombresValidos() {
        return Set.of();
    }
}
