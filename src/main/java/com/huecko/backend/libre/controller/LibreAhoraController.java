package com.huecko.backend.libre.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.libre.dto.LibreAhoraResponse;
import com.huecko.backend.libre.dto.MarcarLibreRequest;
import com.huecko.backend.libre.service.LibreAhoraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** «¿Quién está libre ahora?» y tu propio aviso de «Estoy libre». */
@RestController
@RequestMapping("/api/me/libre-ahora")
@RequiredArgsConstructor
public class LibreAhoraController {

    private final LibreAhoraService libreAhoraService;

    @GetMapping
    public ResponseEntity<LibreAhoraResponse> estado(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return ResponseEntity.ok(libreAhoraService.estado(yo.id()));
    }

    @PutMapping
    public ResponseEntity<LibreAhoraResponse> marcar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                     @Valid @RequestBody MarcarLibreRequest request) {
        return ResponseEntity.ok(libreAhoraService.marcar(yo.id(), request));
    }

    @DeleteMapping
    public ResponseEntity<LibreAhoraResponse> quitar(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return ResponseEntity.ok(libreAhoraService.quitar(yo.id()));
    }
}
