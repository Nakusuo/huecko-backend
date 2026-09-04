package com.huecko.backend.horario.service;

import com.huecko.backend.common.exception.BusinessException;
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

        BloqueHorario bloque = BloqueHorario.builder()
                .usuarioId(usuarioId)
                .tipo(req.tipo())
                .diaSemana(req.diaSemana())
                .fecha(req.fecha())
                .horaInicio(req.horaInicio())
                .horaFin(req.horaFin())
                .etiqueta(req.etiqueta())
                .fuente(BloqueHorario.Fuente.MANUAL)
                .estado(BloqueHorario.Estado.CONFIRMADO)
                .creadoEn(Instant.now())
                .actualizadoEn(Instant.now())
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
        bloque.setHoraInicio(req.horaInicio());
        bloque.setHoraFin(req.horaFin());
        bloque.setEtiqueta(req.etiqueta());
        // Al editar/confirmar, un borrador de OCR pasa a confirmado (RF-03)
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

    private BloqueHorario obtenerDelUsuarioOFallar(String usuarioId, String bloqueId) {
        BloqueHorario bloque = bloqueHorarioRepository.findById(bloqueId)
                .orElseThrow(() -> new BusinessException("Bloque de horario no encontrado: " + bloqueId));
        if (!bloque.getUsuarioId().equals(usuarioId)) {
            throw new BusinessException("El bloque no pertenece al usuario autenticado");
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
        if (!req.horaFin().isAfter(req.horaInicio())) {
            throw new BusinessException("horaFin debe ser posterior a horaInicio");
        }
    }
}
