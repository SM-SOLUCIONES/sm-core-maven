package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Auth.RolBaseEnum;
import com.blue.open.Core.Identity.Auth.PermisoBaseEnum;
import com.blue.open.Core.Identity.DTO.RolDTO;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Identity.Excepciones.CodigoDeErrorIdentity;
import com.blue.open.Core.Identity.Service.UsuarioBaseService;
import com.blue.open.Core.Crud.Excepciones.CodigoDeErrorCrud;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Multiempresa.DTO.EmpresaBaseDTO;
import com.blue.open.Core.Multiempresa.DTO.UsuarioBaseMultiempresaDTO;
import com.blue.open.Core.Multiempresa.DTO.RegistroPublicoRequest;
import com.blue.open.Core.Multiempresa.DTO.UsuarioEmpresaDTO;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Entidad.UsuarioEmpresa;
import com.blue.open.Core.Multiempresa.Excepciones.CodigoDeErrorMultiempresa;
import com.blue.open.Core.Multiempresa.Repository.EmpresaRepositorio;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de usuarios en modo multi-empresa. Agrega sobre UsuarioBaseService todo lo que depende de
 * empresas: validacion de que un admin solo gestione usuarios de su empresa, prevencion de
 * escalacion de roles, filtro del paginado por empresa y los registros publicos.
 * Dependencias externas: la base de datos y, para los codigos de verificacion, core-mail.
 * Recibe: el DTO de usuario del proyecto (con rolesPorEmpresa).
 * Responde: ese DTO ya persistido junto con sus relaciones empresa-rol.
 */
