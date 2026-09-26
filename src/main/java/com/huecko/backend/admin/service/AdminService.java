package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.ResumenAdminResponse;
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
        var datos = new CalculoResumen.Datos(
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
        return CalculoResumen.calcular(datos, Instant.now(), ZonaHoraria.ZONA);
    }
}
