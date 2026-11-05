package com.blue.open.Core.Protocolo.ApiKey;

import com.blue.open.Core.Kernel.Proyeccion.NoProyectable;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Token de acceso personal de un usuario (API key), alternativa a basic auth/JWT para integraciones.
 * Se vincula al usuario por id (id_usuario), no por asociacion JPA, porque el Core no conoce la clase
 * concreta de usuario del proyecto. Un usuario puede tener varios tokens nombrados.
 */
@Setter
@Getter
@Table(name = "auth_access_tokens")
@Entity()
public class AccessToken extends EntidadConBorradoLogico {

    @Column(name = "id_usuario")
    private Long idUsuario;

    // Nombre identificador elegido por el usuario para reconocer el token (ej. "notebook laburo")
    private String nombre;
    // Valor secreto del token, se genera aleatoriamente y no es recuperable una vez creado
    @NoProyectable
    private String codigo;
    // 1 si el token esta habilitado, 0 si fue desactivado manualmente (distinto del soft delete de status)
    private int activo;
}
