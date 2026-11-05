package com.blue.open.Core.Kernel.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Condicion JPQL que agrega el backend al WHERE del paginado (nunca llega desde el front: PedidoDePaginado
 * la marca @JsonIgnore). Sirve para lo que un FiltroDePaginado no puede expresar, por ejemplo restringir
 * por una tabla sin asociacion JPA:
 * <pre>
 * pedido.agregarCondicion("t.id IN (SELECT ue.idUsuario FROM UsuarioEmpresa ue WHERE ue.idEmpresa = :empresa)",
 *         Map.of("empresa", idEmpresa));
 * </pre>
 * jpql usa el alias "t" para la entidad paginada. Los parametros no pueden empezar con "pg_" (reservado).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CondicionDePaginado {
    private String jpql;
    private Map<String, Object> parametros = new HashMap<>();
}