@Transactional
public class UsuarioBaseMultiempresaService<U extends UsuarioBase, DTO extends UsuarioBaseMultiempresaDTO>
        extends UsuarioBaseService<U, DTO> {

    protected EmpresaRepositorio empresaRepositorio;

    protected UsuarioEmpresaRepository usuarioEmpresaRepository;

    protected RolRepository rolRepository;

    protected ContextoDeEmpresa contextoDeEmpresa;

    protected AltaDeEmpresa altaDeEmpresa;

    /*
     * Inyeccion por setter, no por campo ni por constructor: es una clase base que extienden
     * otros modulos del Core y los proyectos. Con constructor, toda subclase estaria obligada a
     * declarar uno y encadenar super(...). Ver core/docs/rules/inyeccion-de-dependencias.md.
     */

    // Recibe EmpresaRepositorio
    @Autowired
    public void setEmpresaRepositorio(EmpresaRepositorio empresaRepositorio) {
        this.empresaRepositorio = empresaRepositorio;
    }

    // Recibe UsuarioEmpresaRepository
    @Autowired
    public void setUsuarioEmpresaRepository(UsuarioEmpresaRepository usuarioEmpresaRepository) {
        this.usuarioEmpresaRepository = usuarioEmpresaRepository;
    }

    // Recibe RolRepository
    @Autowired
    public void setRolRepository(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    // Recibe ContextoDeEmpresa
    @Autowired
    public void setContextoDeEmpresa(ContextoDeEmpresa contextoDeEmpresa) {
        this.contextoDeEmpresa = contextoDeEmpresa;
    }

    // Recibe AltaDeEmpresa
    @Autowired
    public void setAltaDeEmpresa(AltaDeEmpresa altaDeEmpresa) {
        this.altaDeEmpresa = altaDeEmpresa;
    }

    @Override
    public void hookGuardarAuth(DTO request) {
        UsuarioBase usuarioActual = authService.getActualObligatorio();
        EmpresaBase empresaDelEncabezado = contextoDeEmpresa.getEmpresaActualObligatoria();

        boolean usuarioEsGlobal = authService.esSysAdmin(usuarioActual);
        boolean usuarioEsAdminLocal = esAdminDeEmpresa(usuarioActual, empresaDelEncabezado.getId());

        boolean esAlta = (request.getUuid() == null);
        PermisoBaseEnum permisoRequerido = esAlta ? PermisoBaseEnum.USUARIO_CREAR : PermisoBaseEnum.USUARIO_EDITAR;
        // Alta y edicion comparten endpoint: el permiso depende del request, no se puede declarar en @Contrato
        if (!authService.tienePermisos(List.of(permisoRequerido))) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "No tienes permiso",
                    "UsuarioBaseMultiempresaService | hookGuardarAuth | falta " + permisoRequerido);
        }

        if (usuarioEsGlobal || request.getRolesPorEmpresa() == null) {
            return;
        }

        Map<String, Rol> rolesPorUuid = rolesDelRequest(request);

        // 1. Si es edicion, el usuario a editar debe pertenecer a la empresa del admin
        if (!esAlta) {
            boolean perteneceALaEmpresa = usuarioRepositorio.porUuid(request.getUuid())
                    .map(objetivo -> usuarioEmpresaRepository.existeUsuarioEnEmpresa(empresaDelEncabezado.getId(),
                            objetivo.getId()))
                    .orElse(false);
            if (!perteneceALaEmpresa) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                        "No puedes editar un usuario que no pertenece a tu empresa.");
            }
        }

        Set<String> permisosDelSolicitante = authService.getPermisosAgregados(usuarioActual);

        for (UsuarioEmpresaDTO relacionDto : request.getRolesPorEmpresa()) {
            // 2. Los roles asignados tienen que ser de la empresa del admin
            if (relacionDto.getEmpresaUuid() != null && !empresaDelEncabezado.getUuid().equals(relacionDto.getEmpresaUuid())) {
                throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                        "No puedes crear/editar usuarios para una empresa distinta a la tuya.");
            }
            if (relacionDto.getRoles() == null) {
                continue;
            }
            for (RolDTO rolDto : relacionDto.getRoles()) {
                Rol rolDeLaBase = rolesPorUuid.get(rolDto.getUuid());
                if (rolDeLaBase == null) {
                    continue;
                }

                if (esRolDeSistema(rolDeLaBase) && !hookPuedeAsignarRol(rolDeLaBase, usuarioActual, empresaDelEncabezado.getUuid())) {
                    throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                            "No tienes permisos para asignar roles de sistema o de administrador global.");
                }

                if (RolBaseEnum.ADMIN.name().equals(rolDeLaBase.getNombre()) && !usuarioEsAdminLocal) {
                    throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO,
                            "No puedes asignar el rol 'ADMIN' si tú mismo no eres administrador de la empresa.");
                }

                if (!hookPuedeAsignarRol(rolDeLaBase, usuarioActual, empresaDelEncabezado.getUuid())) {
                    throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "Rol no permitido",
                            "No tienes permiso para otorgar el rol: " + rolDeLaBase.getNombre());
                }

                // 3. No se puede otorgar un permiso que el solicitante no posee
                for (var permiso : rolDeLaBase.getPermisos()) {
                    if (!permisosDelSolicitante.contains(permiso.getNombre())) {
                        throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "Escalación detectada",
                                "No puedes otorgar el permiso '" + permiso.getNombre() + "' incluido en el rol '"
                                        + rolDeLaBase.getNombre() + "' porque tú mismo no lo posees en la empresa "
                                        + empresaDelEncabezado.getUuid());
                    }
                }
            }
        }
    }

    private Map<String, Rol> rolesDelRequest(DTO request) {
        List<String> uuidsDeRoles = request.getRolesPorEmpresa().stream()
                .filter(relacionDto -> relacionDto.getRoles() != null)
                .flatMap(relacionDto -> relacionDto.getRoles().stream())
                .map(rolDto -> rolDto.getUuid())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (uuidsDeRoles.isEmpty()) {
            return Map.of();
        }
        return rolRepository.buscarPorUuidsConPermisos(uuidsDeRoles).stream()
                .collect(Collectors.toMap(rol -> rol.getUuid(), rol -> rol));
    }

    private boolean esRolDeSistema(Rol rol) {
        Long idSistema = contextoDeEmpresa.getIdEmpresaDeSistema();
        return rol.getIdEmpresa() != null && rol.getIdEmpresa().equals(idSistema);
    }

    // true si el usuario es SYSADMIN o tiene el rol ADMIN en esa empresa o en la de sistema.
    // Dependencias externas: la base de datos. Responde false si el usuario o la empresa son null.
    protected boolean esAdminDeEmpresa(UsuarioBase usuario, Long idEmpresa) {
        if (usuario == null || idEmpresa == null) {
            return false;
        }
        if (authService.esSysAdmin(usuario)) {
            return true;
        }
        return usuarioEmpresaRepository.existeRolParaUsuario(usuario.getId(), idEmpresa,
                contextoDeEmpresa.getIdEmpresaDeSistema(), List.of(RolBaseEnum.ADMIN.name()));
    }

    @Override
    protected PedidoDePaginado hookPaginarAntesDeConsultar(PedidoDePaginado pedido) {
        UsuarioBase usuarioActual = authService.getActualObligatorio();

        if (!authService.esSysAdmin(usuarioActual)) {
            // User no tiene asociacion JPA con UsuarioEmpresa (el Core no conoce la clase concreta): subconsulta por id
            pedido.agregarCondicion(
                    "t.id IN (SELECT ue.idUsuario FROM UsuarioEmpresa ue WHERE ue.idEmpresa = :empresaActual)",
                    Map.of("empresaActual", contextoDeEmpresa.getEmpresaActualObligatoria().getId()));
        }
        return pedido;
    }

    @Override
    protected U hookGuardarEnBase(U modelo) {
        return super.hookGuardarEnBase(modelo);
    }

    @Override
    public DTO guardar(DTO dto) {
        DTO guardado = super.guardar(dto);
        if (dto.getRolesPorEmpresa() != null) {
            String uuid = guardado.getUuid();
            UsuarioBase usuario = usuarioRepositorio.porUuid(uuid)
                    .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                            "El usuario recien guardado no se encontro por uuid: " + uuid));
            sincronizarRoles(usuario, dto.getRolesPorEmpresa());
            guardado = this.proyectar(uuid);
        }
        return guardado;
    }

    /**
     * Reemplaza las relaciones empresa-rol del usuario por las que trae el DTO.
     * Recibe: el usuario ya persistido y la lista de empresas/roles.
     * Responde: nada; deja auth_usuarios_empresas sincronizada.
     */
    protected void sincronizarRoles(UsuarioBase usuario, List<UsuarioEmpresaDTO> relaciones) {
        for (UsuarioEmpresa existente : usuarioEmpresaRepository.findAllByIdUsuarioAndStatus(usuario.getId(), 0)) {
            existente.setStatus(-1);
            usuarioEmpresaRepository.save(existente);
        }
        for (UsuarioEmpresaDTO dto : relaciones) {
            UsuarioEmpresa relacion = relacionDesdeDto(dto);
            relacion.setIdUsuario(usuario.getId());
            relacion.setStatus(0);
            usuarioEmpresaRepository.save(relacion);
        }
    }

    /*
     * Arma una relacion empresa-roles nueva a partir del DTO. La empresa sale de su uuid (o es la del contexto si el
     * DTO no la trae) y los roles se buscan por uuid o, si no lo traen, por nombre dentro de esa empresa. Una empresa
     * o un rol que no existe se ignora (mismo comportamiento que antes de 3.0.2). No usa Proyector.aplicar: la
     * empresa y los roles no son relaciones @ManyToOne por uuid (se buscan por uuid o por nombre, con la empresa
     * del contexto por defecto).
     * Dependencias externas: la base de datos.
     */
    private UsuarioEmpresa relacionDesdeDto(UsuarioEmpresaDTO dto) {
        UsuarioEmpresa relacion = new UsuarioEmpresa();
        relacion.aplicarCamposBase(dto);
        Optional<EmpresaBase> empresa = dto.getEmpresaUuid() != null
                ? empresaRepositorio.porUuidActiva(dto.getEmpresaUuid())
                : Optional.of(contextoDeEmpresa.getEmpresaActualObligatoria());
        if (empresa.isEmpty()) {
            return relacion;
        }
        Long idEmpresa = empresa.get().getId();
        relacion.setIdEmpresa(idEmpresa);
        if (dto.getRoles() != null) {
            for (RolDTO rolDto : dto.getRoles()) {
                Optional<Rol> rol = Optional.empty();
                if (rolDto.getUuid() != null) {
                    rol = rolRepository.buscarPorUuid(rolDto.getUuid());
                } else if (rolDto.getNombre() != null) {
                    rol = rolRepository.findFirstByNombreAndIdEmpresa(rolDto.getNombre(), idEmpresa);
                }
                rol.ifPresent(relacion.getRoles()::add);
            }
        }
        return relacion;
    }

    /**
     * Alta publica de usuario + empresa. Crea la empresa, asigna el rol ADMIN al usuario y lo deja
     * en estado pendiente de verificacion (-2) hasta que valide el codigo enviado por email.
     */
    public DTO registroPublico(RegistroPublicoRequest<DTO, ? extends EmpresaBaseDTO> request) {
        DTO usuarioDTO = request.getUsuario();
        EmpresaBaseDTO empresaDTO = request.getEmpresa();

        if (empresaDTO == null) {
            throw new ExcepcionDeApi(CodigoDeErrorIdentity.DATOS_INCOMPLETOS,
                    "La compañía no puede ser nula al registrarse. Proporcione los datos de la compañía.");
        }

        hookRegistroPublicoAntesDeCrearEmpresa(usuarioDTO, empresaDTO);

        EmpresaBase nuevaEmpresa = altaDeEmpresa.crear(empresaDTO);

        Rol rolAdmin = rolRepository.findFirstByNombre(RolBaseEnum.ADMIN.name())
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorMultiempresa.CONFIGURACION_DE_SISTEMA_INCOMPLETA,
                        "Rol ADMIN no encontrado. Contacte al administrador del sistema."));

        return altaPendiente(usuarioDTO, nuevaEmpresa, rolAdmin);
    }

    /**
     * Vincula al usuario logeado a una empresa con el rol por defecto de esa empresa.
     * Requiere sesion y solo permite vincularse a si mismo: hasta 2.0.0 era anonimo y aceptaba
     * cualquier usuarioUuid, lo que permitia darle a cualquier usuario el rol por defecto de la
     * empresa de sistema (y con eso permisos globales).
     * Si el usuario ya pertenece a la empresa no crea una relacion nueva.
     */
    public DTO vincular(String empresaUuid, String usuarioUuid) {
        UsuarioBase actual = authService.getActualObligatorio();
        if (!actual.getUuid().equals(usuarioUuid)) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.SIN_PERMISO, "No permitido",
                    "Solo puedes vincular tu propio usuario a una empresa.");
        }

        EmpresaBase empresa = empresaDeAltaPublica(empresaUuid);

        UsuarioBase usuario = usuarioRepositorio.porUuid(usuarioUuid)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                        "Usuario no encontrado", "User not found for uuid: " + usuarioUuid));
        if (usuarioEmpresaRepository.existeUsuarioEnEmpresa(empresa.getId(), usuario.getId())) {
            return this.proyectar(usuarioUuid);
        }

        Rol rol = rolPorDefecto(empresa);
        UsuarioEmpresa relacion = new UsuarioEmpresa();
        relacion.setIdUsuario(usuario.getId());
        relacion.setIdEmpresa(empresa.getId());
        relacion.setRoles(List.of(rol));
        usuarioEmpresaRepository.save(relacion);

        return this.proyectar(usuarioUuid);
    }

    /**
     * Alta publica de un usuario dentro de una empresa existente, con su rol por defecto.
     */
    public DTO registroPublicoDeCliente(String empresaUuid, DTO usuarioDTO) {
        EmpresaBase empresa = empresaDeAltaPublica(empresaUuid);
        return altaPendiente(usuarioDTO, empresa, rolPorDefecto(empresa));
    }

    private EmpresaBase empresaDeAltaPublica(String empresaUuid) {
        String uuid = contextoDeEmpresa.resolverUuidDeEmpresa(empresaUuid);
        EmpresaBase empresa = empresaRepositorio.porUuidActiva(uuid)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorMultiempresa.COMPANY_NO_ENCONTRADA,
                        "Empresa no encontrada", "Company not found for uuid: " + uuid));
        // Los roles de la empresa de sistema valen en todas: nunca se entra a ella por un alta publica.
        // No es SIN_PERMISO: ningun rol lo habilita y registroPublicoDeCliente es anonimo
        if (empresa.getId().equals(contextoDeEmpresa.getIdEmpresaDeSistema())) {
            throw new ExcepcionDeApi(CodigoDeErrorMultiempresa.EMPRESA_DE_SISTEMA_NO_PERMITIDA,
                    "UsuarioBaseMultiempresaService | empresaDeAltaPublica | empresaUuid: " + uuid);
        }
        return empresa;
    }

    private Rol rolPorDefecto(EmpresaBase empresa) {
        return rolRepository.findFirstByIdEmpresaAndPorDefecto(empresa.getId(), 1)
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorMultiempresa.CONFIGURACION_INCOMPLETA,
                        "La empresa no tiene un rol por defecto configurado"));
    }

    /**
     * Crea el usuario en estado pendiente (-2), le asigna el rol indicado y le manda el codigo.
     * No pasa por guardar para saltear la validacion de permisos: es un alta publica.
     */
    private DTO altaPendiente(DTO usuarioDTO, EmpresaBase empresa, Rol rol) {
        U usuario = this.proyector.aplicar(usuarioDTO, this.hookGuardarCrearEntidadVacia(usuarioDTO),
                CodigoDeErrorCrud.RELACION_NO_ENCONTRADA);
        // La contrasena no pasa por la copia generica (@NoProyectable): se guarda hasheada
        if (usuarioDTO.getContrasena() != null) {
            usuario.setContrasena(passwordEncoder.encode(usuarioDTO.getContrasena()));
        }
        usuario.setStatus(-2);
        generarCodigoDeVerificacion(usuario);
        usuario = hookRegistroPublicoAntesDeGuardar(usuario, empresa);
        usuario = this.repository.save(usuario);

        UsuarioEmpresa relacion = new UsuarioEmpresa();
        relacion.setIdUsuario(usuario.getId());
        relacion.setIdEmpresa(empresa.getId());
        relacion.setRoles(List.of(rol));
        usuarioEmpresaRepository.save(relacion);

        enviarMailDeVerificacion(usuario);

        return this.proyectar(usuario.getUuid());
    }

    /**
     * Define si el usuario actual puede asignar un rol concreto.
     * Por defecto el Core prohibe asignar roles de la empresa de sistema.
     */
    protected boolean hookPuedeAsignarRol(Rol rol, UsuarioBase usuario, String empresaUuid) {
        return !esRolDeSistema(rol);
    }

    /**
     * Hook previo al guardado final de un usuario en un registro publico.
     * Util para vincular entidades creadas junto con el usuario.
     */
    protected U hookRegistroPublicoAntesDeGuardar(U usuario, EmpresaBase empresa) {
        return usuario;
    }

    /**
     * Hook previo a la creacion de la empresa en un registro publico.
     * Util para validar datos del usuario (email, nombre de usuario) o de la empresa (CUIT).
     */
    protected void hookRegistroPublicoAntesDeCrearEmpresa(DTO usuarioDTO, EmpresaBaseDTO empresaDTO) {
    }
}
