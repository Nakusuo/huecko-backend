package com.huecko.backend.libre.service;

import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.UnauthorizedException;
import com.huecko.backend.libre.dto.LibreAhoraResponse;
import com.huecko.backend.libre.dto.MarcarLibreRequest;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.LibreAnunciado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * «¿Quién está libre ahora?».
 *
 * Que alguien esté libre solo se cuenta con su nombre si lo avisa esa persona
 * («Estoy libre»), y el aviso caduca solo. Del horario de los demás sale un
 * recuento por grupo, igual que en el heatmap (RNF-02). El horario propio sí
 * se usa para sugerirte hasta cuándo estás libre: es tuyo.
 */
@Service
public class LibreAhoraService {

    /** «Libre» es para hoy: nadie está libre «toda la semana». */
    static final Duration MAXIMO = Duration.ofHours(12);
    static final Duration MINIMO = Duration.ofMinutes(10);
    /** Si no te quedan clases hoy, la sugerencia llega hasta esta hora. */
    static final LocalTime FIN_DEL_DIA = LocalTime.of(22, 0);
    static final int MAX_AVISOS = 3;
    static final Duration VENTANA_AVISOS = Duration.ofHours(1);

    private final UsuarioRepository usuarios;
    private final GrupoRepository grupos;
    private final MiembroGrupoRepository miembros;
    private final BloqueHorarioRepository bloques;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    /** Cada aviso llega en vivo a todos tus grupos: sin tope, alguien podría inundarlos. */
    private final LimitadorDeIntentos avisos = new LimitadorDeIntentos(MAX_AVISOS, VENTANA_AVISOS, 10_000);

    @Autowired
    public LibreAhoraService(UsuarioRepository usuarios, GrupoRepository grupos, MiembroGrupoRepository miembros,
                             BloqueHorarioRepository bloques, ApplicationEventPublisher eventos) {
        this(usuarios, grupos, miembros, bloques, eventos, Clock.systemUTC());
    }

