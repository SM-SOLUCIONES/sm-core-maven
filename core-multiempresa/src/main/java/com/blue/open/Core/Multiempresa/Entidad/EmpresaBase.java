package com.blue.open.Core.Multiempresa.Entidad;

import com.blue.open.Core.Kernel.Entidad.EntidadConBorradoLogico;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclase base de las empresas (mapped superclass): el proyecto la extiende con su @Entity
 * sobre la tabla empresas y le agrega las columnas que necesite (por ejemplo el CUIT).
 * La relacion con usuarios y roles vive en {@link UsuarioEmpresa}, que referencia a la empresa
 * por id y no por asociacion, para no obligar al Core a conocer la clase concreta del proyecto.
 */
@Setter
@Getter
@MappedSuperclass
public abstract class EmpresaBase extends EntidadConBorradoLogico {

    private String nombre, ruta, descripcion, codigo;
    // Saldo de la empresa, uso especifico del negocio (no forma parte del framework base)
    private Double saldo;
}
