package com.huecko.backend.album.service;

import com.huecko.backend.album.dto.AlbumResponses;
import com.huecko.backend.common.Imagenes;
import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.postgres.entity.FotoPlan;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.FotoPlanRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository.FotoPlanResumen;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.AlbumCambiado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * Álbum de recuerdos: las fotos de un plan.
 *
 * Se suben cuando el plan confirmado ya empezó (son recuerdos, no promesas) y
 * las ve solo el grupo. Las fotos viven en Postgres, así que hay dos topes:
 * diez por persona en cada plan y sesenta por grupo, que con fotos de unos
 * 150 KB caben de sobra en la base gratuita. Borrar una la puede hacer quien
 * la subió o quien organiza; el resto puede reportarla.
 */
@Service
public class AlbumService {

    static final int MAX_POR_PERSONA_Y_PLAN = 10;
    static final int MAX_POR_GRUPO = 60;
    static final int MAX_POR_ENVIO = 10;
    static final int MAX_ENVIOS = 20;
    static final Duration VENTANA_ENVIOS = Duration.ofHours(1);
    private static final String NO_EXISTE_PLAN = "El plan no existe o no perteneces a su grupo";
    private static final String NO_EXISTE_FOTO = "Esa foto no existe.";

    private final FotoPlanRepository fotos;
    private final PlanRepository planes;
    private final MiembroGrupoRepository miembros;
    private final UsuarioRepository usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    /** Rehacer cada foto cuesta CPU, y el plan gratuito tiene poca. */
    private final LimitadorDeIntentos envios = new LimitadorDeIntentos(MAX_ENVIOS, VENTANA_ENVIOS, 10_000);

    @Autowired
    public AlbumService(FotoPlanRepository fotos, PlanRepository planes, MiembroGrupoRepository miembros,
                        UsuarioRepository usuarios, ApplicationEventPublisher eventos) {
        this(fotos, planes, miembros, usuarios, eventos, Clock.systemUTC());
    }

