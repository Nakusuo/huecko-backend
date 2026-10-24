package com.huecko.backend.grupo.service;

import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.dto.GrupoRequests;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.dto.MiembroResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.mongo.repository.VotacionExpresOperaciones;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.postgres.repository.VotoVentanaRepository;
import com.huecko.backend.tiemporeal.AccesoRevocado;
import com.huecko.backend.tiemporeal.MiembroUnido;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Módulo 2: grupos y cruce de disponibilidad (HU-05, HU-06, HU-07).
 *
 * Toda operación arranca comprobando la pertenencia del usuario del token. Un
 * grupo es privado: quien no está dentro no puede ni leerlo, y por eso el
 * heatmap tampoco se le puede pedir "a ver qué sale".
 */
@Service
@RequiredArgsConstructor
public class GrupoService {

    /** Códigos equivocados por persona antes de frenarla. Nadie se equivoca tanto escribiendo uno de verdad. */
    static final int MAX_CODIGOS_FALLIDOS = 10;
    static final Duration VENTANA_CODIGOS = Duration.ofMinutes(15);
    private static final int INTENTOS_DE_CODIGO_LIBRE = 5;

    private final GrupoRepository grupoRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BloqueHorarioRepository bloqueHorarioRepository;
    private final CalculadoraDisponibilidad calculadora;
    private final PlanRepository planRepository;
    private final VotoVentanaRepository votoVentanaRepository;
    private final VotacionExpresOperaciones votacionExpresOperaciones;
    private final ApplicationEventPublisher eventos;
    /** Sin límite, se podrían probar códigos hasta acertar con uno ajeno. */
    private final LimitadorDeIntentos codigosFallidos =
            new LimitadorDeIntentos(MAX_CODIGOS_FALLIDOS, VENTANA_CODIGOS, 10_000);

    /* ------------------------------------------------------------------ *
     * Grupos
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public List<GrupoResponse> listar(UUID usuarioId) {
        return grupoRepository.findByMiembro(usuarioId).stream()
                .map(grupo -> respuesta(grupo, usuarioId))
                .toList();
    }

    @Transactional(readOnly = true)
    public GrupoResponse detalle(UUID usuarioId, UUID grupoId) {
        Grupo grupo = exigirMiembro(usuarioId, grupoId).getGrupo();
        return respuesta(grupo, usuarioId);
    }

    /**
     * Quien crea el grupo queda de organizador y como imprescindible (HU-14).
     * El grupo nace con código de invitación; quien organiza puede cambiarlo o
     * desactivarlo.
     */
    @Transactional
    public GrupoResponse crear(UUID usuarioId, GrupoRequests.Crear req) {
        Usuario creador = usuarioOFallar(usuarioId);
        // El admin opera la plataforma; no participa en ella.
        if (creador.getRolSistema() == Usuario.RolSistema.ADMIN) {
            throw new ForbiddenException("Una cuenta de administración no crea ni integra grupos.");
        }

        Grupo grupo = grupoRepository.save(Grupo.builder()
                .nombre(req.nombre().trim())
                .descripcion(req.descripcion() == null ? null : req.descripcion().trim())
                .umbralDisponibilidad(req.umbralDisponibilidad() == null
                        ? Grupo.UMBRAL_POR_DEFECTO
                        : req.umbralDisponibilidad())
                .creadoPor(creador)
                .codigoInvitacion(codigoLibre())
                .build());

        miembroGrupoRepository.save(MiembroGrupo.builder()
                .grupo(grupo)
                .usuario(creador)
                .rol(MiembroGrupo.Rol.ORGANIZADOR)
                .esImprescindible(true)
                .build());

        return respuesta(grupo, usuarioId);
    }

    /** RF-06: cambiar el umbral, el nombre o la descripción. Solo el organizador. */
    @Transactional
    public GrupoResponse actualizar(UUID usuarioId, UUID grupoId, GrupoRequests.Actualizar req) {
        Grupo grupo = exigirOrganizador(usuarioId, grupoId).getGrupo();

        if (req.nombre() != null) {
            if (req.nombre().isBlank()) {
                throw new BusinessException("El nombre del grupo no puede quedar vacío");
            }
            grupo.setNombre(req.nombre().trim());
        }
        if (req.descripcion() != null) {
            grupo.setDescripcion(req.descripcion().trim());
        }
        if (req.umbralDisponibilidad() != null) {
            grupo.setUmbralDisponibilidad(req.umbralDisponibilidad());
        }

        return respuesta(grupoRepository.save(grupo), usuarioId);
    }

    /* ------------------------------------------------------------------ *
     * Membresía
     * ------------------------------------------------------------------ */

