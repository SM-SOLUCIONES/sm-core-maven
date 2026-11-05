package com.blue.open.Core.Multiempresa.Entidad;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Superclase de los usuarios en modo multi-empresa (mapped superclass): la entidad de usuario del proyecto la
 * extiende en vez de extender UsuarioBase directamente. Agrega la coleccion de sus relaciones empresa-roles
 * ({@link UsuarioEmpresa}, tabla auth_usuarios_empresas), que la Proyeccion de UsuarioBaseMultiempresaDTO lee con
 * &#64;Coleccion("rolesPorEmpresa").
 * La coleccion es de solo lectura: la relacion se escribe por UsuarioEmpresaRepository (UsuarioBaseMultiempresaService).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class UsuarioBaseMultiempresa extends UsuarioBase {

    // Relaciones empresa-roles del usuario. insertable/updatable = false: nunca escribe id_usuario
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private List<UsuarioEmpresa> rolesPorEmpresa = new ArrayList<>();
}
