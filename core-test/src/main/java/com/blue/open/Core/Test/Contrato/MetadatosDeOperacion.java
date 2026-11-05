package com.blue.open.Core.Test.Contrato;

import com.blue.open.Core.Kernel.Contrato.Contrato;

import java.util.List;

/**
 * Metadatos de una operacion en el contrato, leidos del @Contrato del metodo del controller.
 * permiso: PUBLICO, AUTENTICADO o los roles/permisos que la habilitan (alcanza con uno).
 */
public record MetadatosDeOperacion(String casoDeUso, String resumen, List<String> permiso) {

    // Metadatos leidos de la anotacion del controller
    public static MetadatosDeOperacion de(Contrato contrato) {
        return new MetadatosDeOperacion(contrato.casoDeUso(), contrato.resumen(), List.of(contrato.permiso()));
    }

    // La operacion exige sesion (todo lo que no es PUBLICO)
    public boolean exigeSesion() {
        return !permiso.contains(Contrato.PUBLICO);
    }

    // La operacion exige un rol o permiso puntual, ademas de la sesion
    public boolean exigePermiso() {
        return exigeSesion() && !permiso.contains(Contrato.AUTENTICADO);
    }

    // Roles/permisos tal como se muestran en el contrato (ej. "SYSADMIN, USUARIO_VER"). Vacio si no exige ninguno
    public String permisoDeclarado() {
        return exigePermiso() ? String.join(", ", permiso) : "";
    }
}
