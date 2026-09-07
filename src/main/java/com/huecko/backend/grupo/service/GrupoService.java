package com.huecko.backend.grupo.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.dto.GrupoRequests;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.dto.MiembroResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    private final GrupoRepository grupoRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BloqueHorarioRepository bloqueHorarioRepository;
    private final CalculadoraDisponibilidad calculadora;

    /* ------------------------------------------------------------------ *
     * Grupos
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public List<GrupoResponse> listar(UUID usuarioId) {
        return grupoRepository.findByMiembro(usuarioId).stream()
                .map(grupo -> GrupoResponse.from(grupo, miembrosDe(grupo.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GrupoResponse detalle(UUID usuarioId, UUID grupoId) {
        Grupo grupo = exigirMiembro(usuarioId, grupoId).getGrupo();
        return GrupoResponse.from(grupo, miembrosDe(grupoId));
    }

    /** Quien crea el grupo queda de organizador y como imprescindible (HU-14). */
    @Transactional
    public GrupoResponse crear(UUID usuarioId, GrupoRequests.Crear req) {
        Usuario creador = usuarioOFallar(usuarioId);

        Grupo grupo = grupoRepository.save(Grupo.builder()
                .nombre(req.nombre().trim())
                .descripcion(req.descripcion() == null ? null : req.descripcion().trim())
                .umbralDisponibilidad(req.umbralDisponibilidad() == null
                        ? Grupo.UMBRAL_POR_DEFECTO
                        : req.umbralDisponibilidad())
                .creadoPor(creador)
                .build());

        miembroGrupoRepository.save(MiembroGrupo.builder()
                .grupo(grupo)
                .usuario(creador)
                .rol(MiembroGrupo.Rol.ORGANIZADOR)
                .esImprescindible(true)
                .build());

        return GrupoResponse.from(grupo, miembrosDe(grupo.getId()));
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

        return GrupoResponse.from(grupoRepository.save(grupo), miembrosDe(grupoId));
    }

    /* ------------------------------------------------------------------ *
     * Membresía
     * ------------------------------------------------------------------ */

    /**
     * Da de alta a alguien en el grupo, por correo. Solo el organizador.
     *
     * Sustituye al antiguo «unirse por código». Con el código, cualquiera que
     * viera la cadena en una captura o en un chat entraba solo, y el grupo se
     * enteraba después; aquí la entrada la decide siempre quien organiza.
     *
     * Es idempotente: volver a añadir a alguien que ya está devuelve el grupo
     * tal cual. El resultado que pedía la llamada ya se cumple, y un 400
     * obligaría a la interfaz a distinguir un caso que no le aporta nada.
     */
    @Transactional
    public GrupoResponse agregarMiembro(UUID usuarioId, UUID grupoId, String email) {
        Grupo grupo = exigirOrganizador(usuarioId, grupoId).getGrupo();

        Usuario nuevo = usuarioRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException(
                        "No hay ninguna cuenta de Huecko con ese correo"));

        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(grupoId, nuevo.getId())) {
            miembroGrupoRepository.save(MiembroGrupo.builder()
                    .grupo(grupo)
                    .usuario(nuevo)
                    .rol(MiembroGrupo.Rol.MIEMBRO)
                    .esImprescindible(false)
                    .build());
        }

        return GrupoResponse.from(grupo, miembrosDe(grupoId));
    }

    /** HU-14: marcar imprescindible o repartir la organización. Solo el organizador. */
    @Transactional
    public GrupoResponse actualizarMiembro(UUID usuarioId, UUID grupoId, UUID objetivoId,
                                           GrupoRequests.ActualizarMiembro req) {
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
        return GrupoResponse.from(objetivo.getGrupo(), miembrosDe(grupoId));
    }

    /**
     * Salirse del grupo, o sacar a alguien si eres el organizador.
     *
     * Si se va el último integrante el grupo se borra: dejarlo vacío solo
     * generaría un grupo fantasma que nadie puede volver a abrir, porque la
     * lista se consulta por membresía.
     */
    @Transactional
    public void salir(UUID usuarioId, UUID grupoId, UUID objetivoId) {
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

        if (miembroGrupoRepository.countByGrupo_Id(grupoId) == 0) {
            grupoRepository.delete(objetivo.getGrupo());
        }
    }

    /* ------------------------------------------------------------------ *
     * Cruce de disponibilidad
     * ------------------------------------------------------------------ */

    /**
     * RF-05 / RF-06 / RF-07.
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

        int umbralEfectivo = umbral == null ? grupo.getUmbralDisponibilidad() : umbral;
        if (umbralEfectivo < 1 || umbralEfectivo > 100) {
            throw new BusinessException("El umbral debe estar entre 1 y 100");
        }

        List<UUID> miembros = miembroGrupoRepository.findUsuarioIdsByGrupoId(grupoId);

        // Solo bloques CONFIRMADOS: un borrador de OCR sin revisar no puede
        // bloquear el hueco de todo un grupo (RNF-06).
        List<BloqueHorario> bloques = miembros.isEmpty()
                ? List.of()
                : bloqueHorarioRepository.findByUsuarioIdInAndEstado(
                        miembros.stream().map(UUID::toString).toList(),
                        BloqueHorario.Estado.CONFIRMADO);

        return calculadora.calcular(grupoId, miembros, bloques, umbralEfectivo,
                semana == null ? LocalDate.now() : semana);
    }

    /* ------------------------------------------------------------------ *
     * Apoyo
     * ------------------------------------------------------------------ */

    private List<MiembroResponse> miembrosDe(UUID grupoId) {
        return miembroGrupoRepository.findByGrupoIdConUsuario(grupoId).stream()
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
