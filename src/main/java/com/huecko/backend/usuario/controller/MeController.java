package com.huecko.backend.usuario.controller;

import com.huecko.backend.auth.dto.UsuarioResponse;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.usuario.dto.ActualizarPerfilRequest;
import com.huecko.backend.usuario.service.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil del usuario del token. Nunca recibe un id por parámetro. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final PerfilService perfilService;

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
}
