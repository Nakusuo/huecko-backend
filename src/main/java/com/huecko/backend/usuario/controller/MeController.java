package com.huecko.backend.usuario.controller;

import com.huecko.backend.auth.dto.UsuarioResponse;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.usuario.dto.ActualizarPerfilRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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

    private final UsuarioRepository usuarioRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<UsuarioResponse> perfil(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok(UsuarioResponse.from(cargar(autenticado)));
    }

    @PatchMapping
    @Transactional
    public ResponseEntity<UsuarioResponse> actualizar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody ActualizarPerfilRequest request) {

        Usuario usuario = cargar(autenticado);

        if (request.nombre() != null) {
            usuario.setNombre(request.nombre().trim());
        }

        if (request.email() != null) {
            String email = request.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(usuario.getEmail())
                    && usuarioRepository.existsByEmailIgnoreCase(email)) {
                throw new BusinessException("El correo ya está en uso. Intenta con otro.");
            }
            usuario.setEmail(email);
        }

        return ResponseEntity.ok(UsuarioResponse.from(usuarioRepository.save(usuario)));
    }

    private Usuario cargar(UsuarioAutenticado autenticado) {
        return usuarioRepository.findById(autenticado.id())
                .orElseThrow(() -> new NotFoundException("El usuario del token ya no existe"));
    }
}
