package com.huecko.backend.ia.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.ia.VigiaIA;
import com.huecko.backend.ia.dto.BorradorPlanResponse;
import com.huecko.backend.ia.dto.EscaneoHorarioResponse;
import com.huecko.backend.ia.dto.EstadoIAResponse;
import com.huecko.backend.ia.dto.IaRequests;
import com.huecko.backend.ia.dto.IdeasPlanResponse;
import com.huecko.backend.ia.dto.ResumenVotacionResponse;
import com.huecko.backend.ia.service.AccesoAsistente;
import com.huecko.backend.ia.service.BorradorPlanService;
import com.huecko.backend.ia.service.EscaneoHorarioService;
import com.huecko.backend.ia.service.IdeasPlanService;
import com.huecko.backend.ia.service.ResumenVotacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Funciones de IA que la persona usa directamente (las de la app móvil).
 *
 * Las que dependen de un grupo o de un plan cuelgan de su ruta
 * (/grupos/{id}/ia/…, /planes/{id}/ia/…): así se lee de un vistazo a qué
 * datos tocan.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IaController {

    private final EscaneoHorarioService escaneoHorarioService;
    private final BorradorPlanService borradorPlanService;
    private final IdeasPlanService ideasPlanService;
    private final ResumenVotacionService resumenVotacionService;
    private final AccesoAsistente acceso;
    private final VigiaIA vigiaIA;

    /** Para que la interfaz sepa si ofrecer la IA. No gasta cupo ni despierta al servicio. */
    @GetMapping("/ia/estado")
    public ResponseEntity<EstadoIAResponse> estado() {
        VigiaIA.Estado estado = acceso.activo() ? vigiaIA.ultimo().estado() : VigiaIA.Estado.DESACTIVADA;
        boolean disponible = estado == VigiaIA.Estado.ACTIVA || estado == VigiaIA.Estado.SIN_COMPROBAR;
        return ResponseEntity.ok(new EstadoIAResponse(disponible, estado.name()));
    }

    /** Lee los bloques de la foto de un horario (multipart, campo «imagen»). No guarda nada. */
    @PostMapping(value = "/ia/horario/escaneo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EscaneoHorarioResponse> escanearHorario(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @RequestPart("imagen") MultipartFile imagen) {
        return ResponseEntity.ok(escaneoHorarioService.escanear(yo.id(), imagen));
    }

    /** Borrador de «Proponer plan» a partir de una frase. Cualquier miembro; no guarda nada. */
    @PostMapping("/grupos/{grupoId}/ia/borrador-plan")
    public ResponseEntity<BorradorPlanResponse> borradorPlan(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID grupoId,
            @Valid @RequestBody IaRequests.BorradorPlan peticion) {
        return ResponseEntity.ok(borradorPlanService.redactar(yo.id(), grupoId, peticion.texto()));
    }

    /** Tres ideas de plan para los huecos del grupo. Cualquier miembro; no guarda nada. */
    @PostMapping("/grupos/{grupoId}/ia/ideas-plan")
    public ResponseEntity<IdeasPlanResponse> ideasPlan(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID grupoId) {
        return ResponseEntity.ok(ideasPlanService.proponer(yo.id(), grupoId));
    }

    /** Cómo va la votación de un plan y qué conviene hacer. Cualquier miembro; no cambia nada. */
    @PostMapping("/planes/{planId}/ia/resumen")
    public ResponseEntity<ResumenVotacionResponse> resumenVotacion(
            @AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(resumenVotacionService.resumir(yo.id(), planId));
    }
}
