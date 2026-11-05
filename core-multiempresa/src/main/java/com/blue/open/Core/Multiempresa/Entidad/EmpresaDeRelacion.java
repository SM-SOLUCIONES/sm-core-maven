package com.blue.open.Core.Multiempresa.Entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;

/**
 * Vista de solo lectura de la tabla empresas con los datos de una empresa que se muestran en una relacion
 * (uuid y nombre). Existe porque el Core no puede nombrar la entidad de empresa del proyecto (la que extiende
 * EmpresaBase), y una asociacion JPA necesita una entidad concreta: {@link UsuarioEmpresa#getEmpresa()} apunta aca
 * para que la Proyeccion de UsuarioEmpresaDTO resuelva empresaUuid y empresaNombre con un LEFT JOIN.
 * No se escribe nunca (@Immutable): la empresa se crea y edita por la entidad del proyecto.
 */
@Getter
@Immutable
@Entity
@Table(name = "empresas")
public class EmpresaDeRelacion {

    @Id
    private Long id;

    @Column(name = "uuid", length = 36)
    @JdbcTypeCode(Types.CHAR)
    private String uuid;

    private String nombre;
}
