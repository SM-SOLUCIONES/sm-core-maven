package com.blue.open.Core.Crud;

import com.blue.open.Core.Kernel.Proyeccion.Proyector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Valida al arrancar las proyecciones de todos los services del ABM (AbstractService) del contexto: el DTO de uuid,
 * guardar y borrar y el del paginado, contra su entidad. Una @Columna o @Coleccion con una ruta inexistente, una
 * @Coleccion que no apunta a una coleccion de entidades o un ciclo de DTOs cortan el arranque con el detalle,
 * en vez de aparecer en el primer request.
 * Dependencias externas: el metamodelo de JPA (via Proyector).
 * Recibe: los AbstractService del contexto. Responde: nada; lanza IllegalStateException si una proyeccion es invalida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidadorDeProyecciones implements SmartInitializingSingleton {

    // ObjectProvider: un proyecto puede no tener ningun service del ABM
    @SuppressWarnings("rawtypes")
    private final ObjectProvider<AbstractService> services;

    private final Proyector proyector;

    // Recorre los services una vez creados todos los singletons
    @Override
    public void afterSingletonsInstantiated() {
        services.forEach(service -> {
            ResolvableType tipo = ResolvableType.forClass(AopUtils.getTargetClass(service)).as(AbstractService.class);
            Class<?> entidad = tipo.getGeneric(0).resolve();
            for (int indice : new int[] { 1, 2 }) {
                Class<?> dto = tipo.getGeneric(indice).resolve();
                if (entidad != null && dto != null) {
                    proyector.validar(entidad, dto);
                }
            }
        });
        log.info("Proyecciones del ABM validadas");
    }
}
