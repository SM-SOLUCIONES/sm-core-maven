package com.blue.open.Core.Identity.Service;

import com.blue.open.Core.Identity.Auth.PermisoBaseEnum;
import com.blue.open.Core.Identity.Auth.RolBaseEnum;
import com.blue.open.Core.Identity.Auth.IPermiso;
import com.blue.open.Core.Identity.Auth.IRol;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Permiso;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Puertos.CatalogoDeAutorizacion;
import com.blue.open.Core.Identity.Puertos.InicializadorDeAutorizacion;
import com.blue.open.Core.Identity.Repository.PermisoRepository;
import com.blue.open.Core.Identity.Repository.RolRepository;
import com.blue.open.Core.Identity.Repository.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sincroniza contra la base los permisos y roles declarados en codigo, y crea el usuario inicial.
 * Los roles y permisos del proyecto llegan por el puerto CatalogoDeAutorizacion: ya no se buscan
 * por reflexion en el paquete com.blue.open.Code.Auth, que obligaba a esa estructura exacta.
 * Dependencias externas: la base de datos.
 * Recibe: nada (corre al arrancar). Responde: nada; deja la base lista para operar.
 */
@Slf4j
@Order(10)
@Service
@RequiredArgsConstructor
public class InicializadorDeBaseService {

    private final PermisoRepository permisoRepository;

    private final RolRepository rolRepository;

    private final UsuarioRepositorio usuarioRepositorio;

    private final PasswordEncoder passwordEncoder;

    private final CatalogoDeAutorizacion catalogoDeAutorizacion;

    private final InicializadorDeAutorizacion inicializadorDeAutorizacion;

    // Usuario inicial. Sin valor por defecto: las credenciales viven en el properties del proyecto.
    // Si falta alguna de las dos, no se crea el usuario inicial.
    @Value("${core.identity.usuario-inicial.nombre-de-usuario:#{null}}")
    private String nombreDeUsuarioInicial;

    @Value("${core.identity.usuario-inicial.contrasena:#{null}}")
    private String contrasenaInicial;

    // Al terminar el arranque sincroniza con la base los permisos y roles declarados en codigo (Core y
    // proyecto) y crea el usuario inicial si esta configurado. Dependencias externas: la base de datos.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializar() {
        log.info("Iniciando inicialización de Base de Datos (Permisos, Roles, Usuario inicial)...");

        sincronizarPermisos(PermisoBaseEnum.values());
        sincronizarPermisos(catalogoDeAutorizacion.getPermisos());

        Long idContexto = inicializadorDeAutorizacion.idContextoDeSistema();

        sincronizarRoles(idContexto, RolBaseEnum.values());
        sincronizarRoles(idContexto, catalogoDeAutorizacion.getRoles());

        sincronizarUsuarioInicial(idContexto);

        log.info("Inicialización de Base de Datos finalizada.");
    }

    private void sincronizarPermisos(IPermiso[] permisos) {
        if (permisos == null) {
            return;
        }
        for (IPermiso permisoDelEnum : permisos) {
            if (permisoRepository.findFirstByNombre(permisoDelEnum.getNombre()).isEmpty()) {
                Permiso permiso = new Permiso();
                permiso.setNombre(permisoDelEnum.getNombre());
                permisoRepository.save(permiso);
                log.info("Permiso sincronizado: {}", permisoDelEnum.getNombre());
            }
        }
    }

    private void sincronizarRoles(Long idContexto, IRol[] roles) {
        if (roles == null) {
            return;
        }
        for (IRol rolDelEnum : roles) {
            Rol rol = rolRepository.findFirstByNombreAndIdEmpresa(rolDelEnum.name(), idContexto)
                    .orElseGet(() -> {
                        Rol nuevo = new Rol();
                        nuevo.setNombre(rolDelEnum.name());
                        nuevo.setIdEmpresa(idContexto);
                        log.info("Creando rol: {}", rolDelEnum.name());
                        return nuevo;
                    });

            Set<? extends IPermiso> permisosDelEnum = rolDelEnum.getPermisos();
            List<Permiso> permisosDeLaBase = new ArrayList<>();

            if (permisosDelEnum != null) {
                Set<String> nombresDePermisosVistos = new LinkedHashSet<>();
                for (IPermiso permisoDelEnum : permisosDelEnum) {
                    if (!nombresDePermisosVistos.add(permisoDelEnum.getNombre())) {
                        continue;
                    }
                    permisoRepository.findFirstByNombre(permisoDelEnum.getNombre()).ifPresentOrElse(
                            permisosDeLaBase::add,
                            () -> log.warn("Permiso {} no encontrado para el rol {}", permisoDelEnum.getNombre(),
                                    rolDelEnum.name()));
                }
            }

            rol.setPermisos(permisosDeLaBase);
            rolRepository.save(rol);
            log.info("Rol sincronizado: {}", rolDelEnum.name());
        }
    }

    private void sincronizarUsuarioInicial(Long idContexto) {
        if (nombreDeUsuarioInicial == null || nombreDeUsuarioInicial.isBlank() || contrasenaInicial == null || contrasenaInicial.isBlank()) {
            log.warn("core.identity.usuario-inicial.nombre-de-usuario/contrasena sin configurar: no se crea el usuario inicial.");
            return;
        }
        Optional<UsuarioBase> existente = usuarioRepositorio.porNombreDeUsuarioActivo(nombreDeUsuarioInicial);
        UsuarioBase usuario;
        if (existente.isPresent()) {
            usuario = existente.get();
        } else {
            usuario = usuarioRepositorio.nuevo();
            usuario.setNombre("SysAdmin");
            usuario.setNombreDeUsuario(nombreDeUsuarioInicial);
            usuario.setContrasena(passwordEncoder.encode(contrasenaInicial));
            usuario.setStatus(0);
            usuario = usuarioRepositorio.guardar(usuario);
            log.info("Usuario inicial creado: {}", nombreDeUsuarioInicial);
        }

        Optional<Rol> rolSysadmin = rolRepository.findFirstByNombreAndIdEmpresa(RolBaseEnum.SYSADMIN.name(),
                idContexto);
        if (rolSysadmin.isEmpty()) {
            log.error("FALTA CRÍTICA: El rol SYSADMIN no se encontró tras la sincronización.");
            return;
        }
        inicializadorDeAutorizacion.vincularSysadmin(usuario, rolSysadmin.get());
    }
}
