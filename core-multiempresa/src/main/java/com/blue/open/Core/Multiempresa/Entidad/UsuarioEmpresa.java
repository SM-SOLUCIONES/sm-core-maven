package com.blue.open.Core.Multiempresa.Entidad;

import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabla intermedia que vincula un usuario con una empresa y sus {@link Rol} en esa empresa.
 * Es la base del RBAC multi-empresa: un mismo usuario puede pertenecer a varias empresas con
 * roles distintos en cada una.
 * Usuario y empresa se escriben por id (id_usuario, id_empresa), no por asociacion JPA, porque
 * ambas entidades concretas las define el proyecto y el Core no puede nombrarlas. Para leer la
 * empresa en una Proyeccion existe la asociacion de solo lectura empresa ({@link EmpresaDeRelacion}),
 * y el usuario la ve como coleccion en {@link UsuarioBaseMultiempresa}.
 */
@Setter
@Getter
@Table(name = "auth_usuarios_empresas")
@Entity()
public class UsuarioEmpresa extends EntidadConBorradoLogico {

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "id_empresa")
    private Long idEmpresa;

    // Empresa de la relacion para leer su uuid y nombre (Proyeccion). Solo lectura: la columna la escribe idEmpresa
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_empresa", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private EmpresaDeRelacion empresa;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "auth_usuarios_empresas_roles", joinColumns = @JoinColumn(name = "id_usuario_empresa"), inverseJoinColumns = @JoinColumn(name = "id_rol"))
    private List<Rol> roles = new ArrayList<>();
}
