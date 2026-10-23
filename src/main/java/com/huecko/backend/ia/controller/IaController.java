package com.huecko.backend.ia.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.ia.VigiaIA;
import com.huecko.backend.ia.dto.EscaneoHorarioResponse;
import com.huecko.backend.ia.dto.EstadoIAResponse;
import com.huecko.backend.ia.service.AccesoAsistente;
import com.huecko.backend.ia.service.EscaneoHorarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Funciones de IA que la persona usa directamente (las de la app móvil).
 *
 * Las que dependen de un grupo o de un plan colgarán de su ruta
 * (/grupos/{id}/ia/…, /planes/{id}/ia/…); aquí van las que no.
 */
@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class IaController {

    private final EscaneoHorarioService escaneoHorarioService;
    private final AccesoAsistente acceso;
    private final VigiaIA vigiaIA;

    /** Para que la interfaz sepa si ofrecer la IA. No gasta cupo ni despierta al servicio. */
    @GetMapping("/estado")
    public ResponseEntity<EstadoIAResponse> estado() {
        VigiaIA.Estado estado = acceso.activo() ? vigiaIA.ultimo().estado() : VigiaIA.Estado.DESACTIVADA;
        boolean disponible = estado == VigiaIA.Estado.ACTIVA || estado == VigiaIA.Estado.SIN_COMPROBAR;
        return ResponseEntity.ok(new EstadoIAResponse(disponible, estado.name()));
    }

    /** Lee los bloques de la foto de un horario (multipart, campo «imagen»). No guarda nada. */
    @PostMapping(value = "/horario/escaneo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EscaneoHorarioResponse> escanearHorario(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @RequestPart("imagen") MultipartFile imagen) {
        return ResponseEntity.ok(escaneoHorarioService.escanear(yo.id(), imagen));
    }
}
