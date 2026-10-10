package com.huecko.backend.reporte.service;

import com.huecko.backend.auth.service.UsuarioAutenticado;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReporteServiceTest {

    private final ReporteUsuarioRepository reportes = mock(ReporteUsuarioRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RegistroFallos fallos = mock(RegistroFallos.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final ReporteService servicio = new ReporteService(reportes, usuarios, fallos, eventos);

    private final UsuarioAutenticado ana = new UsuarioAutenticado(UUID.randomUUID(), "ana@h.com", "Ana");

    private static Usuario cuenta(String email, Usuario.RolSistema rol) {
        return Usuario.builder().id(UUID.randomUUID()).nombre(email).email(email).passwordHash("h").rolSistema(rol).build();
    }

    private ReporteRequests.Crear conducta(String email) {
        return new ReporteRequests.Crear(ReporteUsuario.Tipo.CONDUCTA, "Me insulta en el grupo", "/groups/1", email);
    }

    @Test
    @DisplayName("un reporte de fallo guarda autor, página y estado NUEVO")
    void fallo() {
        servicio.crear(ana, new ReporteRequests.Crear(ReporteUsuario.Tipo.FALLO, "  No carga el horario  ", "/schedule", null), "UA");

        ArgumentCaptor<ReporteUsuario> guardado = ArgumentCaptor.forClass(ReporteUsuario.class);
        verify(reportes).save(guardado.capture());
        assertThat(guardado.getValue().getDescripcion()).isEqualTo("No carga el horario");
        assertThat(guardado.getValue().getAutorEmail()).isEqualTo("ana@h.com");
        assertThat(guardado.getValue().getEstado()).isEqualTo(EstadoRevision.NUEVO);
        assertThat(guardado.getValue().getCuentaReportadaId()).isNull();
    }

    @Test
    @DisplayName("un reporte de conducta señala una cuenta existente, que no sea la propia ni un admin")
    void conductaReglas() {
        Usuario beto = cuenta("beto@h.com", Usuario.RolSistema.USUARIO);
        Usuario admin = cuenta("admin@h.com", Usuario.RolSistema.ADMIN);
        Usuario yo = Usuario.builder().id(ana.id()).nombre("Ana").email("ana@h.com").passwordHash("h").build();
        when(usuarios.findByEmailIgnoreCase("beto@h.com")).thenReturn(Optional.of(beto));
        when(usuarios.findByEmailIgnoreCase("admin@h.com")).thenReturn(Optional.of(admin));
        when(usuarios.findByEmailIgnoreCase("ana@h.com")).thenReturn(Optional.of(yo));
        when(usuarios.findByEmailIgnoreCase("nadie@h.com")).thenReturn(Optional.empty());

        servicio.crear(ana, conducta("beto@h.com"), null);
        ArgumentCaptor<ReporteUsuario> guardado = ArgumentCaptor.forClass(ReporteUsuario.class);
        verify(reportes).save(guardado.capture());
        assertThat(guardado.getValue().getCuentaReportadaId()).isEqualTo(beto.getId().toString());

        assertThatThrownBy(() -> servicio.crear(ana, conducta(null), null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> servicio.crear(ana, conducta("nadie@h.com"), null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> servicio.crear(ana, conducta("ana@h.com"), null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> servicio.crear(ana, conducta("admin@h.com"), null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("más de 5 reportes en una hora se rechazan")
    void limitePorHora() {
        when(reportes.countByAutorIdAndCreadoEnAfter(anyString(), any())).thenReturn(5L);

        assertThatThrownBy(() -> servicio.crear(ana,
                new ReporteRequests.Crear(ReporteUsuario.Tipo.FALLO, "Algo va mal aquí", null, null), null))
                .isInstanceOf(BusinessException.class);
        verify(reportes, never()).save(any());
    }

    @Test
    @DisplayName("los errores del navegador tienen un tope por persona cada 10 minutos")
    void limiteErrores() {
        Instant ahora = Instant.now();
        for (int i = 0; i < ReporteService.MAX_ERRORES_CLIENTE; i++) {
            assertThat(servicio.dentroDelLimite(ana.id(), ahora)).isTrue();
        }
        assertThat(servicio.dentroDelLimite(ana.id(), ahora)).isFalse();
        assertThat(servicio.dentroDelLimite(ana.id(), ahora.plus(ReporteService.VENTANA_ERRORES).plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("desde un reporte de conducta se suspende la cuenta señalada y el reporte pasa a revisado")
    void suspender() {
        Usuario beto = cuenta("beto@h.com", Usuario.RolSistema.USUARIO);
        ReporteUsuario reporte = ReporteUsuario.builder().id("r1").tipo(ReporteUsuario.Tipo.CONDUCTA)
                .cuentaReportadaId(beto.getId().toString()).estado(EstadoRevision.NUEVO).creadoEn(Instant.now()).build();
        when(reportes.findById("r1")).thenReturn(Optional.of(reporte));
        when(usuarios.findById(beto.getId())).thenReturn(Optional.of(beto));

        ReporteResponse r = servicio.cambiarSuspension("r1", true);

        assertThat(beto.isSuspendido()).isTrue();
        assertThat(r.cuentaReportadaSuspendida()).isTrue();
        assertThat(r.estado()).isEqualTo(EstadoRevision.REVISADO);
        // Y se le corta el tiempo real, que no pasa por el filtro HTTP.
        verify(eventos).publishEvent(new AccesoRevocado(beto.getId()));
    }

    @Test
    @DisplayName("un reporte de fallo no permite suspender a nadie")
    void falloNoSuspende() {
        ReporteUsuario reporte = ReporteUsuario.builder().id("r2").tipo(ReporteUsuario.Tipo.FALLO)
                .estado(EstadoRevision.NUEVO).build();
        when(reportes.findById("r2")).thenReturn(Optional.of(reporte));

        assertThatThrownBy(() -> servicio.cambiarSuspension("r2", true)).isInstanceOf(BusinessException.class);
        verify(usuarios, never()).save(any());
    }
}
