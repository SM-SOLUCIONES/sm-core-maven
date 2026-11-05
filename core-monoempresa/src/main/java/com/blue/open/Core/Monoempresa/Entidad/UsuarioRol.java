package com.blue.open.Core.Monoempresa.Entidad;

import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Vinculo directo entre un usuario y un rol, sin empresas de por medio.
 * Es el modelo de scoping de los proyectos mono-empresa: un usuario tiene roles y punto.
 * Se relaciona con {@link Rol} y con el usuario por id (id_usuario), porque el Core no conoce
 * la clase concreta de usuario del proyecto.
 */
@Setter
@Getter
@Table(name = "auth_usuarios_roles")
@Entity()
public class UsuarioRol extends EntidadConBorradoLogico {

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol")
    private Rol rol;
}
