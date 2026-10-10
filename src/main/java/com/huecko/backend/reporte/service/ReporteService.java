package com.huecko.backend.reporte.service;

import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.ReporteUsuario;
import com.huecko.backend.mongo.repository.ReporteUsuarioRepository;
import com.huecko.backend.observabilidad.service.RegistroFallos;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.reporte.dto.ReporteRequests;
import com.huecko.backend.reporte.dto.ReporteResponse;
import com.huecko.backend.tiemporeal.AccesoRevocado;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reportes de las personas y errores del navegador.
 *
 * El admin no navega cuentas: la única acción que tiene sobre una es
 * suspenderla, y solo desde un reporte de CONDUCTA que la señala.
 */
@Service
public class ReporteService {

    /** Reportes a mano por persona y hora. Frena el spam sin molestar a nadie de verdad. */
    static final int MAX_REPORTES_POR_HORA = 5;

    /** Errores de navegador por persona cada 10 minutos. Un bucle de errores no llena la bandeja. */
    static final int MAX_ERRORES_CLIENTE = 20;
    static final Duration VENTANA_ERRORES = Duration.ofMinutes(10);

    private final ReporteUsuarioRepository reportes;
    private final UsuarioRepository usuarios;
    private final RegistroFallos registroFallos;
    private final ApplicationEventPublisher eventos;
    /** Acotado en claves: con el registro abierto, un mapa por usuario crecía sin fin. */
    private final LimitadorDeIntentos erroresRecientes =
            new LimitadorDeIntentos(MAX_ERRORES_CLIENTE, VENTANA_ERRORES, 10_000);

    public ReporteService(ReporteUsuarioRepository reportes, UsuarioRepository usuarios,
                          RegistroFallos registroFallos, ApplicationEventPublisher eventos) {
        this.reportes = reportes;
        this.usuarios = usuarios;
        this.registroFallos = registroFallos;
        this.eventos = eventos;
    }

    public void crear(UsuarioAutenticado autor, ReporteRequests.Crear req, String navegador) {
        Instant ahora = Instant.now();
        if (reportes.countByAutorIdAndCreadoEnAfter(autor.id().toString(), ahora.minus(Duration.ofHours(1)))
                >= MAX_REPORTES_POR_HORA) {
            throw new BusinessException("Ya enviaste varios reportes en la última hora. Espera un poco antes de mandar otro.");
        }

        ReporteUsuario.ReporteUsuarioBuilder reporte = ReporteUsuario.builder()
                .tipo(req.tipo())
                .descripcion(req.descripcion().trim())
                .ruta(req.ruta())
                .navegador(navegador == null ? null : navegador.substring(0, Math.min(navegador.length(), 300)))
                .autorId(autor.id().toString())
                .autorNombre(autor.nombre())
                .autorEmail(autor.email())
                .estado(EstadoRevision.NUEVO)
                .creadoEn(ahora)
                .actualizadoEn(ahora);

        if (req.tipo() == ReporteUsuario.Tipo.CONDUCTA) {
            Usuario reportada = cuentaReportada(autor, req.emailReportado());
            reporte.cuentaReportadaId(reportada.getId().toString())
                    .cuentaReportadaNombre(reportada.getNombre())
                    .cuentaReportadaEmail(reportada.getEmail());
        }

        reportes.save(reporte.build());
    }

