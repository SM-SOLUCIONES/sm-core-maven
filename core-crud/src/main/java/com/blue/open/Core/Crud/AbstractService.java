package com.blue.open.Core.Crud;

import com.blue.open.Core.Crud.Borrado.EstrategiaDeBorrado;
import com.blue.open.Core.Crud.Borrado.SelectorDeBorrado;
import com.blue.open.Core.Crud.Excepciones.CodigoDeErrorCrud;
import com.blue.open.Core.Kernel.DTO.EntidadBaseDTO;
import com.blue.open.Core.Kernel.DTO.Pagina;
import com.blue.open.Core.Kernel.DTO.PedidoDePaginado;
import com.blue.open.Core.Kernel.Entidad.EntidadBase;
import com.blue.open.Core.Kernel.Excepciones.CodigoDeErrorKernel;
import com.blue.open.Core.Kernel.Excepciones.ExcepcionDeApi;
import com.blue.open.Core.Kernel.Proyeccion.Proyector;
import com.blue.open.Core.Kernel.Puertos.ProveedorDeUsuarioActual;
import com.blue.open.Core.Kernel.Repository.EntidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service base del ABM de una entidad (uuid, paginar, guardar, borrar). Lo invocan los controllers escritos con la
 * receta de core/core-crud/docs/receta-controller.md; el permiso fijo de cada endpoint lo declara su @Contrato.
 * Lee siempre con una Proyeccion (el DTO se arma desde la consulta, ver Proyector) y al guardar aplica el DTO sobre
 * la entidad; la respuesta de guardar y borrar es la misma Proyeccion que uuid. No hay mapper por entidad.
 * Dependencias externas: ninguna. Solo conoce tres piezas del kernel: quien es el usuario actual
 * (para la auditoria), como se borra la entidad y el Proyector. No conoce ninguna entidad de usuario ni de empresa.
 * Recibe: uuid, PedidoDePaginado o DTO segun la operacion.
 * Responde: el DTO de la entidad, o la pagina de PAGINADODTO.
 */
@Service
public abstract class AbstractService<T extends EntidadBase, DTO extends EntidadBaseDTO, PAGINADODTO extends EntidadBaseDTO> {

    protected EntidadRepository<T> repository;

    protected Proyector proyector;

    protected ProveedorDeUsuarioActual usuarioActual;

    protected SelectorDeBorrado selectorDeBorrado;

    /*
     * Inyeccion por setter, no por campo ni por constructor.
     * Es una clase base que extiende cada proyecto: con constructor, todo service estaria obligado
     * a declarar uno y encadenar super(...), y agregar una dependencia aca romperia todos los
     * services de todos los proyectos. Ver core/docs/rules/inyeccion-de-dependencias.md.
     */

    // Recibe el repositorio de la entidad concreta
    @Autowired
    public void setRepository(EntidadRepository<T> repository) {
        this.repository = repository;
    }

    // Recibe el motor de Proyeccion: lee el DTO desde la consulta y aplica el DTO sobre la entidad al guardar
    @Autowired
    public void setProyector(Proyector proyector) {
        this.proyector = proyector;
    }

