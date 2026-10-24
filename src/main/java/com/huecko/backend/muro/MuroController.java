package com.huecko.backend.muro;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** El muro del grupo, por páginas: `?antes=` con el `siguiente` de la página anterior. */
@RestController
@RequestMapping("/api/grupos/{grupoId}/muro")
@RequiredArgsConstructor
public class MuroController {

    private final MuroService muroService;

    @GetMapping
    public ResponseEntity<MuroResponse> muro(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID grupoId,
                                             @RequestParam(required = false) Instant antes) {
        return ResponseEntity.ok(muroService.muro(yo.id(), grupoId, antes));
    }
}
