package com.huecko.backend.horario.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.horario.dto.BloqueHorarioRequest;
import com.huecko.backend.horario.dto.BloqueHorarioResponse;
import com.huecko.backend.horario.service.BloqueHorarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El usuarioId sigue viajando en la ruta porque así lo consume hoy el frontend
 * (endpoints.schedule en huecko-frontend), pero YA NO se confía en él: cada
 * método comprueba que coincida con el usuario del JWT. Sin esa comprobación,
 * cualquiera con un token válido podría leer y borrar el horario de otra
 * persona con solo cambiar un id en la URL.
 *
 * El siguiente paso natural es dejar las rutas en /api/bloques-horario y tomar
 * el id solo del token; eso obliga a tocar endpoints.ts y scheduleService.ts.
 */
@RestController
@RequestMapping("/api/usuarios/{usuarioId}/bloques-horario")
@RequiredArgsConstructor
public class BloqueHorarioController {

    private final BloqueHorarioService bloqueHorarioService;

    @PostMapping
    public ResponseEntity<BloqueHorarioResponse> crear(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody BloqueHorarioRequest request) {
        BloqueHorarioResponse creado =
                bloqueHorarioService.crear(exigirPropio(usuarioId, autenticado), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{bloqueId}")
    public ResponseEntity<BloqueHorarioResponse> actualizar(
            @PathVariable String usuarioId,
            @PathVariable String bloqueId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody BloqueHorarioRequest request) {
        return ResponseEntity.ok(
                bloqueHorarioService.actualizar(exigirPropio(usuarioId, autenticado), bloqueId, request));
    }

    @DeleteMapping("/{bloqueId}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String usuarioId,
            @PathVariable String bloqueId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        bloqueHorarioService.eliminar(exigirPropio(usuarioId, autenticado), bloqueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<BloqueHorarioResponse>> listarConfirmados(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok(
                bloqueHorarioService.listarConfirmados(exigirPropio(usuarioId, autenticado)));
    }

    /** RF-03: bandeja de borradores generados por OCR pendientes de revisión. */
    @GetMapping("/borradores")
    public ResponseEntity<List<BloqueHorarioResponse>> listarBorradores(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok(
                bloqueHorarioService.listarBorradores(exigirPropio(usuarioId, autenticado)));
    }

    /**
     * Devuelve el id del token, no el de la URL: aunque coincidan, así el
     * service nunca recibe un valor que venga del cliente.
     */
    private String exigirPropio(String usuarioIdEnRuta, UsuarioAutenticado autenticado) {
        String propio = autenticado.id().toString();
        if (!propio.equals(usuarioIdEnRuta)) {
            throw new ForbiddenException("No puedes consultar ni modificar el horario de otra persona");
        }
        return propio;
    }
}
