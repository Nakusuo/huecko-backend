package com.huecko.backend.llegada.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.llegada.dto.LlegadasResponse;
import com.huecko.backend.llegada.service.LlegadaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** «Llegué» de un plan: verlo, marcarlo (PUT) y deshacerlo (DELETE). */
@RestController
@RequestMapping("/api/planes/{planId}/llegadas")
@RequiredArgsConstructor
public class LlegadaController {

    private final LlegadaService llegadaService;

    @GetMapping
    public ResponseEntity<LlegadasResponse> ver(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID planId) {
        return ResponseEntity.ok(llegadaService.ver(yo.id(), planId));
    }

    @PutMapping
    public ResponseEntity<LlegadasResponse> marcar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID planId) {
        return ResponseEntity.ok(llegadaService.marcar(yo.id(), planId));
    }

    @DeleteMapping
    public ResponseEntity<LlegadasResponse> quitar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID planId) {
        return ResponseEntity.ok(llegadaService.quitar(yo.id(), planId));
    }
}
