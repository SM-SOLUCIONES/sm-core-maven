package com.blue.open.Core.Kernel.Contrato;

import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Puertos.AutorizadorDeEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Hace cumplir el permiso de @Contrato antes de ejecutar el metodo del controller (y antes de @Valid: un usuario
 * sin permiso recibe 403, nunca 400). Solo actua sobre controllers de com.blue.open: Actuator, /error y los recursos
 * estaticos no pasan por aca. Un endpoint sin @Contrato se rechaza con 403 (ademas, VerificadorDeContratos impide
 * que la aplicacion arranque con uno).
 * Dependencias externas: el AutorizadorDeEndpoint del proyecto (core-identity o el default del kernel).
 * Recibe: el request y el metodo que lo atiende. Responde: true si puede seguir; lanza ExcepcionDeApi si no.
 */
@RequiredArgsConstructor
public class InterceptorDeContrato implements HandlerInterceptor {

    // ObjectProvider: el autorizador se resuelve en cada request, no al registrar el interceptor
    private final ObjectProvider<AutorizadorDeEndpoint> autorizador;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo) || !esDelProyecto(metodo)) {
            return true;
        }
        Contrato contrato = metodo.getMethodAnnotation(Contrato.class);
        if (contrato == null) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                    "InterceptorDeContrato | " + metodo.getBeanType().getSimpleName() + "." + metodo.getMethod().getName()
                            + " no tiene @Contrato");
        }
        autorizador.getObject().autorizar(contrato.permiso());
        return true;
    }

    // Controllers del proyecto y del Core (no los de Spring ni de terceros)
    static boolean esDelProyecto(HandlerMethod metodo) {
        return metodo.getBeanType().getName().startsWith("com.blue.open.");
    }
}
