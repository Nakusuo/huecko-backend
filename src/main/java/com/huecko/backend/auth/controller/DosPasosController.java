package com.huecko.backend.auth.controller;

import com.huecko.backend.auth.dto.CodigoDosPasosRequest;
import com.huecko.backend.auth.dto.DesactivarDosPasosRequest;
import com.huecko.backend.auth.service.DosPasosService;
import com.huecko.backend.auth.service.ProteccionAcceso;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.common.IpCliente;
import com.huecko.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Configurar la verificación en dos pasos de la propia cuenta.
 *
 * Los pasos que comprueban un código comparten el límite de intentos del
 * login: con la sesión abierta tampoco se puede probar códigos sin fin.
 */
@RestController
@RequestMapping("/api/me/dos-pasos")
@RequiredArgsConstructor
public class DosPasosController {

    private final DosPasosService dosPasosService;
    private final ProteccionAcceso proteccion;

    @GetMapping
    public ResponseEntity<DosPasosService.Estado> estado(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return ResponseEntity.ok(dosPasosService.estado(yo.id()));
    }

    /** Secreto y dirección `otpauth://` para el QR. Aún no activa nada. */
    @PostMapping("/iniciar")
    public ResponseEntity<DosPasosService.Configuracion> iniciar(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return ResponseEntity.ok(dosPasosService.iniciar(yo.id()));
    }

    /** Con el primer código correcto se activa. Devuelve los códigos de respaldo. */
    @PostMapping("/activar")
    public ResponseEntity<Map<String, List<String>>> activar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                             @Valid @RequestBody CodigoDosPasosRequest request,
                                                             HttpServletRequest http) {
        Instant ahora = Instant.now();
        List<String> codigos = conLimite(yo.id(), http, ahora,
                () -> dosPasosService.activar(yo.id(), request.codigo(), ahora));
        return ResponseEntity.ok(Map.of("codigosRespaldo", codigos));
    }

    @PostMapping("/codigos-respaldo")
    public ResponseEntity<Map<String, List<String>>> regenerar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                               @Valid @RequestBody CodigoDosPasosRequest request,
                                                               HttpServletRequest http) {
        Instant ahora = Instant.now();
        List<String> codigos = conLimite(yo.id(), http, ahora,
                () -> dosPasosService.regenerarCodigos(yo.id(), request.codigo(), ahora));
        return ResponseEntity.ok(Map.of("codigosRespaldo", codigos));
    }

    @PostMapping("/desactivar")
    public ResponseEntity<Void> desactivar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                           @Valid @RequestBody DesactivarDosPasosRequest request,
                                           HttpServletRequest http) {
        Instant ahora = Instant.now();
        conLimite(yo.id(), http, ahora, () -> {
            dosPasosService.desactivar(yo.id(), request.password(), request.codigo(), ahora);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private <T> T conLimite(UUID usuarioId, HttpServletRequest http, Instant ahora, Supplier<T> accion) {
        proteccion.antesDeCodigo(IpCliente.de(http), usuarioId, ahora);
        try {
            T resultado = accion.get();
            proteccion.codigoCorrecto(usuarioId);
            return resultado;
        } catch (BusinessException incorrecto) {
            proteccion.codigoFallido(usuarioId, ahora);
            throw incorrecto;
        }
    }
}
