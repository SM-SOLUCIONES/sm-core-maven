package com.blue.open.Core.Identity.Configuracion;

import com.blue.open.Core.Identity.Auth.IPermiso;
import com.blue.open.Core.Identity.Auth.IRol;
import com.blue.open.Core.Identity.Puertos.CatalogoDeAutorizacion;
import com.blue.open.Core.Identity.Puertos.ResolutorDePermisos;
import com.blue.open.Core.Identity.Puertos.InicializadorDeAutorizacion;
import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Puertos.PoliticaDeAccesoExterno;
import com.blue.open.Core.Identity.Puertos.SincronizadorDeRolesExternos;
import com.blue.open.Core.Identity.Puertos.ValidadorDeContexto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Valores por defecto de core-identity. Todos son @ConditionalOnMissingBean: el modulo de scoping
 * (core-monoempresa o core-multiempresa) o el propio proyecto los reemplazan publicando su bean.
 * Sin ninguno de esos modulos, el sistema funciona con usuarios sin roles.
 */
@Configuration
public class IdentityConfiguracion {

    // Codificador de contrasenas por defecto: BCrypt
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Sin modulo de scoping no hay roles: el usuario se autentica pero no tiene permisos
    @Bean
    @ConditionalOnMissingBean(ResolutorDePermisos.class)
    public ResolutorDePermisos resolutorSinRoles() {
        return new ResolutorDePermisos() {
            @Override
            public boolean tieneAlgunRolOPermiso(Long idUsuario, List<String> roles, List<String> permisos) {
                return false;
            }

            @Override
            public Set<String> permisosDe(Long idUsuario) {
                return Collections.emptySet();
            }

            @Override
            public boolean esSysAdmin(Long idUsuario) {
                return false;
            }
        };
    }

    // Sin core-multiempresa no hay contexto extra que validar en cada request
    @Bean
    @ConditionalOnMissingBean(ValidadorDeContexto.class)
    public ValidadorDeContexto validadorDeContextoVacio() {
        return usuario -> {
        };
    }

    // Sin modulo de scoping no hay donde escribir los roles que declara un SSO externo
    @Bean
    @ConditionalOnMissingBean(SincronizadorDeRolesExternos.class)
    public SincronizadorDeRolesExternos sincronizadorSinScoping() {
        return (usuario, contextoExterno, nombresDeRoles) -> {
        };
    }

    // Sin politica propia del proyecto, toda identidad externa valida puede entrar (comportamiento hasta 3.3.2)
    @Bean
    @ConditionalOnMissingBean(PoliticaDeAccesoExterno.class)
    public PoliticaDeAccesoExterno politicaDeAccesoExternoPermitirTodo() {
        return (protocolo, identificador, usuarioVigente) -> {
        };
    }

    // Sin modulo de scoping los roles quedan sin contexto y el usuario inicial sin vincular
    @Bean
    @ConditionalOnMissingBean(InicializadorDeAutorizacion.class)
    public InicializadorDeAutorizacion inicializadorSinScoping() {
        return new InicializadorDeAutorizacion() {
            @Override
            public Long idContextoDeSistema() {
                return null;
            }

            @Override
            public void vincularSysadmin(UsuarioBase usuario, Rol rolSysadmin) {
                // Sin core-monoempresa ni core-multiempresa no hay donde guardar la relacion
            }
        };
    }

    // Un proyecto sin roles ni permisos propios no necesita declarar el catalogo
    @Bean
    @ConditionalOnMissingBean(CatalogoDeAutorizacion.class)
    public CatalogoDeAutorizacion catalogoVacio() {
        return new CatalogoDeAutorizacion() {
            @Override
            public IRol[] getRoles() {
                return new IRol[0];
            }

            @Override
            public IPermiso[] getPermisos() {
                return new IPermiso[0];
            }
        };
    }
}
