package com.blue.open.Core.Protocolo.ApiKey;

import com.blue.open.Core.Identity.Entidad.UsuarioBase;
import com.blue.open.Core.Identity.Service.AuthService;
import com.blue.open.Core.Kernel.Contrato.Contrato;
import com.blue.open.Core.Kernel.Contrato.Descripcion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de gestion de tokens de acceso personales del usuario logeado.
 * Viven en este modulo: un proyecto que no declare core-protocolo-apikey no los publica ni crea
 * la tabla auth_access_tokens.
 * Dependencias externas: la base de datos.
 * Recibe: el nombre del token a crear o el uuid del token a borrar.
 * Responde: el token creado (unica vez que se expone el codigo), o la lista de tokens del usuario.
 */
@RestController
@RequestMapping("${core.protocolo.apikey.ruta:usuario}")
@RequiredArgsConstructor
public class AccessTokenController {

    private final AuthService authService;

    private final AuthTokenService authTokenService;

    // Crea un token de acceso personal para el usuario de la sesion. Responde el token (unica vez que se ve el codigo)
    @PostMapping("crear-token")
    @Contrato(casoDeUso = "CU-TOK-01", resumen = "Crea un token de acceso personal para el usuario de la sesión (requiere sesión)",
            permiso = Contrato.AUTENTICADO)
    public ResponseEntity<AccessTokenDTO> crearToken(@Valid @RequestBody AccessTokenDTO dto) {
        UsuarioBase usuario = authService.autenticar();
        return ResponseEntity.ok(authTokenService.proyectar(usuario,
                authTokenService.crearAccessToken(usuario, dto.getNombre()).getUuid()));
    }

    // Elimina un token de acceso propio. Responde el token eliminado
    @DeleteMapping("borrar-token/{uuid}")
    @Contrato(casoDeUso = "CU-TOK-03", resumen = "Elimina un token de acceso propio (requiere sesión)",
            permiso = Contrato.AUTENTICADO)
    public ResponseEntity<AccessTokenDTO> borrarToken(
            @Descripcion("Uuid del token de acceso propio a eliminar") @PathVariable String uuid) {
        UsuarioBase usuario = authService.autenticar();
        return ResponseEntity.ok(authTokenService.proyectar(usuario,
                authTokenService.borrarAccessToken(usuario, uuid).getUuid()));
    }

    // Lista los tokens de acceso vigentes del usuario de la sesion
    @GetMapping("tokens")
    @Contrato(casoDeUso = "CU-TOK-02", resumen = "Lista los tokens de acceso vigentes del usuario de la sesión (requiere sesión)",
            permiso = Contrato.AUTENTICADO)
    public ResponseEntity<List<AccessTokenDTO>> listarTokens() {
        UsuarioBase usuario = authService.autenticar();
        return ResponseEntity.ok(authTokenService.proyectarVigentes(usuario));
    }
}
