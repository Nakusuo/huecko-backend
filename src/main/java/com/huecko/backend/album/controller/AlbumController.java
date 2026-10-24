package com.huecko.backend.album.controller;

import com.huecko.backend.album.dto.AlbumResponses;
import com.huecko.backend.album.service.AlbumService;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Álbum de recuerdos: solo para la gente del grupo (si no, 404). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping("/grupos/{grupoId}/album")
    public ResponseEntity<AlbumResponses.AlbumGrupo> album(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                           @PathVariable UUID grupoId) {
        return ResponseEntity.ok(albumService.album(yo.id(), grupoId));
    }

    @GetMapping("/planes/{planId}/fotos")
    public ResponseEntity<AlbumResponses.AlbumPlan> delPlan(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                            @PathVariable UUID planId) {
        return ResponseEntity.ok(albumService.delPlan(yo.id(), planId));
    }

    /** Multipart con una o varias partes «fotos». El frontend las manda ya reducidas. */
    @PostMapping(value = "/planes/{planId}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlbumResponses.AlbumPlan> subir(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                          @PathVariable UUID planId,
                                                          @RequestPart("fotos") List<MultipartFile> fotos) {
        List<byte[]> bytes = new ArrayList<>();
        try {
            for (MultipartFile foto : fotos) {
                bytes.add(foto.getBytes());
            }
        } catch (IOException ex) {
            throw new BusinessException("No se pudieron leer las fotos. Vuelve a intentarlo.");
        }
        return ResponseEntity.ok(albumService.subir(yo.id(), planId, bytes));
    }

    /**
     * Una foto no cambia nunca (si se borra, desaparece; no se sustituye), así
     * que el navegador la puede guardar una semana. `private`: es del grupo.
     */
    @GetMapping(value = "/fotos/{fotoId}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> foto(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID fotoId) {
        return jpeg(albumService.imagen(yo.id(), fotoId, false));
    }

    @GetMapping(value = "/fotos/{fotoId}/miniatura", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> miniatura(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID fotoId) {
        return jpeg(albumService.imagen(yo.id(), fotoId, true));
    }

    @DeleteMapping("/fotos/{fotoId}")
    public ResponseEntity<Void> borrar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable UUID fotoId) {
        albumService.borrar(yo.id(), fotoId);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<byte[]> jpeg(byte[] bytes) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .contentType(MediaType.IMAGE_JPEG)
                .body(bytes);
    }
}
