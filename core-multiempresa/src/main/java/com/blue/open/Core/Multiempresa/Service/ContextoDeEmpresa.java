package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Excepciones.CodigoDeErrorMultiempresa;
import com.blue.open.Core.Multiempresa.Repository.EmpresaRepositorio;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Resuelve cual es la empresa de la peticion en curso, a partir del header X-Empresa-Uuid.
 * En Core 1.x esta logica estaba dentro de AuthService, lo que obligaba a todo proyecto a tener
 * el modelo multi-empresa aunque no lo usara. Ahora vive solo en este modulo.
 * Dependencias externas: la base de datos y el contexto HTTP de Spring.
 * Recibe: la request en curso. Responde: el uuid resuelto y la empresa activa del usuario.
 */
@Service
@RequiredArgsConstructor
public class ContextoDeEmpresa {

    public static final String ENCABEZADO_EMPRESA = "X-Empresa-Uuid";

    private final EmpresaRepositorio empresaRepositorio;

    private final UsuarioEmpresaRepository usuarioEmpresaRepository;

    // Lazy: AuthService -> ResolutorDePermisosMultiempresa -> ContextoDeEmpresa -> AuthService es circular
    @Lazy
    private final AuthService authService;

    // false = mono-empresa: se usa siempre la empresa por defecto y no se exige el header
    @Value("${core.multiempresa.habilitada:true}")
    private boolean multiempresaHabilitada;

    // Uuid de la empresa operativa por defecto cuando multiempresa esta apagado
    @Value("${core.multiempresa.uuid-por-defecto:2}")
    private String uuidEmpresaPorDefecto;

    // Uuid de la empresa de sistema, cuyos roles valen en todas las empresas
    @Value("${core.multiempresa.uuid-de-sistema:1}")
    private String uuidEmpresaDeSistema;

    /**
     * Id de la empresa de sistema. Antes estaba hardcodeado como 1 dentro de las consultas JPQL,
     * lo que obligaba a que esa fila existiera con ese id exacto en todos los proyectos.
     * Dependencias externas: la base de datos.
     * Responde null por contrato si la empresa de sistema no existe: los llamadores pasan el id como parametro de
     * consultas JPQL, donde null no coincide con ninguna fila (nadie queda como usuario de sistema).
     */
    public Long getIdEmpresaDeSistema() {
        // orElse(null): el contrato del metodo es devolver null si la empresa de sistema no existe
        return empresaRepositorio.porUuid(uuidEmpresaDeSistema).map(empresa -> empresa.getId()).orElse(null);
    }

    // true si el proyecto corre en modo multiempresa (core.multiempresa.habilitada)
    public boolean isMultiempresaHabilitada() {
        return multiempresaHabilitada;
    }

    // Uuid de la empresa operativa por defecto (core.multiempresa.uuid-por-defecto)
    public String getUuidEmpresaPorDefecto() {
        return uuidEmpresaPorDefecto;
    }

    // Recibe el uuid del header. Si viene vacio y multiempresa esta apagado responde el uuid de la empresa por
    // defecto; si no, responde el mismo uuid recibido (puede ser null)
    public String resolverUuidDeEmpresa(String uuid) {
        if ((uuid == null || uuid.trim().isEmpty()) && !multiempresaHabilitada) {
            return uuidEmpresaPorDefecto;
        }
        return uuid;
    }

    // Uuid de empresa de la peticion en curso (header X-Empresa-Uuid resuelto con resolverUuidDeEmpresa).
    // Dependencias externas: el contexto HTTP de Spring. Responde null si no hay peticion en curso o no hay header
    public String getUuidDeLaRequest() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes();
        if (requestAttributes == null)
            return null;
        return resolverUuidDeEmpresa(requestAttributes.getRequest().getHeader(ENCABEZADO_EMPRESA));
    }

    // Valor crudo del header, sin resolver el default. Lo usa el endpoint /contexto.
    public String getUuidCrudoDeLaRequest() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes();
        if (requestAttributes == null)
            return null;
        return requestAttributes.getRequest().getHeader(ENCABEZADO_EMPRESA);
    }

    // Empresa operativa por defecto. Dependencias externas: la base de datos. Vacio si no existe
    public Optional<EmpresaBase> getEmpresaPorDefecto() {
        return empresaRepositorio.porUuid(uuidEmpresaPorDefecto);
    }

    /**
     * Id de la empresa de la peticion. Se usa para el scoping de permisos.
     * Dependencias externas: la base de datos y el contexto HTTP de Spring.
     * Responde null por contrato (de ahi el sufijo ONull) si no hay header o si el uuid no corresponde a ninguna
     * empresa: los llamadores lo pasan como parametro de consultas JPQL que lo interpretan como "sin empresa".
     */
    public Long getIdEmpresaActualONull() {
        String uuid = getUuidDeLaRequest();
        if (uuid == null || uuid.trim().isEmpty()) {
            return null;
        }
        // orElse(null): el contrato del metodo es devolver null si la empresa no existe
        return empresaRepositorio.porUuid(uuid).map(empresa -> empresa.getId()).orElse(null);
    }

    /**
     * Empresa activa de la sesion. Exige que el usuario pertenezca a ella o a la empresa de sistema.
     */
    public EmpresaBase getEmpresaActualObligatoria() {
        UsuarioBase usuario = authService.autenticar();

        String uuid = getUuidDeLaRequest();
        if (uuid == null || uuid.trim().isEmpty())
            throw new ExcepcionDeApi(CodigoDeErrorMultiempresa.COMPANY_REQUERIDA,
                    "Falta el header " + ENCABEZADO_EMPRESA);

        EmpresaBase empresa = empresaRepositorio.porUuidActiva(uuid)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorMultiempresa.COMPANY_NO_ENCONTRADA,
                        "El UUID suministrado no es válido"));

        if (usuarioEmpresaRepository.existeUsuarioEnEmpresa(empresa.getId(), usuario.getId())) {
            return empresa;
        }

        // Pertenecer a la empresa de sistema habilita cualquier empresa (mismo criterio que ValidadorDeContextoMultiempresa)
        if (usuarioEmpresaRepository.existeUsuarioEnEmpresa(getIdEmpresaDeSistema(), usuario.getId())) {
            return empresa;
        }

        throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "No tienes acceso a esta empresa.");
    }
}