    AlbumService(FotoPlanRepository fotos, PlanRepository planes, MiembroGrupoRepository miembros,
                 UsuarioRepository usuarios, ApplicationEventPublisher eventos, Clock reloj) {
        this.fotos = fotos;
        this.planes = planes;
        this.miembros = miembros;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Los planes vividos del grupo, del más reciente al más antiguo, con sus fotos. */
    @Transactional(readOnly = true)
    public AlbumResponses.AlbumGrupo album(UUID usuarioId, UUID grupoId) {
        exigirMiembro(usuarioId, grupoId, "El grupo no existe o no perteneces a él");
        Instant ahora = reloj.instant();
        List<FotoPlanResumen> todas = fotos.findByGrupoIdOrderBySubidaEnAsc(grupoId);
        Map<UUID, List<FotoPlanResumen>> porPlan = todas.stream()
                .collect(Collectors.groupingBy(FotoPlanResumen::getPlanId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, Plan> mostrados = new LinkedHashMap<>();
        planes.findConfirmadosConFecha(List.of(grupoId)).stream()
                .filter(p -> empezo(p, ahora))
                .forEach(p -> mostrados.put(p.getId(), p));
        // Un plan que se reprogramó después de tener fotos sigue en el álbum.
        Set<UUID> faltan = new HashSet<>(porPlan.keySet());
        faltan.removeAll(mostrados.keySet());
        planes.findAllById(faltan).forEach(p -> mostrados.put(p.getId(), p));

        List<AlbumResponses.PlanConFotos> lista = mostrados.values().stream()
                .sorted(Comparator.comparing(AlbumService::inicio, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> {
                    List<FotoPlanResumen> suyas = porPlan.getOrDefault(p.getId(), List.of());
                    VentanaPlan fecha = p.getVentanaConfirmada();
                    return new AlbumResponses.PlanConFotos(p.getId(), p.getTitulo(),
                            fecha == null ? null : fecha.getFecha(), fecha == null ? null : fecha.getHoraInicio(),
                            suyas.size(), suyas.isEmpty() ? null : suyas.get(0).getId());
                })
                .toList();
        return new AlbumResponses.AlbumGrupo(todas.size(), MAX_POR_GRUPO, lista);
    }

    @Transactional(readOnly = true)
    public AlbumResponses.AlbumPlan delPlan(UUID usuarioId, UUID planId) {
        Plan plan = planes.findByIdConVentanas(planId).orElseThrow(() -> new NotFoundException(NO_EXISTE_PLAN));
        MiembroGrupo yo = exigirMiembro(usuarioId, plan.getGrupo().getId(), NO_EXISTE_PLAN);
        boolean organiza = yo.getRol() == MiembroGrupo.Rol.ORGANIZADOR;

        List<FotoPlanResumen> suyas = fotos.findByPlanIdOrderBySubidaEnAsc(planId);
        Map<UUID, String> nombres = usuarios.findAllById(suyas.stream().map(FotoPlanResumen::getAutorId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
        int mias = (int) suyas.stream().filter(f -> f.getAutorId().equals(usuarioId)).count();
        int restantesMias = Math.max(0, MAX_POR_PERSONA_Y_PLAN - mias);
        int restantesGrupo = Math.max(0, MAX_POR_GRUPO - (int) fotos.countByGrupoId(plan.getGrupo().getId()));

        List<AlbumResponses.Foto> lista = suyas.stream()
                .map(f -> new AlbumResponses.Foto(f.getId(), f.getAutorId(),
                        nombres.getOrDefault(f.getAutorId(), "Alguien"), f.getAncho(), f.getAlto(), f.getSubidaEn(),
                        organiza || f.getAutorId().equals(usuarioId)))
                .toList();
        VentanaPlan fecha = plan.getVentanaConfirmada();
        return new AlbumResponses.AlbumPlan(plan.getId(), plan.getGrupo().getId(), plan.getTitulo(),
                fecha == null ? null : fecha.getFecha(), fecha == null ? null : fecha.getHoraInicio(),
                empezo(plan, reloj.instant()) && restantesMias > 0 && restantesGrupo > 0,
                restantesMias, restantesGrupo, lista);
    }

    /** Sube hasta diez fotos de una vez. O entran todas o ninguna. */
    @Transactional
    public AlbumResponses.AlbumPlan subir(UUID usuarioId, UUID planId, List<byte[]> archivos) {
        if (archivos.isEmpty()) {
            throw new BusinessException("Elige al menos una foto.");
        }
        if (archivos.size() > MAX_POR_ENVIO) {
            throw new BusinessException("Como mucho " + MAX_POR_ENVIO + " fotos a la vez.");
        }
        Plan plan = planConAcceso(usuarioId, planId);
        UUID grupoId = plan.getGrupo().getId();
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        if (!empezo(plan, ahora)) {
            throw new BusinessException("Las fotos se suben cuando el plan ya empezó.");
        }

        long mias = fotos.countByPlanIdAndAutorId(planId, usuarioId);
        if (mias + archivos.size() > MAX_POR_PERSONA_Y_PLAN) {
            throw new BusinessException("Cada persona puede subir hasta " + MAX_POR_PERSONA_Y_PLAN
                    + " fotos por plan: te quedan " + Math.max(0, MAX_POR_PERSONA_Y_PLAN - mias) + ".");
        }
        long delGrupo = fotos.countByGrupoId(grupoId);
        if (delGrupo + archivos.size() > MAX_POR_GRUPO) {
            throw new BusinessException("El álbum del grupo está casi lleno: quedan "
                    + Math.max(0, MAX_POR_GRUPO - delGrupo) + " de " + MAX_POR_GRUPO + " fotos. Borren alguna para subir más.");
        }
        if (!envios.permitir(usuarioId.toString(), ahora)) {
            throw new DemasiadosIntentosException("Subiste muchas fotos seguidas. Espera un rato y vuelve a intentarlo.");
        }

        // Primero se rehacen todas: si una no es una imagen, no se guarda ninguna.
        List<Imagenes.Recuerdo> rehechas = new ArrayList<>();
        for (int i = 0; i < archivos.size(); i++) {
            try {
                rehechas.add(Imagenes.recuerdo(archivos.get(i)));
            } catch (BusinessException ex) {
                throw new BusinessException(archivos.size() == 1 ? ex.getMessage() : "Foto " + (i + 1) + ": " + ex.getMessage());
            }
        }
        for (int i = 0; i < rehechas.size(); i++) {
            Imagenes.Recuerdo r = rehechas.get(i);
            fotos.save(FotoPlan.builder()
                    .id(UUID.randomUUID())
                    .planId(planId)
                    .grupoId(grupoId)
                    .autorId(usuarioId)
                    .contenido(r.foto())
                    .miniatura(r.miniatura())
                    .ancho(r.ancho())
                    .alto(r.alto())
                    // Un microsegundo entre una y otra: conservan el orden en que se eligieron.
                    .subidaEn(ahora.plus(i, ChronoUnit.MICROS))
                    .build());
        }

        String nombre = usuarios.findById(usuarioId).map(Usuario::getNombre).orElse("Alguien");
        eventos.publishEvent(new AlbumCambiado(grupoId, planId, plan.getTitulo(), usuarioId, nombre, rehechas.size()));
        return delPlan(usuarioId, planId);
    }

    @Transactional
    public void borrar(UUID usuarioId, UUID fotoId) {
        FotoPlanResumen foto = fotos.findResumenById(fotoId).orElseThrow(() -> new NotFoundException(NO_EXISTE_FOTO));
        MiembroGrupo yo = exigirMiembro(usuarioId, foto.getGrupoId(), NO_EXISTE_FOTO);
        if (!foto.getAutorId().equals(usuarioId) && yo.getRol() != MiembroGrupo.Rol.ORGANIZADOR) {
            throw new ForbiddenException("Solo quien la subió o quien organiza el grupo puede borrarla.");
        }
        fotos.deleteById(fotoId);
        String titulo = planes.findById(foto.getPlanId()).map(Plan::getTitulo).orElse("");
        eventos.publishEvent(new AlbumCambiado(foto.getGrupoId(), foto.getPlanId(), titulo, usuarioId, null, 0));
    }

    /** Los bytes de una foto (o su miniatura), solo para alguien del grupo. */
    @Transactional(readOnly = true)
    public byte[] imagen(UUID usuarioId, UUID fotoId, boolean miniatura) {
        FotoPlanResumen foto = fotos.findResumenById(fotoId).orElseThrow(() -> new NotFoundException(NO_EXISTE_FOTO));
        exigirMiembro(usuarioId, foto.getGrupoId(), NO_EXISTE_FOTO);
        return (miniatura ? fotos.miniatura(fotoId) : fotos.contenido(fotoId))
                .orElseThrow(() -> new NotFoundException(NO_EXISTE_FOTO));
    }

    /** Si el plan está confirmado y ya llegó su hora: desde entonces se pueden subir recuerdos. */
    static boolean empezo(Plan plan, Instant ahora) {
        Instant inicio = inicio(plan);
        return plan.getEstado() == Plan.Estado.CONFIRMADO && inicio != null && !inicio.isAfter(ahora);
    }

    private static Instant inicio(Plan plan) {
        VentanaPlan fecha = plan.getVentanaConfirmada();
        if (fecha == null || fecha.getFecha() == null || fecha.getHoraInicio() == null) {
            return null;
        }
        return LocalDateTime.of(fecha.getFecha(), fecha.getHoraInicio()).atZone(ZonaHoraria.ZONA).toInstant();
    }

    /** Un plan o una foto de un grupo ajeno se responden como inexistentes, igual que en el resto de la API. */
    private MiembroGrupo exigirMiembro(UUID usuarioId, UUID grupoId, String mensaje) {
        return miembros.findByGrupo_IdAndUsuario_Id(grupoId, usuarioId).orElseThrow(() -> new NotFoundException(mensaje));
    }

    private Plan planConAcceso(UUID usuarioId, UUID planId) {
        Plan plan = planes.findByIdConVentanas(planId).orElseThrow(() -> new NotFoundException(NO_EXISTE_PLAN));
        exigirMiembro(usuarioId, plan.getGrupo().getId(), NO_EXISTE_PLAN);
        return plan;
    }
}
