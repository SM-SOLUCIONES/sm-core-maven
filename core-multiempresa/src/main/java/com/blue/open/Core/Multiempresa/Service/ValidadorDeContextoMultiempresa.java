package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Puertos.ValidadorDeContexto;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.Excepciones.CodigoDeErrorMultiempresa;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Exige, en cada llamada autenticada, que el usuario pertenezca a la empresa del header
 * X-Empresa-Uuid (los sysadmin quedan exentos). Es la regla que antes vivia dentro de
 * AuthService.valid() (hoy AuthService.autenticar()) y que obligaba a todo proyecto a tener el modelo multi-empresa.
 * Dependencias externas: la base de datos y el contexto HTTP.
 * Recibe: el usuario autenticado. Responde: nada; lanza 403 si no pertenece a la empresa.
 */
@Primary
@Service
@RequiredArgsConstructor
public class ValidadorDeContextoMultiempresa implements ValidadorDeContexto {

    private final UsuarioEmpresaRepository usuarioEmpresaRepository;

    private final ContextoDeEmpresa contextoDeEmpresa;

    @Override
    public void validar(UsuarioBase usuario) {
        Long idEmpresa = contextoDeEmpresa.getIdEmpresaActualONull();
        if (idEmpresa == null) {
            return;
        }
        if (usuarioEmpresaRepository.existeUsuarioEnEmpresa(idEmpresa, usuario.getId())) {
            return;
        }
        // Pertenecer a la empresa de sistema habilita cualquier empresa: sus roles valen en todas
        if (usuarioEmpresaRepository.existeUsuarioEnEmpresa(contextoDeEmpresa.getIdEmpresaDeSistema(), usuario.getId())) {
            return;
        }
        throw new ExcepcionDeApi(CodigoDeErrorMultiempresa.EMPRESA_NO_PERMITIDA,
                "El usuario no pertenece a la compañía o no está validado");
    }
}