    /**
     * Da de alta a alguien en el grupo, por correo. Solo el organizador.
     *
     * Convive con el código de invitación (unirse): por correo la entrada la
     * decide quien organiza persona a persona; con el código, la decide al
     * repartirlo.
     *
     * Es idempotente: volver a añadir a alguien que ya está devuelve el grupo
     * tal cual. El resultado que pedía la llamada ya se cumple, y un 400
     * obligaría a la interfaz a distinguir un caso que no le aporta nada.
     */
    @Transactional
    public GrupoResponse agregarMiembro(UUID usuarioId, UUID grupoId, String email) {
        // Dos altas simultáneas del mismo correo (doble clic) pasaban las dos la
        // comprobación de abajo y la segunda fallaba con un 500.
        grupoRepository.bloquearPorId(grupoId);
        Grupo grupo = exigirOrganizador(usuarioId, grupoId).getGrupo();

        Usuario nuevo = usuarioRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException(
                        "No hay ninguna cuenta de Huecko con ese correo"));
        if (nuevo.getRolSistema() == Usuario.RolSistema.ADMIN) {
            throw new BusinessException("Esa cuenta es de administración y no puede unirse a grupos.");
        }

        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(grupoId, nuevo.getId())) {
            miembroGrupoRepository.save(MiembroGrupo.builder()
                    .grupo(grupo)
                    .usuario(nuevo)
                    .rol(MiembroGrupo.Rol.MIEMBRO)
                    .esImprescindible(false)
                    .build());
        }

        return respuesta(grupo, usuarioId);
    }

    /**
     * Unirse con el código que pasó quien organiza (como en la app móvil).
     *
     * El código se quitó en su día porque cualquiera que lo viera en una
     * captura entraba solo y el grupo se enteraba tarde. Por eso ahora solo lo
     * ve quien organiza, se puede cambiar o desactivar, los fallos tienen
     * límite y el grupo recibe un aviso en vivo con cada entrada.
     *
     * Es idempotente: si ya estabas, devuelve el grupo sin avisar a nadie.
     */
    @Transactional
    public GrupoResponse unirse(UUID usuarioId, String codigo) {
        String clave = usuarioId.toString();
        Instant ahora = Instant.now();
        if (codigosFallidos.bloqueado(clave, ahora)) {
            throw new DemasiadosIntentosException(
                    "Demasiados códigos incorrectos seguidos. Espera unos minutos y vuelve a intentarlo.");
        }
        Grupo grupo = grupoRepository.findByCodigoInvitacion(CodigosDeInvitacion.normalizar(codigo)).orElse(null);
        if (grupo == null) {
            codigosFallidos.registrar(clave, ahora);
            throw new NotFoundException("Código de invitación inválido o desactivado.");
        }

        Usuario yo = usuarioOFallar(usuarioId);
        if (yo.getRolSistema() == Usuario.RolSistema.ADMIN) {
            throw new ForbiddenException("Una cuenta de administración no crea ni integra grupos.");
        }
        // Mismo cerrojo que el alta por correo: dos entradas a la vez no duplican la fila.
        grupoRepository.bloquearPorId(grupo.getId());
        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(grupo.getId(), usuarioId)) {
            miembroGrupoRepository.save(MiembroGrupo.builder()
                    .grupo(grupo)
                    .usuario(yo)
                    .rol(MiembroGrupo.Rol.MIEMBRO)
                    .esImprescindible(false)
                    .build());
            eventos.publishEvent(new MiembroUnido(grupo.getId(), usuarioId, yo.getNombre()));
        }
        codigosFallidos.olvidar(clave);
        return respuesta(grupo, usuarioId);
    }

    /** Un código nuevo; el anterior deja de servir. Solo el organizador. */
    @Transactional
    public GrupoResponse nuevoCodigo(UUID usuarioId, UUID grupoId) {
        Grupo grupo = exigirOrganizador(usuarioId, grupoId).getGrupo();
        grupo.setCodigoInvitacion(codigoLibre());
        return respuesta(grupoRepository.save(grupo), usuarioId);
    }

    /** Nadie más entra con código hasta generar otro. Solo el organizador. */
    @Transactional
    public GrupoResponse desactivarCodigo(UUID usuarioId, UUID grupoId) {
        Grupo grupo = exigirOrganizador(usuarioId, grupoId).getGrupo();
        grupo.setCodigoInvitacion(null);
        return respuesta(grupoRepository.save(grupo), usuarioId);
    }

    /** HU-14: marcar imprescindible o repartir la organización. Solo el organizador. */
    @Transactional
    public GrupoResponse actualizarMiembro(UUID usuarioId, UUID grupoId, UUID objetivoId,
                                           GrupoRequests.ActualizarMiembro req) {
        grupoRepository.bloquearPorId(grupoId);
        exigirOrganizador(usuarioId, grupoId);

        MiembroGrupo objetivo = miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(grupoId, objetivoId)
                .orElseThrow(() -> new NotFoundException("Esa persona no pertenece al grupo"));

        if (req.esImprescindible() != null) {
            objetivo.setEsImprescindible(req.esImprescindible());
        }
        if (req.rol() != null && req.rol() != objetivo.getRol()) {
            if (objetivo.getRol() == MiembroGrupo.Rol.ORGANIZADOR && esElUnicoOrganizador(grupoId)) {
                throw new BusinessException(
                        "El grupo se quedaría sin organizador. Nombra a otro antes de quitarte el rol.");
            }
            objetivo.setRol(req.rol());
        }

        miembroGrupoRepository.save(objetivo);
        return respuesta(objetivo.getGrupo(), usuarioId);
    }

    /**
     * Salirse del grupo, o sacar a alguien si eres el organizador.
     *
     * Si se va el último integrante el grupo se borra: dejarlo vacío solo
     * generaría un grupo fantasma que nadie puede volver a abrir, porque la
     * lista se consulta por membresía. Si tiene planes no se borra: las
     * votaciones y planes cuelgan de él, y el borrado fallaba por la clave
     * foránea dejando a la persona atrapada en el grupo. Queda sin integrantes,
     * que a efectos de la app es lo mismo, y sus planes aún en votación se
     * cancelan: nadie va a votarlos ni a cerrarlos, y el barrido los acabaría
     * confirmando con los votos de gente que ya no está.
     *
     * Quien se va se lleva sus votos de lo que sigue abierto (planes en
     * votación y votaciones exprés). Los de planes ya cerrados se quedan: son
     * el histórico de cómo se decidió.
     */
    @Transactional
    public void salir(UUID usuarioId, UUID grupoId, UUID objetivoId) {
        grupoRepository.bloquearPorId(grupoId);
        MiembroGrupo propio = exigirMiembro(usuarioId, grupoId);

        boolean esOtraPersona = !usuarioId.equals(objetivoId);
        if (esOtraPersona && propio.getRol() != MiembroGrupo.Rol.ORGANIZADOR) {
            throw new ForbiddenException("Solo el organizador puede sacar a alguien del grupo");
        }

        MiembroGrupo objetivo = miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(grupoId, objetivoId)
                .orElseThrow(() -> new NotFoundException("Esa persona no pertenece al grupo"));

        if (objetivo.getRol() == MiembroGrupo.Rol.ORGANIZADOR
                && esElUnicoOrganizador(grupoId)
                && miembroGrupoRepository.countByGrupo_Id(grupoId) > 1) {
            throw new BusinessException(
                    "Eres el único organizador. Nombra a otro antes de salir del grupo.");
        }

        miembroGrupoRepository.delete(objetivo);
        votoVentanaRepository.deleteAll(votoVentanaRepository.findByUsuarioEnGrupoConEstado(
                objetivoId, grupoId, Plan.Estado.PROPUESTO));

        if (miembroGrupoRepository.countByGrupo_Id(grupoId) == 0) {
            if (grupoRepository.tienePlanes(grupoId)) {
                cancelarPlanesEnVotacion(grupoId);
            } else {
                grupoRepository.delete(objetivo.getGrupo());
            }
        }

        // Va a Mongo, fuera de la transacción. Se hace al final para que un
        // fallo de las comprobaciones de arriba no le quite el voto a nadie.
        votacionExpresOperaciones.retirarVotosDe(grupoId.toString(), objetivoId.toString());

        // Su suscripción viva al grupo se corta tras el commit (SesionesTiempoReal).
        eventos.publishEvent(new AccesoRevocado(objetivoId));
    }

    private void cancelarPlanesEnVotacion(UUID grupoId) {
        Instant ahora = Instant.now();
        List<Plan> abiertos = planRepository.findByGrupo_IdAndEstado(grupoId, Plan.Estado.PROPUESTO);
        abiertos.forEach(plan -> {
            plan.setEstado(Plan.Estado.CANCELADO);
            plan.setCerradoEn(ahora);
        });
        planRepository.saveAll(abiertos);
    }

    /* ------------------------------------------------------------------ *
     * Cruce de disponibilidad
     * ------------------------------------------------------------------ */

    /**
     * RF-05 / RF-06 / RF-07.
     *
     * El umbral de la consulta tiene los mismos límites (50–100) que el que se
     * guarda al crear o editar el grupo: con un 10 % el heatmap sugería huecos
     * que el propio grupo no podría adoptar como ajuste.
     *
     * `umbral` permite mirar el heatmap con otro porcentaje sin tocar el ajuste
     * del grupo — el organizador prueba al 70 % antes de decidir si lo cambia.
     * `semana` mueve la ventana de siete días, que solo afecta a los bloques
     * puntuales; los recurrentes salen igual en cualquier semana.
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse disponibilidad(UUID usuarioId, UUID grupoId,
                                                 Integer umbral, LocalDate semana) {
        Grupo grupo = exigirMiembro(usuarioId, grupoId).getGrupo();

        // Solo se valida el de la petición: el guardado ya pasó por las mismas
        // reglas, y rechazarlo aquí dejaría sin heatmap a un grupo antiguo.
        if (umbral != null && (umbral < Grupo.UMBRAL_MINIMO || umbral > 100)) {
            throw new BusinessException("El umbral debe estar entre " + Grupo.UMBRAL_MINIMO + " y 100");
        }
        int umbralEfectivo = umbral == null ? grupo.getUmbralDisponibilidad() : umbral;

        List<UUID> miembros = miembroGrupoRepository.findUsuarioIdsByGrupoId(grupoId);

        // Solo bloques CONFIRMADOS: un borrador de OCR sin revisar no puede
        // bloquear el hueco de todo un grupo (RNF-06).
        List<BloqueHorario> bloques = miembros.isEmpty()
                ? List.of()
                : bloqueHorarioRepository.findByUsuarioIdInAndEstado(
                        miembros.stream().map(UUID::toString).toList(),
                        BloqueHorario.Estado.CONFIRMADO);

        return calculadora.calcular(grupoId, miembros, bloques, umbralEfectivo,
                semana == null ? ZonaHoraria.hoy() : semana);
    }

    /* ------------------------------------------------------------------ *
     * Apoyo
     * ------------------------------------------------------------------ */

    /**
     * Organizadores primero y después por nombre. La consulta ordena por el
     * texto del rol, y "MIEMBRO" va antes que "ORGANIZADOR" en el alfabeto; el
     * frontend asigna los colores por posición y espera ese orden.
     */
    private static final Comparator<MiembroGrupo> ORDEN_DE_MIEMBROS =
            Comparator.comparing((MiembroGrupo m) -> m.getRol() != MiembroGrupo.Rol.ORGANIZADOR)
                    .thenComparing(m -> m.getUsuario().getNombre(), String.CASE_INSENSITIVE_ORDER);

    /** El grupo visto por `usuarioId`: el código de invitación solo lo ve quien organiza. */
    private GrupoResponse respuesta(Grupo grupo, UUID usuarioId) {
        List<MiembroResponse> miembros = miembrosDe(grupo.getId());
        boolean organiza = miembros.stream()
                .anyMatch(m -> m.usuarioId().equals(usuarioId) && m.rol() == MiembroGrupo.Rol.ORGANIZADOR);
        return GrupoResponse.from(grupo, miembros, organiza);
    }

    /**
     * Un código que ningún grupo usa. Con 32⁸ combinaciones chocar es casi
     * imposible, pero si pasa se prueba otro en vez de romper el índice único.
     */
    private String codigoLibre() {
        for (int intento = 0; intento < INTENTOS_DE_CODIGO_LIBRE; intento++) {
            String codigo = CodigosDeInvitacion.nuevo();
            if (!grupoRepository.existsByCodigoInvitacion(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de invitación libre");
    }

    private List<MiembroResponse> miembrosDe(UUID grupoId) {
        return miembroGrupoRepository.findByGrupoIdConUsuario(grupoId).stream()
                .sorted(ORDEN_DE_MIEMBROS)
                .map(MiembroResponse::from)
                .toList();
    }

    /**
     * Un grupo al que no perteneces se responde como inexistente, no como
     * prohibido: un 403 confirmaría que ese id existe a quien solo está
     * probando identificadores.
     */
    private MiembroGrupo exigirMiembro(UUID usuarioId, UUID grupoId) {
        return miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(grupoId, usuarioId)
                .orElseThrow(() -> new NotFoundException("El grupo no existe o no perteneces a él"));
    }

    private MiembroGrupo exigirOrganizador(UUID usuarioId, UUID grupoId) {
        MiembroGrupo miembro = exigirMiembro(usuarioId, grupoId);
        if (miembro.getRol() != MiembroGrupo.Rol.ORGANIZADOR) {
            throw new ForbiddenException("Solo el organizador del grupo puede hacer este cambio");
        }
        return miembro;
    }

    private boolean esElUnicoOrganizador(UUID grupoId) {
        return miembroGrupoRepository.findByGrupoIdConUsuario(grupoId).stream()
                .filter(m -> m.getRol() == MiembroGrupo.Rol.ORGANIZADOR)
                .count() <= 1;
    }

    private Usuario usuarioOFallar(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("El usuario del token ya no existe"));
    }
}
