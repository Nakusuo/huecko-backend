package com.huecko.backend.auth.controller;

import com.huecko.backend.auth.dto.AuthResponse;
import com.huecko.backend.auth.dto.DosPasosLoginRequest;
import com.huecko.backend.auth.dto.LoginRequest;
import com.huecko.backend.auth.dto.RegisterRequest;
import com.huecko.backend.auth.service.AuthService;
import com.huecko.backend.auth.service.ProteccionAcceso;
import com.huecko.backend.common.IpCliente;
import com.huecko.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Rutas públicas: son las únicas que no exigen JWT, por eso llevan límite de intentos. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ProteccionAcceso proteccion;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        proteccion.antesDeLogin(IpCliente.de(http), request.email(), Instant.now());
        try {
            AuthResponse respuesta = authService.login(request);
            proteccion.loginCorrecto(request.email());
            return ResponseEntity.ok(respuesta);
        } catch (BusinessException credencialesIncorrectas) {
            proteccion.loginFallido(request.email(), Instant.now());
            throw credencialesIncorrectas;
        }
    }

    /** Segundo paso del login con verificación en dos pasos: `{ desafio, codigo }` → `{ token, user }`. */
    @PostMapping("/dos-pasos")
    public ResponseEntity<AuthResponse> dosPasos(@Valid @RequestBody DosPasosLoginRequest request,
                                                 HttpServletRequest http) {
        Instant ahora = Instant.now();
        UUID usuarioId = authService.usuarioDelDesafio(request.desafio());
        proteccion.antesDeCodigo(IpCliente.de(http), usuarioId, ahora);
        try {
            AuthResponse respuesta = authService.completarDosPasos(usuarioId, request.codigo(), ahora);
            proteccion.codigoCorrecto(usuarioId);
            return ResponseEntity.ok(respuesta);
        } catch (BusinessException codigoIncorrecto) {
            proteccion.codigoFallido(usuarioId, ahora);
            throw codigoIncorrecto;
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        proteccion.antesDeRegistro(IpCliente.de(http), Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }
}
