package com.huecko.backend.horario.controller;

import com.huecko.backend.horario.dto.BloqueHorarioRequest;
import com.huecko.backend.horario.dto.BloqueHorarioResponse;
import com.huecko.backend.horario.service.BloqueHorarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NOTA: el usuarioId se toma por ahora como path variable para poder probar
 * el módulo de forma aislada. Cuando se conecte Spring Security + JWT (fuera
 * del alcance de este módulo), esto se reemplaza por el usuario del token
 * autenticado (ej. @AuthenticationPrincipal), no por un valor que llega del cliente.
 */
@RestController
@RequestMapping("/api/usuarios/{usuarioId}/bloques-horario")
@RequiredArgsConstructor
public class BloqueHorarioController {

    private final BloqueHorarioService bloqueHorarioService;

    @PostMapping
    public ResponseEntity<BloqueHorarioResponse> crear(
            @PathVariable String usuarioId,
            @Valid @RequestBody BloqueHorarioRequest request) {
        BloqueHorarioResponse creado = bloqueHorarioService.crear(usuarioId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{bloqueId}")
    public ResponseEntity<BloqueHorarioResponse> actualizar(
            @PathVariable String usuarioId,
            @PathVariable String bloqueId,
            @Valid @RequestBody BloqueHorarioRequest request) {
        return ResponseEntity.ok(bloqueHorarioService.actualizar(usuarioId, bloqueId, request));
    }

    @DeleteMapping("/{bloqueId}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String usuarioId,
            @PathVariable String bloqueId) {
        bloqueHorarioService.eliminar(usuarioId, bloqueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<BloqueHorarioResponse>> listarConfirmados(@PathVariable String usuarioId) {
        return ResponseEntity.ok(bloqueHorarioService.listarConfirmados(usuarioId));
    }

    /** RF-03: bandeja de borradores generados por OCR pendientes de revisión. */
    @GetMapping("/borradores")
    public ResponseEntity<List<BloqueHorarioResponse>> listarBorradores(@PathVariable String usuarioId) {
        return ResponseEntity.ok(bloqueHorarioService.listarBorradores(usuarioId));
    }
}
