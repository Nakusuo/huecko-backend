package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.GrupoAdminResponse;
import com.huecko.backend.admin.dto.ResumenAdminResponse;
import com.huecko.backend.admin.dto.UsuarioAdminResponse;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.mongo.repository.AlertaRetrasoRepository;
import com.huecko.backend.mongo.repository.AusenciaRepository;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.mongo.repository.VotacionExpresRepository;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.postgres.repository.VotoVentanaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lecturas del panel de administración. Solo lo usa `/api/admin/**`, que exige ADMIN. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UsuarioRepository usuarioRepository;
    private final GrupoRepository grupoRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final PlanRepository planRepository;
    private final VotoVentanaRepository votoVentanaRepository;
    private final BloqueHorarioRepository bloqueHorarioRepository;
    private final AlertaRetrasoRepository alertaRetrasoRepository;
    private final AusenciaRepository ausenciaRepository;
    private final VotacionExpresRepository votacionExpresRepository;

    @Value("${huecko.imprevistos.dias-purga:7}")
    private int diasPurga;

    @Transactional(readOnly = true)
    public ResumenAdminResponse resumen() {
        return CalculoResumen.calcular(cargarDatos(), Instant.now(), ZonaHoraria.ZONA);
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> usuarios() {
        return ListadoUsuarios.listar(cargarDatos());
    }

    @Transactional(readOnly = true)
    public List<GrupoAdminResponse> grupos() {
        return ListadoGrupos.listar(cargarDatos());
    }

    /**
     * Suspende o reactiva una cuenta. A un admin no se le puede suspender: sin
     * esa regla, un admin podía dejar la plataforma sin nadie que la opere,
     * empezando por sí mismo.
     */
    @Transactional
    public UsuarioAdminResponse cambiarSuspension(UUID usuarioId, boolean suspendido) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("Esa cuenta no existe."));
        if (usuario.getRolSistema() == Usuario.RolSistema.ADMIN) {
            throw new BusinessException("No se puede suspender a un administrador.");
        }
        usuario.setSuspendido(suspendido);
        usuarioRepository.save(usuario);

        String id = usuarioId.toString();
        return usuarios().stream().filter(u -> u.id().equals(id)).findFirst().orElseThrow();
    }

    private CalculoResumen.Datos cargarDatos() {
        return new CalculoResumen.Datos(
                usuarioRepository.findAll(),
                grupoRepository.findAll(),
                miembroGrupoRepository.findAll(),
                planRepository.findAll(),
                votoVentanaRepository.findAll(),
                bloqueHorarioRepository.findAll(),
                alertaRetrasoRepository.findAll(),
                ausenciaRepository.findAll(),
                votacionExpresRepository.findAll(),
                diasPurga);
    }
}
