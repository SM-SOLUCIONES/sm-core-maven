package com.blue.open.Core.Multiempresa.Service;

import com.blue.open.Core.Crud.Excepciones.CodigoDeErrorCrud;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Proyeccion.Proyector;
import com.blue.open.Core.Multiempresa.DTO.EmpresaBaseDTO;
import com.blue.open.Core.Multiempresa.Entidad.EmpresaBase;
import com.blue.open.Core.Multiempresa.Repository.EmpresaRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementacion por defecto de {@link AltaDeEmpresa}: instancia la entidad de empresa del proyecto, le aplica el DTO
 * con el Proyector (incluidos los campos propios del proyecto, ej. el CUIT) y la guarda.
 * Un proyecto que necesite logica extra al crear la empresa publica su propio bean AltaDeEmpresa anotado @Primary.
 * Dependencias externas: la base de datos.
 * Recibe: el DTO de empresa. Responde: la empresa persistida.
 */
@Service
@RequiredArgsConstructor
public class AltaDeEmpresaPorProyector implements AltaDeEmpresa {

    private final Proyector proyector;

    private final EmpresaRepositorio empresaRepositorio;

    @Override
    public EmpresaBase crear(EmpresaBaseDTO dto) {
        try {
            EmpresaBase modelo = proyector.aplicar(dto, empresaRepositorio.nueva(),
                    CodigoDeErrorCrud.RELACION_NO_ENCONTRADA);
            return empresaRepositorio.guardar(modelo);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "AltaDeEmpresaPorProyector | crear | " + e.getMessage());
        }
    }
}
