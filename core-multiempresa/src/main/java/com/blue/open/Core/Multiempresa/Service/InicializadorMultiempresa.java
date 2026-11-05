package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Entidad.Rol;
import com.blue.open.Core.Identity.Puertos.InicializadorDeAutorizacion;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Entidad.UsuarioEmpresa;
import com.blue.open.Core.Multiempresa.Repository.EmpresaRepositorio;
import com.blue.open.Core.Multiempresa.Repository.UsuarioEmpresaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Crea al arrancar la empresa de sistema (roles globales) y la empresa operativa por defecto, y
 * vincula al usuario inicial con el rol SYSADMIN.
 * Los nombres y uuids ya no estan fijos en el Core: se configuran por properties, porque en Core 1.x
 * todo proyecto arrancaba con una empresa llamada "BlueOpenData".
 * Dependencias externas: la base de datos.
 * Recibe: el usuario inicial y el rol SYSADMIN. Responde: el id de la empresa de sistema.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class InicializadorMultiempresa implements InicializadorDeAutorizacion {

    private final EmpresaRepositorio empresaRepositorio;

    private final UsuarioEmpresaRepository usuarioEmpresaRepository;

    @Value("${core.multiempresa.uuid-de-sistema:1}")
    private String uuidEmpresaDeSistema;

    @Value("${core.multiempresa.nombre-de-sistema:Sistema}")
    private String nombreEmpresaDeSistema;

    @Value("${core.multiempresa.uuid-por-defecto:2}")
    private String uuidEmpresaPorDefecto;

    @Value("${core.multiempresa.nombre-por-defecto:Principal}")
    private String nombreEmpresaPorDefecto;

    @Override
    public Long idContextoDeSistema() {
        EmpresaBase sistema = asegurarEmpresa(uuidEmpresaDeSistema, nombreEmpresaDeSistema);
        asegurarEmpresa(uuidEmpresaPorDefecto, nombreEmpresaPorDefecto);
        return sistema.getId();
    }

    private EmpresaBase asegurarEmpresa(String uuid, String nombre) {
        return empresaRepositorio.porUuid(uuid).orElseGet(() -> {
            EmpresaBase nueva = empresaRepositorio.nueva();
            nueva.setNombre(nombre);
            nueva.setStatus(0);
            nueva.setUuid(uuid);
            EmpresaBase guardada = empresaRepositorio.guardar(nueva);
            log.info("Empresa creada: {} (uuid={})", nombre, uuid);
            return guardada;
        });
    }

    @Override
    public void vincularSysadmin(UsuarioBase usuario, Rol rolSysadmin) {
        Long idSistema = rolSysadmin.getIdEmpresa();
        if (idSistema == null) {
            idSistema = idContextoDeSistema();
        }
        if (usuarioEmpresaRepository.buscarPorUsuarioYEmpresa(usuario.getId(), idSistema).isPresent()) {
            return;
        }
        UsuarioEmpresa usuarioEmpresa = new UsuarioEmpresa();
        usuarioEmpresa.setIdUsuario(usuario.getId());
        usuarioEmpresa.setIdEmpresa(idSistema);
        usuarioEmpresa.setRoles(new ArrayList<>(Collections.singletonList(rolSysadmin)));
        usuarioEmpresaRepository.save(usuarioEmpresa);
        log.info("Usuario inicial vinculado a la empresa de sistema con rol SYSADMIN");
    }
}
