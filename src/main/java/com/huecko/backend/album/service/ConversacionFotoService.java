package com.huecko.backend.album.service;

import com.huecko.backend.album.dto.AlbumResponses;
import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.postgres.entity.ComentarioFoto;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.ReaccionFoto;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.ComentarioFotoRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository.FotoPlanResumen;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.ReaccionFotoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.ConversacionFoto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reacciones (❤️ 😂 🔥 😮 👏) y comentarios en las fotos del álbum. Como las
 * fotos, solo para la gente del grupo. Un comentario lo borra quien lo
 * escribió o quien organiza el grupo.
 */
@Service
public class ConversacionFotoService {

    static final int MAX_COMENTARIOS = 20;
    static final int MAX_REACCIONES = 60;
    static final Duration VENTANA = Duration.ofMinutes(10);
    private static final String NO_EXISTE_FOTO = "Esa foto no existe.";

    private final FotoPlanRepository fotos;
    private final ReaccionFotoRepository reacciones;
    private final ComentarioFotoRepository comentarios;
    private final MiembroGrupoRepository miembros;
    private final UsuarioRepository usuarios;
    private final PlanRepository planes;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    /** Cada comentario y cada reacción llegan en vivo a todo el grupo. */
    private final LimitadorDeIntentos ritmoComentarios = new LimitadorDeIntentos(MAX_COMENTARIOS, VENTANA, 10_000);
    private final LimitadorDeIntentos ritmoReacciones = new LimitadorDeIntentos(MAX_REACCIONES, VENTANA, 10_000);

    @Autowired
    public ConversacionFotoService(FotoPlanRepository fotos, ReaccionFotoRepository reacciones,
                                   ComentarioFotoRepository comentarios, MiembroGrupoRepository miembros,
                                   UsuarioRepository usuarios, PlanRepository planes,
                                   ApplicationEventPublisher eventos) {
        this(fotos, reacciones, comentarios, miembros, usuarios, planes, eventos, Clock.systemUTC());
    }