    LibreAhoraService(UsuarioRepository usuarios, GrupoRepository grupos, MiembroGrupoRepository miembros,
                      BloqueHorarioRepository bloques, ApplicationEventPublisher eventos, Clock reloj) {
        this.usuarios = usuarios;
        this.grupos = grupos;
        this.miembros = miembros;
        this.bloques = bloques;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public LibreAhoraResponse estado(UUID usuarioId) {
        Instant ahora = reloj.instant();
        LocalDateTime local = LocalDateTime.ofInstant(ahora, ZonaHoraria.ZONA);
        Usuario yo = cuenta(usuarioId);

        Map<Grupo, List<MiembroGrupo>> integrantes = new LinkedHashMap<>();
        for (Grupo grupo : grupos.findByMiembro(usuarioId)) {
            integrantes.put(grupo, miembros.findByGrupoIdConUsuario(grupo.getId()));
        }

        // Una sola consulta para los bloques de todas las personas implicadas.
        Set<String> ids = new HashSet<>();
        ids.add(usuarioId.toString());
        integrantes.values().forEach(lista -> lista.forEach(m -> ids.add(m.getUsuario().getId().toString())));
        Map<String, List<BloqueHorario>> horarios = bloques
                .findByUsuarioIdInAndEstado(new ArrayList<>(ids), BloqueHorario.Estado.CONFIRMADO).stream()
                .collect(Collectors.groupingBy(BloqueHorario::getUsuarioId));

        List<LibreAhoraResponse.GrupoLibre> respuesta = new ArrayList<>();
        integrantes.forEach((grupo, lista) -> {
            int sinClase = (int) lista.stream()
                    .filter(m -> !HorarioDeHoy.ocupadoA(horariosDe(horarios, m.getUsuario()), local))
                    .count();
            List<LibreAhoraResponse.PersonaLibre> personas = lista.stream()
                    .map(MiembroGrupo::getUsuario)
                    .filter(u -> !u.getId().equals(usuarioId) && libre(u, ahora))
                    .sorted(Comparator.comparing(Usuario::getLibreHasta).reversed())
                    .map(u -> new LibreAhoraResponse.PersonaLibre(u.getId(), u.getNombre(), u.getFotoActualizadaEn(),
                            u.getLibreHasta(), u.getLibreNota()))
                    .toList();
            respuesta.add(new LibreAhoraResponse.GrupoLibre(grupo.getId(), grupo.getNombre(), sinClase, lista.size(),
                    personas));
        });

        List<BloqueHorario> mios = horarios.getOrDefault(usuarioId.toString(), List.of());
        LibreAhoraResponse.MiAviso aviso = libre(yo, ahora)
                ? new LibreAhoraResponse.MiAviso(yo.getLibreHasta(), yo.getLibreNota())
                : null;
        return new LibreAhoraResponse(aviso, HorarioDeHoy.ocupadoA(mios, local), sugerencia(mios, ahora), respuesta);
    }

    /** «Estoy libre». Solo el primer aviso (no alargarlo) llega en vivo a tus grupos. */
    @Transactional
    public LibreAhoraResponse marcar(UUID usuarioId, MarcarLibreRequest request) {
        Instant ahora = reloj.instant();
        Usuario yo = cuenta(usuarioId);

        Instant hasta = request.hasta();
        if (hasta == null) {
            Instant segunHorario = sugerencia(
                    bloques.findByUsuarioIdAndEstado(usuarioId.toString(), BloqueHorario.Estado.CONFIRMADO), ahora);
            hasta = segunHorario != null ? segunHorario : ahora.plus(Duration.ofHours(1));
        }
        if (hasta.isBefore(ahora.plus(MINIMO))) {
            throw new BusinessException("Elige una hora de aquí a un rato: al menos dentro de 10 minutos.");
        }
        if (hasta.isAfter(ahora.plus(MAXIMO))) {
            throw new BusinessException("Como mucho 12 horas: «libre» es para hoy.");
        }

        boolean nuevo = !libre(yo, ahora);
        yo.setLibreHasta(hasta.truncatedTo(ChronoUnit.MICROS));
        yo.setLibreNota(nota(request.nota()));
        usuarios.save(yo);

        if (nuevo && avisos.permitir(usuarioId.toString(), ahora)) {
            List<UUID> grupoIds = grupos.findByMiembro(usuarioId).stream().map(Grupo::getId).toList();
            if (!grupoIds.isEmpty()) {
                eventos.publishEvent(new LibreAnunciado(grupoIds, usuarioId, yo.getNombre(), yo.getLibreHasta(),
                        yo.getLibreNota()));
            }
        }
        return estado(usuarioId);
    }

    @Transactional
    public LibreAhoraResponse quitar(UUID usuarioId) {
        Usuario yo = cuenta(usuarioId);
        yo.setLibreHasta(null);
        yo.setLibreNota(null);
        usuarios.save(yo);
        return estado(usuarioId);
    }

    /**
     * Hasta cuándo estás libre según tu horario: tu siguiente bloque de hoy o,
     * si no te queda ninguno, las 22:00. Nula si ahora mismo tienes un bloque
     * o si ya pasó esa hora: entonces tu horario no dice nada, y ofrecerlo
     * como «según tu horario» sería inventarlo.
     */
    static Instant sugerencia(List<BloqueHorario> mios, Instant ahora) {
        LocalDateTime local = LocalDateTime.ofInstant(ahora, ZonaHoraria.ZONA);
        if (HorarioDeHoy.ocupadoA(mios, local)) {
            return null;
        }
        LocalTime siguiente = HorarioDeHoy.siguienteInicio(mios, local);
        Instant hasta = local.toLocalDate().atTime(siguiente != null ? siguiente : FIN_DEL_DIA)
                .atZone(ZonaHoraria.ZONA).toInstant();
        if (hasta.isBefore(ahora.plus(MINIMO))) {
            return null;
        }
        Instant tope = ahora.plus(MAXIMO);
        return hasta.isAfter(tope) ? tope : hasta;
    }

    private static boolean libre(Usuario usuario, Instant ahora) {
        return usuario.getLibreHasta() != null && usuario.getLibreHasta().isAfter(ahora);
    }

    private static List<BloqueHorario> horariosDe(Map<String, List<BloqueHorario>> horarios, Usuario usuario) {
        return horarios.getOrDefault(usuario.getId().toString(), List.of());
    }

    private static String nota(String valor) {
        if (valor == null) {
            return null;
        }
        String limpia = valor.strip().replaceAll("\\s+", " ");
        return limpia.isEmpty() ? null : limpia;
    }

    private Usuario cuenta(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Tu cuenta ya no existe. Inicia sesión de nuevo."));
    }
}
