package com.huecko.backend.retraso.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.retraso.dto.RetrasoRequests;
import com.huecko.backend.retraso.dto.RetrasoResponse;
import com.huecko.backend.retraso.service.RetrasoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Módulo 4 (HU-11, HU-12).
 *
 * Cuelga del plan y no del grupo: un retraso siempre lo es respecto de un
 * evento concreto, y el plan ya sabe a qué grupo pertenece.
 *
 * Es `PUT` y no `POST` porque cada persona tiene como mucho un retraso por
 * plan: repetir la llamada corrige la estimación en vez de acumular avisos.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RetrasoController {

    private final RetrasoService retrasoService;

    /** RF-12 y RF-13. */
    @PutMapping("/planes/{planId}/retrasos/mio")
    public ResponseEntity<RetrasoResponse> reportar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                    @PathVariable UUID planId,
                                                    @Valid @RequestBody RetrasoRequests.Reportar request) {
        return ResponseEntity.ok(
                retrasoService.reportar(yo.id(), planId, request.minutosEstimados()));
    }

    /** RF-14: estado de puntualidad de cada asistente. */
    @GetMapping("/planes/{planId}/retrasos")
    public ResponseEntity<List<RetrasoResponse>> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                        @PathVariable UUID planId) {
        return ResponseEntity.ok(retrasoService.listar(yo.id(), planId));
    }

    /** Al final se llega a tiempo. */
    @DeleteMapping("/planes/{planId}/retrasos/mio")
    public ResponseEntity<Void> retirar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                        @PathVariable UUID planId) {
        retrasoService.retirar(yo.id(), planId);
        return ResponseEntity.noContent().build();
    }
}