    ConversacionFotoService(FotoPlanRepository fotos, ReaccionFotoRepository reacciones,
                            ComentarioFotoRepository comentarios, MiembroGrupoRepository miembros,
                            UsuarioRepository usuarios, PlanRepository planes, ApplicationEventPublisher eventos,
                            Clock reloj) {
        this.fotos = fotos;
        this.reacciones = reacciones;
        this.comentarios = comentarios;
        this.miembros = miembros;
        this.usuarios = usuarios;
        this.planes = planes;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Pone o quita una reacción tuya. Repetir la misma acción no cambia nada. */
    @Transactional
    public AlbumResponses.Interacciones reaccionar(UUID usuarioId, UUID fotoId, ReaccionFoto.Reaccion reaccion,
                                                   boolean poner) {
        FotoPlanResumen foto = fotoDelGrupo(usuarioId, fotoId);
        Instant ahora = reloj.instant();
        ReaccionFoto.Clave clave = new ReaccionFoto.Clave(fotoId, usuarioId, reaccion);
        boolean tenia = reacciones.existsById(clave);
        if (poner != tenia) {
            if (!ritmoReacciones.permitir(usuarioId.toString(), ahora)) {
                throw new DemasiadosIntentosException("Vas muy rápido. Espera un momento.");
            }
            if (poner) {
                reacciones.save(new ReaccionFoto(clave, ahora.truncatedTo(ChronoUnit.MICROS)));
            } else {
                reacciones.deleteById(clave);
            }
            publicar(foto, usuarioId, false);
        }
        return resumen(List.of(fotoId), usuarioId).get(fotoId);
    }

    @Transactional(readOnly = true)
    public List<AlbumResponses.Comentario> comentarios(UUID usuarioId, UUID fotoId) {
        FotoPlanResumen foto = fotoDelGrupo(usuarioId, fotoId);
        boolean organiza = rol(usuarioId, foto.getGrupoId()) == MiembroGrupo.Rol.ORGANIZADOR;
        List<ComentarioFoto> lista = comentarios.findByFotoIdOrderByCreadoEnAsc(fotoId);
        Map<UUID, Usuario> autores = usuarios.findAllById(lista.stream().map(ComentarioFoto::getAutorId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
        return lista.stream().map(c -> {
            Usuario autor = autores.get(c.getAutorId());
            return new AlbumResponses.Comentario(c.getId(), c.getAutorId(),
                    autor == null ? "Alguien" : autor.getNombre(), autor == null ? null : autor.getFotoActualizadaEn(),
                    c.getTexto(), c.getCreadoEn(), organiza || c.getAutorId().equals(usuarioId));
        }).toList();
    }

    @Transactional
    public List<AlbumResponses.Comentario> comentar(UUID usuarioId, UUID fotoId, String texto) {
        FotoPlanResumen foto = fotoDelGrupo(usuarioId, fotoId);
        String limpio = texto == null ? "" : texto.strip().replaceAll("[ \\t]+", " ");
        if (limpio.isEmpty()) {
            throw new BusinessException("Escribe algo antes de enviarlo.");
        }
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        if (!ritmoComentarios.permitir(usuarioId.toString(), ahora)) {
            throw new DemasiadosIntentosException("Comentaste mucho seguido. Espera un rato.");
        }
        comentarios.save(ComentarioFoto.builder()
                .id(UUID.randomUUID())
                .fotoId(fotoId)
                .autorId(usuarioId)
                .texto(limpio)
                .creadoEn(ahora)
                .build());
        publicar(foto, usuarioId, true);
        return comentarios(usuarioId, fotoId);
    }

    @Transactional
    public void borrarComentario(UUID usuarioId, UUID comentarioId) {
        ComentarioFoto comentario = comentarios.findById(comentarioId)
                .orElseThrow(() -> new NotFoundException("Ese comentario no existe."));
        FotoPlanResumen foto = fotoDelGrupo(usuarioId, comentario.getFotoId());
        if (!comentario.getAutorId().equals(usuarioId)
                && rol(usuarioId, foto.getGrupoId()) != MiembroGrupo.Rol.ORGANIZADOR) {
            throw new ForbiddenException("Solo quien lo escribió o quien organiza el grupo puede borrarlo.");
        }
        comentarios.deleteById(comentarioId);
        publicar(foto, usuarioId, false);
    }

    /**
     * Reacciones y comentarios de varias fotos, con dos consultas en total.
     * Lo usa el álbum de un plan para pintar los contadores de cada foto.
     */
    @Transactional(readOnly = true)
    public Map<UUID, AlbumResponses.Interacciones> resumen(Collection<UUID> fotoIds, UUID usuarioId) {
        Map<UUID, Map<String, Integer>> cuentas = new HashMap<>();
        Map<UUID, List<String>> mias = new HashMap<>();
        if (fotoIds.isEmpty()) {
            return Map.of();
        }
        for (ReaccionFoto r : reacciones.deLasFotos(fotoIds)) {
            UUID foto = r.getId().getFotoId();
            String nombre = r.getId().getReaccion().name();
            cuentas.computeIfAbsent(foto, f -> new LinkedHashMap<>()).merge(nombre, 1, Integer::sum);
            if (r.getId().getUsuarioId().equals(usuarioId)) {
                mias.computeIfAbsent(foto, f -> new ArrayList<>()).add(nombre);
            }
        }
        Map<UUID, Integer> numComentarios = new HashMap<>();
        for (Object[] fila : comentarios.contarPorFoto(fotoIds)) {
            numComentarios.put((UUID) fila[0], ((Number) fila[1]).intValue());
        }
        Map<UUID, AlbumResponses.Interacciones> resultado = new HashMap<>();
        for (UUID id : fotoIds) {
            resultado.put(id, new AlbumResponses.Interacciones(id, ordenadas(cuentas.get(id)),
                    mias.getOrDefault(id, List.of()), numComentarios.getOrDefault(id, 0)));
        }
        return resultado;
    }

    /** En el orden en que se ofrecen, no en el que llegaron. */
    private static Map<String, Integer> ordenadas(Map<String, Integer> cuentas) {
        if (cuentas == null) {
            return Map.of();
        }
        Map<String, Integer> orden = new LinkedHashMap<>();
        Arrays.stream(ReaccionFoto.Reaccion.values())
                .filter(r -> cuentas.containsKey(r.name()))
                .forEach(r -> orden.put(r.name(), cuentas.get(r.name())));
        return orden;
    }

    private void publicar(FotoPlanResumen foto, UUID usuarioId, boolean comentario) {
        String nombre = usuarios.findById(usuarioId).map(Usuario::getNombre).orElse("Alguien");
        String titulo = planes.findById(foto.getPlanId()).map(Plan::getTitulo).orElse("");
        eventos.publishEvent(new ConversacionFoto(foto.getGrupoId(), foto.getPlanId(), foto.getId(),
                foto.getAutorId(), usuarioId, nombre, titulo, comentario));
    }

    private MiembroGrupo.Rol rol(UUID usuarioId, UUID grupoId) {
        return miembros.findByGrupo_IdAndUsuario_Id(grupoId, usuarioId).map(MiembroGrupo::getRol).orElse(null);
    }

    /** Una foto de un grupo ajeno se responde como inexistente. */
    private FotoPlanResumen fotoDelGrupo(UUID usuarioId, UUID fotoId) {
        return fotos.findResumenById(fotoId)
                .filter(f -> miembros.existsByGrupo_IdAndUsuario_Id(f.getGrupoId(), usuarioId))
                .orElseThrow(() -> new NotFoundException(NO_EXISTE_FOTO));
    }
}
