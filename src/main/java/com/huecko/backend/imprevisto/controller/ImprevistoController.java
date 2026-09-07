package com.huecko.backend.imprevisto.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.imprevisto.dto.ImprevistoDtos;
import com.huecko.backend.imprevisto.service.ImprevistoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Módulo 5 (HU-13 a HU-16). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ImprevistoController {

    private final ImprevistoService imprevistoService;

    /**
     * RF-15 y RF-16: reportar que no podré ir. La respuesta dice si la ausencia
     * abrió una votación exprés (crítica) o solo se informó (no crítica).
     */
    @PostMapping("/planes/{planId}/imprevistos")
    public ResponseEntity<ImprevistoDtos.ResultadoReporte> reportar(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID planId,
            @Valid @RequestBody ImprevistoDtos.Reportar request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imprevistoService.reportar(yo.id(), planId, request.motivo()));
    }

    /** RF-17: votar. Cambiar de opinión sustituye el voto, de ahí el `PUT`. */
    @PutMapping("/planes/{planId}/votacion-expres/voto")
    public ResponseEntity<ImprevistoDtos.VotacionExpresResponse> votar(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID planId,
            @Valid @RequestBody ImprevistoDtos.VotarExpres request) {

        return ResponseEntity.ok(imprevistoService.votar(yo.id(), planId, request.opcion()));
    }

    /**
     * La votación exprés abierta de este plan, si la hay.
     *
     * Devuelve 204 y no 404 cuando no hay ninguna: no encontrar votación es el
     * caso normal, no un error que la interfaz deba tratar como tal.
     */
    @GetMapping("/planes/{planId}/votacion-expres")
    public ResponseEntity<ImprevistoDtos.VotacionExpresResponse> abierta(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID planId) {

        return imprevistoService.abierta(yo.id(), planId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
