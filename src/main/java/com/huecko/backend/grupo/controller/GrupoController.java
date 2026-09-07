package com.huecko.backend.grupo.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.dto.GrupoRequests;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.service.GrupoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Módulo 2 (HU-05, HU-06, HU-07).
 *
 * A diferencia de BloqueHorarioController, el usuario nunca llega por la URL:
 * sale del JWT con @AuthenticationPrincipal. Un id de usuario en la ruta sería
 * un dato que el cliente elige, y con él cualquiera podría pedir los grupos
 * de otra persona.
 */
@RestController
@RequestMapping("/api/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final GrupoService grupoService;

    /** Grupos a los que pertenece quien pide. */
    @GetMapping
    public ResponseEntity<List<GrupoResponse>> listar(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return ResponseEntity.ok(grupoService.listar(yo.id()));
    }

    @PostMapping
    public ResponseEntity<GrupoResponse> crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                               @Valid @RequestBody GrupoRequests.Crear request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(grupoService.crear(yo.id(), request));
    }

    @GetMapping("/{grupoId}")
    public ResponseEntity<GrupoResponse> detalle(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                 @PathVariable UUID grupoId) {
        return ResponseEntity.ok(grupoService.detalle(yo.id(), grupoId));
    }

    /** RF-06: ajustar el umbral de coincidencia, el nombre o la descripción. */
    @PatchMapping("/{grupoId}")
    public ResponseEntity<GrupoResponse> actualizar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                    @PathVariable UUID grupoId,
                                                    @Valid @RequestBody GrupoRequests.Actualizar request) {
        return ResponseEntity.ok(grupoService.actualizar(yo.id(), grupoId, request));
    }

    @PostMapping("/unirse")
    public ResponseEntity<GrupoResponse> unirse(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                @Valid @RequestBody GrupoRequests.Unirse request) {
        return ResponseEntity.ok(grupoService.unirse(yo.id(), request));
    }

    @PatchMapping("/{grupoId}/miembros/{usuarioId}")
    public ResponseEntity<GrupoResponse> actualizarMiembro(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID grupoId,
            @PathVariable UUID usuarioId,
            @Valid @RequestBody GrupoRequests.ActualizarMiembro request) {
        return ResponseEntity.ok(grupoService.actualizarMiembro(yo.id(), grupoId, usuarioId, request));
    }

    /** Salirse del grupo, o sacar a alguien si quien pide es el organizador. */
    @DeleteMapping("/{grupoId}/miembros/{usuarioId}")
    public ResponseEntity<Void> salir(@AuthenticationPrincipal UsuarioAutenticado yo,
                                      @PathVariable UUID grupoId,
                                      @PathVariable UUID usuarioId) {
        grupoService.salir(yo.id(), grupoId, usuarioId);
        return ResponseEntity.noContent().build();
    }

    /**
     * RF-05 / RF-06 / RF-07: heatmap semanal y ventanas que cumplen el umbral.
     *
     * `umbral` es opcional y solo afecta a esta consulta; el ajuste guardado del
     * grupo se cambia con el PATCH. `semana` acepta cualquier día y se
     * normaliza al lunes de esa semana.
     */
    @GetMapping("/{grupoId}/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> disponibilidad(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID grupoId,
            @RequestParam(required = false) Integer umbral,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        return ResponseEntity.ok(grupoService.disponibilidad(yo.id(), grupoId, umbral, semana));
    }
}
