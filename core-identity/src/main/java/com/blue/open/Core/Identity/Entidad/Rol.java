package com.blue.open.Core.Identity.Entidad;

import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Rol del sistema RBAC: agrupa {@link Permiso} y se asigna a usuarios segun el modulo de
 * scoping que use el proyecto (core-monoempresa o core-multiempresa).
 * Relacion N-a-N con {@link Permiso} via auth_roles_permisos.
 */
@Setter
@Getter
@Table(name = "auth_roles")
@Entity()
public class Rol extends EntidadConBorradoLogico {
    protected String nombre;

    /*
     * Empresa duena del rol. En core-identity es solo un identificador sin FK ni navegacion:
     * queda en null en los proyectos mono-empresa. core-multiempresa le agrega la foreign key
     * a empresas por changeset propio y lo usa para el scoping de roles por empresa.
     */
    @Column(name = "id_empresa")
    protected Long idEmpresa;

    // 1 si es el rol asignado por defecto a nuevos usuarios de la empresa, 0 si no
    @Column(name = "por_defecto")
    @JdbcTypeCode(Types.TINYINT)
    protected Integer porDefecto = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "auth_roles_permisos", joinColumns = @JoinColumn(name = "id_rol"), inverseJoinColumns = @JoinColumn(name = "id_permiso"))
    protected List<Permiso> permisos = new ArrayList<>();
}
