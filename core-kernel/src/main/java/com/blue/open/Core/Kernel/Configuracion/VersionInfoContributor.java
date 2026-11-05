package com.blue.open.Core.Kernel.Configuracion;

import com.blue.open.Core.Kernel.CoreVersion;
import com.blue.open.Core.Kernel.Puertos.ProveedorDeVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Publica en /info la version del proyecto y la del Core.
 * Dependencias externas: ninguna. Recibe: nada.
 * Responde: version (del proyecto) y coreVersion (del artefacto core-kernel).
 */
@Component
@RequiredArgsConstructor
public class VersionInfoContributor implements InfoContributor {

    private final ProveedorDeVersion proveedorDeVersion;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("version", proveedorDeVersion.getVersion());
        builder.withDetail("coreVersion", CoreVersion.get());
    }
}
