package com.blue.open.Core.Crud.Borrado;

import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Elige la EstrategiaDeBorrado que corresponde a cada entidad, en orden de prioridad (Order):
 * primero borrado logico, y si la entidad no lo soporta, borrado fisico.
 * Un proyecto puede registrar su propia estrategia con un Order menor para ganarle a las dos.
 * Recibe: la clase de la entidad. Responde: la estrategia aplicable.
 */
@Component
public class SelectorDeBorrado {

    private final List<EstrategiaDeBorrado> estrategias;

    // Recibe todas las estrategias de borrado registradas, ordenadas por Order
    public SelectorDeBorrado(List<EstrategiaDeBorrado> estrategias) {
        this.estrategias = estrategias;
    }

    // Recibe: la clase de la entidad. Responde: la primera estrategia que aplica a ella;
    // lanza ERROR_INTERNO si ninguna aplica.
    public EstrategiaDeBorrado para(Class<?> claseDeEntidad) {
        for (EstrategiaDeBorrado estrategia : estrategias) {
            if (estrategia.aplicaA(claseDeEntidad)) {
                return estrategia;
            }
        }
        throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                "SelectorDeBorrado | no hay EstrategiaDeBorrado para " + claseDeEntidad.getName());
    }
}
