package com.huecko.backend.usuario.controller;

import com.huecko.backend.auth.dto.UsuarioResponse;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.usuario.dto.ActualizarPerfilRequest;
import com.huecko.backend.usuario.dto.LogrosResponse;
import com.huecko.backend.usuario.dto.PerfilSocialRequest;
import com.huecko.backend.usuario.service.FotoPerfilService;
import com.huecko.backend.usuario.service.PerfilPublicoService;
import com.huecko.backend.usuario.service.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Perfil del usuario del token. Nunca recibe un id por parámetro. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    /** El frontend manda la foto ya reducida (unos 30 KB); 2 MB es margen de sobra. */
    private static final long MAX_BYTES_FOTO = 2L * 1024 * 1024;

    private final PerfilService perfilService;
    private final FotoPerfilService fotoPerfilService;
    private final PerfilPublicoService perfilPublicoService;

    @GetMapping
    public ResponseEntity<UsuarioResponse> perfil(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok(UsuarioResponse.from(perfilService.cargar(autenticado.id())));
    }

    @PatchMapping
    public ResponseEntity<UsuarioResponse> actualizar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody ActualizarPerfilRequest request) {
        return ResponseEntity.ok(UsuarioResponse.from(perfilService.actualizar(autenticado.id(), request)));
    }

    /** Carrera, ciclo, frase e intereses: lo que ve el resto de tus grupos en tu tarjeta. */
    @PutMapping("/perfil")
    public ResponseEntity<UsuarioResponse> perfilSocial(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody PerfilSocialRequest request) {
        return ResponseEntity.ok(UsuarioResponse.from(perfilService.actualizarSocial(autenticado.id(), request)));
    }

    /** Multipart, campo «foto». El servidor la rehace siempre: ver Imagenes. */
    @PutMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UsuarioResponse> subirFoto(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @RequestPart("foto") MultipartFile foto) {
        if (foto.getSize() > MAX_BYTES_FOTO) {
            throw new BusinessException("La foto supera los 2 MB.");
        }
        byte[] bytes;
        try {
            bytes = foto.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer la foto. Vuelve a intentarlo.");
        }
        return ResponseEntity.ok(UsuarioResponse.from(fotoPerfilService.guardar(autenticado.id(), bytes)));
    }

    @DeleteMapping("/foto")
    public ResponseEntity<UsuarioResponse> quitarFoto(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok(UsuarioResponse.from(fotoPerfilService.quitar(autenticado.id())));
    }

    @GetMapping("/logros")
    public ResponseEntity<LogrosResponse> logros(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok(perfilPublicoService.logros(autenticado.id()));
    }
}
