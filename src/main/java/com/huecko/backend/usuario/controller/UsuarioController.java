package com.huecko.backend.usuario.controller;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.postgres.entity.FotoPerfil;
import com.huecko.backend.usuario.dto.PerfilPublicoResponse;
import com.huecko.backend.usuario.service.FotoPerfilService;
import com.huecko.backend.usuario.service.PerfilPublicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Lo que puedes ver de otra persona: su tarjeta y su foto. Las dos piden
 * compartir al menos un grupo; si no, se responde 404.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final PerfilPublicoService perfilPublicoService;
    private final FotoPerfilService fotoPerfilService;

    @GetMapping("/{usuarioId}/perfil")
    public ResponseEntity<PerfilPublicoResponse> perfil(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                        @PathVariable UUID usuarioId) {
        return ResponseEntity.ok(perfilPublicoService.perfil(yo.id(), usuarioId));
    }

    /**
     * La foto como JPEG. `private` para que ningún proxy intermedio la guarde,
     * y un día de caché en el navegador: el frontend la pide con la fecha del
     * último cambio en la URL, así que una foto nueva nunca sale vieja.
     */
    @GetMapping(value = "/{usuarioId}/foto", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> foto(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID usuarioId) {
        FotoPerfil foto = fotoPerfilService.ver(yo.id(), usuarioId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .contentType(MediaType.IMAGE_JPEG)
                .body(foto.getContenido());
    }
}