    // Recibe el puerto que resuelve quien es el usuario logeado, para la auditoria
    @Autowired
    public void setUsuarioActual(ProveedorDeUsuarioActual usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    // Recibe el selector que decide si la entidad se borra logica o fisicamente
    @Autowired
    public void setSelectorDeBorrado(SelectorDeBorrado selectorDeBorrado) {
        this.selectorDeBorrado = selectorDeBorrado;
    }

    private final Class<T> claseDeEntidad;
    private final Class<DTO> claseDeDto;
    private final Class<PAGINADODTO> claseDePaginadoDto;

    // Estrategia de borrado que corresponde a esta entidad (logico o fisico, ver core-crud/Borrado)
    protected EstrategiaDeBorrado borrado() {
        return this.selectorDeBorrado.para(this.getClaseDeEntidad());
    }

    // Hooks

    /*
     * Autorizacion que depende de los datos (alcance por empresa, escalada de permisos, alta vs. edicion), con
     * ProveedorDeUsuarioActual.tienePermisos. Nunca para exigir un permiso fijo: eso va en @Contrato del endpoint.
     */
    protected void hookObtenerAuth(String uuid) {
    }

    // Autorizacion segun los datos del pedido de paginado. Por defecto no restringe nada
    protected void hookPaginarAuth(PedidoDePaginado pedido) {
    }

    // Autorizacion segun el DTO a guardar (ej. alta vs. edicion). Por defecto no restringe nada
    protected void hookGuardarAuth(DTO modelo) {
    }

    // Autorizacion segun el registro a borrar. Por defecto no restringe nada
    protected void hookBorrarAuth(String uuid) {
    }

    // Recibe el uuid de un registro y responde su Proyeccion, sin filtrar por status. Vacio si no existe.
    protected Optional<DTO> hookObtenerBuscarEnBase(String uuid) {
        return this.proyector.porUuid(this.getClaseDeEntidad(), uuid, this.getClaseDeDto());
    }

    // Excluye del paginado los registros borrados. En borrado fisico no agrega ningun filtro.
    protected PedidoDePaginado hookPaginarFiltrarStatus(PedidoDePaginado pedido) {
        this.borrado().filtrarVigentes(pedido);
        return pedido;
    }

    // Agrega parametros de busqueda y ordedamiento previos a ejecutar la consulta
    protected PedidoDePaginado hookPaginarAntesDeConsultar(PedidoDePaginado pedido) {
        return pedido;
    }

    // Ejecuta la query para paginar. Responde el DTO de paginado armado desde la consulta, con sus @Coleccion.
    protected Pagina<PAGINADODTO> hookPaginarEjecutarConsulta(PedidoDePaginado pedido) {
        return this.repository.paginar(pedido, this.getClaseDePaginadoDto());
    }

    // Escape para los campos del DTO que no salen de la Proyeccion ni de una @Coleccion (ej: un dato calculado).
    // Debe resolverlos en una consulta para toda la pagina, nunca una por registro.
    protected List<PAGINADODTO> hookPaginarCompletarResultados(List<PAGINADODTO> dtos) {
        return dtos;
    }

    // Permite filtrar los resultados del paginado despues de ser mapeados a DTO
    protected List<PAGINADODTO> hookPaginarFiltrarResultados(List<PAGINADODTO> dtos) {
        return dtos;
    }

    // Arma la Pagina de respuesta con el listado final y los datos de paginacion de la consulta
    protected Pagina<PAGINADODTO> hookPaginarCrearPagina(Pagina<PAGINADODTO> pagina, List<PAGINADODTO> dtos) {
        return new Pagina<>(dtos, pagina.getNumero(), pagina.getTamano(), pagina.getTotalDeElementos());
    }

    // Si el DTO contiene un UUID, busca el registro vigente en la db por UUID. Vacio si es un alta.
    protected Optional<T> hookGuardarBuscarExistente(DTO dto) {
        if (dto.getUuid() != null && !dto.getUuid().isEmpty()) {
            return this.borrado().buscarVigentePorUuid(this.repository, dto.getUuid());
        }
        return Optional.empty();
    }

    // Si hookGuardarBuscarExistente(dto) no encontro ningun registro, esta funcion
    // crea un registro vacio
    protected T hookGuardarCrearEntidadVacia(DTO dto) {
        try {
            return claseDeEntidad.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "Error creando la entidad: " + e.getMessage());
        }
    }

    // Establece el usuario creador del registro
    protected T hookGuardarAsignarUsuarioDeCreacion(T modelo) {
        Long idUsuario = this.usuarioActual.getIdUsuarioActual();
        if (idUsuario != null)
            modelo.setIdUsuarioDeCreacion(idUsuario);
        return modelo;
    }