    private Usuario cuentaReportada(UsuarioAutenticado autor, String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("Indica el correo de la cuenta de la que informas.");
        }
        Usuario cuenta = usuarios.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException("No hay ninguna cuenta de Huecko con ese correo."));
        if (cuenta.getId().equals(autor.id())) {
            throw new BusinessException("No puedes reportar tu propia cuenta.");
        }
        if (cuenta.getRolSistema() == Usuario.RolSistema.ADMIN) {
            throw new BusinessException("Esa cuenta es de administración.");
        }
        return cuenta;
    }

    /** Devuelve si se registró: con el límite superado se descarta en silencio. */
    public boolean registrarErrorCliente(UsuarioAutenticado autor, ReporteRequests.ErrorCliente req, String navegador) {
        if (!dentroDelLimite(autor.id(), Instant.now())) {
            return false;
        }
        registroFallos.registrarCliente(req.tipo(), req.mensaje(), req.traza(), req.ruta(), navegador);
        return true;
    }

    boolean dentroDelLimite(UUID usuarioId, Instant ahora) {
        return erroresRecientes.permitir(usuarioId.toString(), ahora);
    }

    /* ------------------------------ Admin ------------------------------ */

    /** Los nuevos primero y, dentro de cada estado, los más recientes. */
    @Transactional(readOnly = true)
    public List<ReporteResponse> listar() {
        List<ReporteUsuario> todos = reportes.findAll();
        Map<String, Usuario> cuentas = usuarios.findAllById(todos.stream()
                        .map(ReporteUsuario::getCuentaReportadaId)
                        .filter(Objects::nonNull)
                        .map(UUID::fromString)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(u -> u.getId().toString(), Function.identity()));

        return todos.stream()
                .sorted(Comparator.comparing(ReporteUsuario::getEstado)
                        .thenComparing(ReporteUsuario::getCreadoEn, Comparator.reverseOrder()))
                .map(r -> respuesta(r, cuentas))
                .toList();
    }

    public ReporteResponse cambiarEstado(String id, EstadoRevision estado) {
        ReporteUsuario reporte = buscar(id);
        reporte.setEstado(estado);
        reporte.setActualizadoEn(Instant.now());
        reportes.save(reporte);
        return respuesta(reporte, cuentasDe(reporte));
    }

    /**
     * Suspende o reactiva la cuenta señalada por un reporte de CONDUCTA. Al
     * actuar sobre él, un reporte NUEVO pasa a REVISADO.
     */
    @Transactional
    public ReporteResponse cambiarSuspension(String id, boolean suspendido) {
        ReporteUsuario reporte = buscar(id);
        if (reporte.getTipo() != ReporteUsuario.Tipo.CONDUCTA || reporte.getCuentaReportadaId() == null) {
            throw new BusinessException("Este reporte no señala a ninguna cuenta.");
        }
        Usuario cuenta = usuarios.findById(UUID.fromString(reporte.getCuentaReportadaId()))
                .orElseThrow(() -> new NotFoundException("La cuenta reportada ya no existe."));
        if (cuenta.getRolSistema() == Usuario.RolSistema.ADMIN) {
            throw new BusinessException("No se puede suspender a un administrador.");
        }
        cuenta.setSuspendido(suspendido);
        usuarios.save(cuenta);
        if (suspendido) {
            // El tiempo real no pasa por el filtro HTTP: sin esto seguía recibiendo eventos.
            eventos.publishEvent(new AccesoRevocado(cuenta.getId()));
        }

        if (reporte.getEstado() == EstadoRevision.NUEVO) {
            reporte.setEstado(EstadoRevision.REVISADO);
        }
        reporte.setActualizadoEn(Instant.now());
        reportes.save(reporte);
        return ReporteResponse.from(reporte, cuenta.isSuspendido());
    }

    private ReporteUsuario buscar(String id) {
        return reportes.findById(id).orElseThrow(() -> new NotFoundException("Ese reporte no existe."));
    }

    private Map<String, Usuario> cuentasDe(ReporteUsuario reporte) {
        if (reporte.getCuentaReportadaId() == null) {
            return Map.of();
        }
        return usuarios.findById(UUID.fromString(reporte.getCuentaReportadaId()))
                .map(u -> Map.of(u.getId().toString(), u))
                .orElse(Map.of());
    }

    private static ReporteResponse respuesta(ReporteUsuario r, Map<String, Usuario> cuentas) {
        Usuario cuenta = r.getCuentaReportadaId() == null ? null : cuentas.get(r.getCuentaReportadaId());
        return ReporteResponse.from(r, cuenta == null ? null : cuenta.isSuspendido());
    }
}
