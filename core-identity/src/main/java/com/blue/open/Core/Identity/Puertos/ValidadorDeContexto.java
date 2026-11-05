package com.blue.open.Core.Identity.Puertos;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;

/**
 * Validacion extra que corre despues de identificar al usuario, en cada llamada autenticada.
 * En core-identity no hace nada; core-multiempresa la usa para exigir que el usuario pertenezca
 * a la empresa del header X-Empresa-Uuid. Antes esa regla estaba dentro de AuthService, lo que
 * obligaba a todo proyecto a tener el modelo multi-empresa.
 * Recibe: el usuario ya autenticado. Responde: nada; lanza 403 si el contexto no es valido.
 */
public interface ValidadorDeContexto {
    void validar(UsuarioBase usuario);
}
