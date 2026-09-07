package com.huecko.backend.plan.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.plan.dto.PlanRequests;
import com.huecko.backend.plan.dto.PlanResponse;
import com.huecko.backend.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Módulo 3 (HU-08, HU-09, HU-10).
 *
 * Las rutas de listar y crear cuelgan del grupo, porque un plan no existe
 * fuera de uno. Las de un plan concreto no lo repiten: el plan ya sabe a qué
 * grupo pertenece, y arrastrar el `grupoId` por la URL abriría la puerta a que
 * los dos identificadores no coincidieran.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping("/grupos/{grupoId}/planes")
    public ResponseEntity<List<PlanResponse>> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                     @PathVariable UUID grupoId) {
        return ResponseEntity.ok(planService.listar(yo.id(), grupoId));
    }

    /** RF-08: proponer un plan con 2–5 ventanas que ya cumplen el umbral del grupo. */
    @PostMapping("/grupos/{grupoId}/planes")
    public ResponseEntity<PlanResponse> crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                              @PathVariable UUID grupoId,
                                              @Valid @RequestBody PlanRequests.Crear request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planService.crear(yo.id(), grupoId, request));
    }

    @GetMapping("/planes/{planId}")
    public ResponseEntity<PlanResponse> detalle(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                @PathVariable UUID planId) {
        return ResponseEntity.ok(planService.detalle(yo.id(), planId));
    }

    /** RF-09: votar una ventana. Con voto único, sustituye al anterior. */
    @PutMapping("/planes/{planId}/ventanas/{ventanaId}/voto")
    public ResponseEntity<PlanResponse> votar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                              @PathVariable UUID planId,
                                              @PathVariable UUID ventanaId) {
        return ResponseEntity.ok(planService.votar(yo.id(), planId, ventanaId));
    }

    /**
     * Retirar el voto. Es `DELETE` sobre el mismo recurso que crea el `PUT`, y
     * ambos son idempotentes: repetirlos deja el mismo estado.
     */
    @DeleteMapping("/planes/{planId}/ventanas/{ventanaId}/voto")
    public ResponseEntity<PlanResponse> quitarVoto(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                   @PathVariable UUID planId,
                                                   @PathVariable UUID ventanaId) {
        return ResponseEntity.ok(planService.quitarVoto(yo.id(), planId, ventanaId));
    }

    /**
     * RF-10: cerrar antes de tiempo. Al vencer el plazo se cierra solo, sin
     * que nadie tenga que llamar aquí (ver `CierreVotacionScheduler`).
     */
    @PostMapping("/planes/{planId}/cerrar")
    public ResponseEntity<PlanResponse> cerrar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                               @PathVariable UUID planId) {
        return ResponseEntity.ok(planService.cerrarManualmente(yo.id(), planId));
    }
}
