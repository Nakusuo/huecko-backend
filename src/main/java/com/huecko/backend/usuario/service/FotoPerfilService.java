package com.huecko.backend.usuario.service;

import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.common.exception.UnauthorizedException;
import com.huecko.backend.postgres.entity.FotoPerfil;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.FotoPerfilRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Foto de perfil. La ve quien comparte al menos un grupo contigo, nadie más:
 * no hay enlaces públicos a fotos, y a quien no tiene un grupo en común se le
 * responde como si la foto no existiera.
 */
@Service
public class FotoPerfilService {

    static final int MAX_CAMBIOS = 10;
    static final Duration VENTANA_CAMBIOS = Duration.ofHours(1);
    private static final String NO_EXISTE = "Esa foto no existe.";

    private final UsuarioRepository usuarios;
    private final FotoPerfilRepository fotos;
    private final MiembroGrupoRepository miembros;
    private final Clock reloj;
    /** Rehacer la foto cuesta CPU, y cada cambio obliga a todos sus grupos a volver a descargarla. */
    private final LimitadorDeIntentos cambios = new LimitadorDeIntentos(MAX_CAMBIOS, VENTANA_CAMBIOS, 10_000);

    @Autowired
    public FotoPerfilService(UsuarioRepository usuarios, FotoPerfilRepository fotos, MiembroGrupoRepository miembros) {
        this(usuarios, fotos, miembros, Clock.systemUTC());
    }

    FotoPerfilService(UsuarioRepository usuarios, FotoPerfilRepository fotos, MiembroGrupoRepository miembros,
                      Clock reloj) {
        this.usuarios = usuarios;
        this.fotos = fotos;
        this.miembros = miembros;
        this.reloj = reloj;
    }

    @Transactional
    public Usuario guardar(UUID usuarioId, byte[] original) {
        Usuario usuario = cuenta(usuarioId);
        // Con la precisión de Postgres: si no, la respuesta y las lecturas
        // posteriores darían dos «versiones» distintas de la misma foto.
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        if (!cambios.permitir(usuarioId.toString(), ahora)) {
            throw new DemasiadosIntentosException("Cambiaste de foto muchas veces seguidas. Espera un rato y vuelve a intentarlo.");
        }
        byte[] limpia = FotosDePerfil.normalizar(original);

        fotos.save(new FotoPerfil(usuarioId, limpia, ahora));
        usuario.setFotoActualizadaEn(ahora);
        return usuarios.save(usuario);
    }

    @Transactional
    public Usuario quitar(UUID usuarioId) {
        Usuario usuario = cuenta(usuarioId);
        fotos.deleteById(usuarioId);
        usuario.setFotoActualizadaEn(null);
        return usuarios.save(usuario);
    }

    /** La foto de `usuarioId` vista por `quienMira`: la propia, o la de alguien de un grupo en común. */
    @Transactional(readOnly = true)
    public FotoPerfil ver(UUID quienMira, UUID usuarioId) {
        if (!quienMira.equals(usuarioId) && miembros.gruposEnComun(quienMira, usuarioId).isEmpty()) {
            throw new NotFoundException(NO_EXISTE);
        }
        return fotos.findById(usuarioId).orElseThrow(() -> new NotFoundException(NO_EXISTE));
    }

    /** Para la administración, a partir de un reporte de conducta (ver ReporteService). */
    @Transactional(readOnly = true)
    public FotoPerfil verSinComprobar(UUID usuarioId) {
        return fotos.findById(usuarioId).orElseThrow(() -> new NotFoundException(NO_EXISTE));
    }

    private Usuario cuenta(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Tu cuenta ya no existe. Inicia sesión de nuevo."));
    }
}
