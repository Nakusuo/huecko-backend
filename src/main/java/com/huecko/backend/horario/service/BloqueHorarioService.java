package com.huecko.backend.horario.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.horario.dto.BloqueHorarioRequest;
import com.huecko.backend.horario.dto.BloqueHorarioResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BloqueHorarioService {

    private final BloqueHorarioRepository bloqueHorarioRepository;

    /** RF-01 / RF-04: crear un bloque manual (recurrente o puntual). */
    public BloqueHorarioResponse crear(String usuarioId, BloqueHorarioRequest req) {
        validarCoherenciaTipo(req);

        // RF-02/RF-03: lo que llega marcado como OCR nace en BORRADOR y no cuenta
        // como disponibilidad real hasta que el usuario lo confirma (RNF-06).
        BloqueHorario.Fuente fuente = req.fuente() == null ? BloqueHorario.Fuente.MANUAL : req.fuente();
        BloqueHorario.Estado estado = fuente == BloqueHorario.Fuente.OCR
                ? BloqueHorario.Estado.BORRADOR
                : BloqueHorario.Estado.CONFIRMADO;

        Instant ahora = Instant.now();
        BloqueHorario bloque = BloqueHorario.builder()
                .usuarioId(usuarioId)
                .tipo(req.tipo())
                .diaSemana(req.diaSemana())
                .fecha(req.fecha())
                .fechaFin(req.fechaFin())
                .horaInicio(req.horaInicio())
                .horaFin(req.horaFin())
                .etiqueta(req.etiqueta())
                .categoria(req.categoria())
                .color(req.color())
                .fuente(fuente)
                .estado(estado)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();

        return BloqueHorarioResponse.from(bloqueHorarioRepository.save(bloque));
    }

    /** RF-04 (edición) / RF-03 (confirmar o corregir un borrador de OCR). */
    public BloqueHorarioResponse actualizar(String usuarioId, String bloqueId, BloqueHorarioRequest req) {
        validarCoherenciaTipo(req);
        BloqueHorario bloque = obtenerDelUsuarioOFallar(usuarioId, bloqueId);

        bloque.setTipo(req.tipo());
        bloque.setDiaSemana(req.diaSemana());
        bloque.setFecha(req.fecha());
        bloque.setFechaFin(req.fechaFin());
        bloque.setHoraInicio(req.horaInicio());
        bloque.setHoraFin(req.horaFin());
        bloque.setEtiqueta(req.etiqueta());
        bloque.setCategoria(req.categoria());
        bloque.setColor(req.color());
        // Al editar/confirmar, un borrador de OCR pasa a confirmado (RF-03).
        // La `fuente` original NO se toca: sigue siendo trazable que vino de OCR.
        bloque.setEstado(BloqueHorario.Estado.CONFIRMADO);
        bloque.setActualizadoEn(Instant.now());

        return BloqueHorarioResponse.from(bloqueHorarioRepository.save(bloque));
    }

    /** RF-04: eliminar un bloque propio. */
    public void eliminar(String usuarioId, String bloqueId) {
        BloqueHorario bloque = obtenerDelUsuarioOFallar(usuarioId, bloqueId);
        bloqueHorarioRepository.delete(bloque);
    }

    /** Lista todos los bloques confirmados de un usuario (base para el cruce del Módulo 2). */
    public List<BloqueHorarioResponse> listarConfirmados(String usuarioId) {
        return bloqueHorarioRepository
                .findByUsuarioIdAndEstado(usuarioId, BloqueHorario.Estado.CONFIRMADO)
                .stream()
                .map(BloqueHorarioResponse::from)
                .toList();
    }

    /** Lista los borradores pendientes de revisión de OCR (RF-03). */
    public List<BloqueHorarioResponse> listarBorradores(String usuarioId) {
        return bloqueHorarioRepository
                .findByUsuarioIdAndEstado(usuarioId, BloqueHorario.Estado.BORRADOR)
                .stream()
                .map(BloqueHorarioResponse::from)
                .toList();
    }

    /**
     * Un bloque que no existe da 404; uno que existe pero es de otra persona da 403.
     * Antes ambos casos eran un 400 genérico, y la UI no podía distinguir
     * "esto ya no está" de "esto no es tuyo".
     */
    private BloqueHorario obtenerDelUsuarioOFallar(String usuarioId, String bloqueId) {
        BloqueHorario bloque = bloqueHorarioRepository.findById(bloqueId)
                .orElseThrow(() -> new NotFoundException("Bloque de horario no encontrado: " + bloqueId));
        if (!bloque.getUsuarioId().equals(usuarioId)) {
            throw new ForbiddenException("El bloque no pertenece al usuario autenticado");
        }
        return bloque;
    }

    private void validarCoherenciaTipo(BloqueHorarioRequest req) {
        if (req.tipo() == BloqueHorario.Tipo.RECURRENTE && req.diaSemana() == null) {
            throw new BusinessException("Un bloque recurrente requiere diaSemana");
        }
        if (req.tipo() == BloqueHorario.Tipo.PUNTUAL && req.fecha() == null) {
            throw new BusinessException("Un bloque puntual requiere fecha");
        }
        if (req.tipo() == BloqueHorario.Tipo.PUNTUAL
                && req.fechaFin() != null
                && req.fechaFin().isBefore(req.fecha())) {
            throw new BusinessException("fechaFin no puede ser anterior a fecha");
        }
        if (!req.horaFin().isAfter(req.horaInicio())) {
            throw new BusinessException("horaFin debe ser posterior a horaInicio");
        }
    }
}
