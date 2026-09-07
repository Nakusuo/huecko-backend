package com.huecko.backend.horario.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.horario.dto.BloqueHorarioRequest;
import com.huecko.backend.horario.dto.BloqueHorarioResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El service se prueba con el repositorio simulado: no hace falta Mongo
 * levantado, así que estas pruebas corren en cualquier máquina y en CI.
 */
@ExtendWith(MockitoExtension.class)
class BloqueHorarioServiceTest {

    private static final String USUARIO = "11111111-1111-1111-1111-111111111111";
    private static final String OTRO_USUARIO = "22222222-2222-2222-2222-222222222222";

    @Mock
    private BloqueHorarioRepository repository;

    @InjectMocks
    private BloqueHorarioService service;

    @Test
    @DisplayName("crear() persiste categoría, color y fechaFin en vez de descartarlos")
    void crearGuardaLosCamposDePresentacion() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        BloqueHorarioRequest req = new BloqueHorarioRequest(
                BloqueHorario.Tipo.PUNTUAL,
                null,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 9),
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                "Viaje a Cusco",
                "Personal",
                "#22C55E",
                null);

        service.crear(USUARIO, req);

        ArgumentCaptor<BloqueHorario> guardado = ArgumentCaptor.forClass(BloqueHorario.class);
        verify(repository).save(guardado.capture());

        assertThat(guardado.getValue().getCategoria()).isEqualTo("Personal");
        assertThat(guardado.getValue().getColor()).isEqualTo("#22C55E");
        assertThat(guardado.getValue().getFechaFin()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(guardado.getValue().getUsuarioId()).isEqualTo(USUARIO);
    }

    @Test
    @DisplayName("RF-03: un bloque con fuente OCR nace en BORRADOR, no confirmado")
    void bloqueOcrNaceEnBorrador() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        service.crear(USUARIO, recurrente(BloqueHorario.Fuente.OCR));

        ArgumentCaptor<BloqueHorario> guardado = ArgumentCaptor.forClass(BloqueHorario.class);
        verify(repository).save(guardado.capture());

        assertThat(guardado.getValue().getFuente()).isEqualTo(BloqueHorario.Fuente.OCR);
        assertThat(guardado.getValue().getEstado()).isEqualTo(BloqueHorario.Estado.BORRADOR);
    }

    @Test
    @DisplayName("Sin fuente explícita el bloque es MANUAL y queda confirmado")
    void bloqueManualNaceConfirmado() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        service.crear(USUARIO, recurrente(null));

        ArgumentCaptor<BloqueHorario> guardado = ArgumentCaptor.forClass(BloqueHorario.class);
        verify(repository).save(guardado.capture());

        assertThat(guardado.getValue().getFuente()).isEqualTo(BloqueHorario.Fuente.MANUAL);
        assertThat(guardado.getValue().getEstado()).isEqualTo(BloqueHorario.Estado.CONFIRMADO);
    }

    @Test
    @DisplayName("RF-03: confirmar un borrador lo pasa a CONFIRMADO sin perder que vino de OCR")
    void confirmarBorradorConservaLaTrazabilidad() {
        BloqueHorario borrador = BloqueHorario.builder()
                .id("b1")
                .usuarioId(USUARIO)
                .tipo(BloqueHorario.Tipo.RECURRENTE)
                .diaSemana(4)
                .horaInicio(LocalTime.of(16, 0))
                .horaFin(LocalTime.of(18, 30))
                .fuente(BloqueHorario.Fuente.OCR)
                .estado(BloqueHorario.Estado.BORRADOR)
                .build();

        when(repository.findById("b1")).thenReturn(Optional.of(borrador));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        BloqueHorarioResponse resultado = service.actualizar(USUARIO, "b1", recurrente(null));

        assertThat(resultado.estado()).isEqualTo(BloqueHorario.Estado.CONFIRMADO);
        assertThat(resultado.fuente()).isEqualTo(BloqueHorario.Fuente.OCR);
    }

    @Test
    @DisplayName("Un bloque inexistente da 404, no un 400 genérico")
    void bloqueInexistenteEs404() {
        when(repository.findById("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(USUARIO, "fantasma"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Un bloque de otra persona da 403 y no se borra")
    void bloqueAjenoEs403() {
        BloqueHorario ajeno = BloqueHorario.builder()
                .id("b2")
                .usuarioId(OTRO_USUARIO)
                .build();
        when(repository.findById("b2")).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.eliminar(USUARIO, "b2"))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("Un bloque puntual con fechaFin anterior a fecha se rechaza")
    void rangoDeFechasInvertidoSeRechaza() {
        BloqueHorarioRequest req = new BloqueHorarioRequest(
                BloqueHorario.Tipo.PUNTUAL,
                null,
                LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 7),
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                "Rango al revés", null, null, null);

        assertThatThrownBy(() -> service.crear(USUARIO, req))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    private BloqueHorarioRequest recurrente(BloqueHorario.Fuente fuente) {
        return new BloqueHorarioRequest(
                BloqueHorario.Tipo.RECURRENTE,
                4,
                null,
                null,
                LocalTime.of(16, 0),
                LocalTime.of(18, 30),
                "Laboratorio de Redes",
                "Clase",
                "#7C3AED",
                fuente);
    }
}