    /*
     * Aplica el DTO sobre la entidad (nueva o existente): copia los campos simples, asigna las relaciones de
     * @Columna("relacion.uuid") (RELACION_NO_ENCONTRADA si no existe), valida las transiciones de status y llama a
     * AplicaDto.aplicar(dto) si la entidad lo implementa. Se sobreescribe para lo que necesita dependencias.
     */
    protected T hookGuardarAplicarDto(DTO dto, T modelo) {
        if (modelo == null) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO,
                    "Error hookGuardarAplicarDto recibe un null de entrada");
        }
        return this.proyector.aplicar(dto, modelo, CodigoDeErrorCrud.RELACION_NO_ENCONTRADA);
    }

    // Establece el ultimo usuario que edito el registro
    protected T hookGuardarAsignarUsuarioDeActualizacion(T modelo) {
        Long idUsuario = this.usuarioActual.getIdUsuarioActual();
        if (idUsuario != null && modelo != null)
            modelo.setIdUsuarioDeActualizacion(idUsuario);
        return modelo;
    }

    // Guarda el registro nuevo, o el editado, en la base de datos
    protected T hookGuardarEnBase(T modelo) {
        return this.repository.save(modelo);
    }

    // Responde el registro guardado con la misma Proyeccion que uuid
    protected DTO hookGuardarProyectarRespuesta(T modelo) {
        return this.proyectar(modelo.getUuid());
    }

    // Busca el registro vigente a borrar usando su uuid. Vacio si no existe o ya fue borrado.
    protected Optional<T> hookBorrarBuscarPorUuid(String uuid) {
        return this.borrado().buscarVigentePorUuid(this.repository, uuid);
    }

    // Aplica el borrado segun la estrategia de la entidad (status = -1, o DELETE fisico)
    protected T hookBorrarAplicarBorrado(T modelo) {
        return this.borrado().borrar(this.repository, modelo, this.usuarioActual.getIdUsuarioActual());
    }

    // Proyeccion del registro a borrar, o del ya borrado. Vacio si ya no esta en la tabla (borrado fisico)
    protected Optional<DTO> hookBorrarProyectar(String uuid) {
        return this.proyector.porUuid(this.getClaseDeEntidad(), uuid, this.getClaseDeDto());
    }

    // Resuelve T aunque haya clases genericas intermedias (ej. EmpresaService -> EmpresaBaseService<E, DTO>
    // -> AbstractService<E, DTO, DTO>). Hasta 2.0.0 solo miraba la superclase directa y quedaba en null,
    // lo que rompia el alta y el borrado de Empresa y Usuario.
    @SuppressWarnings("unchecked")
    protected AbstractService() {
        ResolvableType tipo = ResolvableType.forClass(ClassUtils.getUserClass(getClass())).as(AbstractService.class);
        this.claseDeEntidad = (Class<T>) tipo.getGeneric(0).resolve();
        this.claseDeDto = (Class<DTO>) tipo.getGeneric(1).resolve();
        this.claseDePaginadoDto = (Class<PAGINADODTO>) tipo.getGeneric(2).resolve();
    }

    // Clase de la entidad del ABM
    protected Class<T> getClaseDeEntidad() {
        return claseDeEntidad;
    }

    // DTO que responden obtener, guardar y borrar: la consulta trae solo las columnas de sus campos
    protected Class<DTO> getClaseDeDto() {
        return claseDeDto;
    }

    // DTO que responde el paginado: la consulta trae solo las columnas de sus campos
    protected Class<PAGINADODTO> getClaseDePaginadoDto() {
        return claseDePaginadoDto;
    }

    // Proyeccion de un registro que tiene que existir (ej: recien guardado)
    protected DTO proyectar(String uuid) {
        return this.proyector.porUuid(this.getClaseDeEntidad(), uuid, this.getClaseDeDto())
                .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                        "AbstractService | proyectar | no se encontro el registro " + uuid));
    }

    /**
     * Busca por UUID
     */
    @Transactional(readOnly = true)
    public DTO obtenerPorUuid(String uuid) {
        try {
            this.hookObtenerAuth(uuid);
            return this.hookObtenerBuscarEnBase(uuid)
                    .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO));
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "Error en obtenerPorUuid: " + e.getMessage());
        }
    }

    /**
     * Paginacion generica
     */
    @Transactional(readOnly = true)
    public Pagina<PAGINADODTO> paginar(PedidoDePaginado pedido) throws Exception {
        try {
            this.hookPaginarAuth(pedido);
            pedido = this.hookPaginarFiltrarStatus(pedido);
            pedido.setClaseDeEntidad(this.getClaseDeEntidad());
            pedido = this.hookPaginarAntesDeConsultar(pedido);
            Pagina<PAGINADODTO> pagina = this.hookPaginarEjecutarConsulta(pedido);
            List<PAGINADODTO> dtos = this.hookPaginarCompletarResultados(pagina.getContenido());
            dtos = this.hookPaginarFiltrarResultados(dtos);
            return this.hookPaginarCrearPagina(pagina, dtos);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "Error en paginar: " + e.getMessage());
        }
    }

    /**
     * Guardar o actualizar
     */
    @Transactional
    public DTO guardar(DTO dto) {
        try {
            this.hookGuardarAuth(dto);
            // Sin uuid, o con uno que no corresponde a un registro vigente, es un alta
            T modelo = this.hookGuardarBuscarExistente(dto)
                    .orElseGet(() -> this.hookGuardarAsignarUsuarioDeCreacion(this.hookGuardarCrearEntidadVacia(dto)));
            modelo = this.hookGuardarAplicarDto(dto, modelo);
            modelo = this.hookGuardarAsignarUsuarioDeActualizacion(modelo);
            modelo = this.hookGuardarEnBase(modelo);
            return hookGuardarProyectarRespuesta(modelo);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "Error en guardar: " + e.getMessage());
        }
    }

    /**
     * Eliminar. Logico (status = -1) o fisico, segun la EstrategiaDeBorrado de la entidad.
     */
    @Transactional
    public DTO borrar(String uuid) {
        try {
            this.hookBorrarAuth(uuid);
            T modelo = this.hookBorrarBuscarPorUuid(uuid)
                    .orElseThrow(() -> new ExcepcionDeApi(CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO,
                            "No existe o ya fue eliminado", "No existe o ya fue eliminado"));
            DTO dto = this.hookBorrarProyectar(uuid).orElseThrow(() -> new ExcepcionDeApi(
                    CodigoDeErrorCrud.REGISTRO_NO_ENCONTRADO, "No existe o ya fue eliminado", "No existe o ya fue eliminado"));
            this.hookBorrarAplicarBorrado(modelo);
            // Borrado logico: el registro sigue en la tabla y responde con su status final (-1)
            return this.hookBorrarProyectar(uuid).orElse(dto);
        } catch (ExcepcionDeApi e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionDeApi(CodigoDeErrorKernel.ERROR_INTERNO, "Error en borrar: " + e.getMessage());
        }
    }
}
