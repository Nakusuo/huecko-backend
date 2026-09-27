package com.huecko.backend.reporte.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.reporte.dto.ReporteRequests;
import com.huecko.backend.reporte.service.ReporteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lo que cualquier cuenta manda al equipo de Huecko: reportes a mano y errores del navegador. */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @PostMapping
    public ResponseEntity<Void> crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                      @Valid @RequestBody ReporteRequests.Crear request,
                                      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String navegador) {
        reporteService.crear(yo, request, navegador);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 202 siempre: al cliente le da igual si se guardó o se descartó por el límite. */
    @PostMapping("/errores")
    public ResponseEntity<Void> errorCliente(@AuthenticationPrincipal UsuarioAutenticado yo,
                                             @Valid @RequestBody ReporteRequests.ErrorCliente request,
                                             @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String navegador) {
        reporteService.registrarErrorCliente(yo, request, navegador);
        return ResponseEntity.accepted().build();
    }
}
