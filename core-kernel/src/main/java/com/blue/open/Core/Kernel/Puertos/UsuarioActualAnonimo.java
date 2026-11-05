package com.blue.open.Core.Kernel.Puertos;

import java.util.List;

/**
 * Implementacion por defecto de {@link ProveedorDeUsuarioActual} para proyectos sin core-identity.
 * No hay usuario logeado: la auditoria queda en null y nunca tiene permisos.
 * Dependencias externas: ninguna. Recibe: nada. Responde: siempre "sin usuario".
 */
public class UsuarioActualAnonimo implements ProveedorDeUsuarioActual {

    @Override
    public Long getIdUsuarioActual() {
        return null;
    }

    @Override
    public boolean tienePermisos(List<Enum<?>> requeridos) {
        return false;
    }
}
