package com.huecko.backend.plan.service;

import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.entity.VotoVentana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-10. Es la regla que peor avisa cuando se rompe: un desempate mal resuelto
 * no lanza ninguna excepción, simplemente confirma el día equivocado.
 */
class SelectorVentanaGanadoraTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 9, 8);
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 10);

    private final SelectorVentanaGanadora selector = new SelectorVentanaGanadora();

    @Test
    @DisplayName("Gana la ventana con más votos")
    void ganaLaMasVotada() {
        VentanaPlan martes = ventana(MARTES, "10:00");
        VentanaPlan jueves = ventana(JUEVES, "16:00");

        Optional<VentanaPlan> ganadora = selector.elegir(
                List.of(martes, jueves),
                votos(martes, 2, jueves, 3));

        assertThat(ganadora).contains(jueves);
    }

    @Test
    @DisplayName("Sin ningún voto no hay ganadora: el plan no se confirma solo")
    void sinVotosNoHayGanadora() {
        assertThat(selector.elegir(List.of(ventana(MARTES, "10:00"), ventana(JUEVES, "16:00")), List.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("Sin ventanas tampoco revienta")
    void sinVentanasNoHayGanadora() {
        assertThat(selector.elegir(List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("A igualdad de votos gana la fecha más temprana")
    void elEmpateLoDecideLaFechaMasTemprana() {
        VentanaPlan martes = ventana(MARTES, "16:00");
        VentanaPlan jueves = ventana(JUEVES, "10:00");

        Optional<VentanaPlan> ganadora = selector.elegir(
                List.of(jueves, martes), // deliberadamente desordenadas
                votos(martes, 2, jueves, 2));

        assertThat(ganadora).contains(martes);
    }

    @Test
    @DisplayName("Empate el mismo día: gana la hora más temprana")
    void elEmpateElMismoDiaLoDecideLaHora() {
        VentanaPlan manana = ventana(MARTES, "10:00");
        VentanaPlan tarde = ventana(MARTES, "17:00");

        Optional<VentanaPlan> ganadora = selector.elegir(
                List.of(tarde, manana),
                votos(manana, 1, tarde, 1));

        assertThat(ganadora).contains(manana);
    }

    @Test
    @DisplayName("Una ventana sin votos no gana aunque sea la más temprana")
    void laVentanaSinVotosNoGana() {
        VentanaPlan temprana = ventana(MARTES, "09:00");
        VentanaPlan tardia = ventana(JUEVES, "18:00");

        Optional<VentanaPlan> ganadora = selector.elegir(
                List.of(temprana, tardia),
                votos(temprana, 0, tardia, 1));

        assertThat(ganadora).contains(tardia);
    }

    @Test
    @DisplayName("El recuento por ventana cuadra con los votos emitidos")
    void elRecuentoCuadra() {
        VentanaPlan martes = ventana(MARTES, "10:00");
        VentanaPlan jueves = ventana(JUEVES, "16:00");

        var recuento = selector.recuentoPorVentana(votos(martes, 3, jueves, 1));

        assertThat(recuento.get(martes.getId())).isEqualTo(3);
        assertThat(recuento.get(jueves.getId())).isEqualTo(1);
    }

    /* ------------------------------------------------------------------ */

    private VentanaPlan ventana(LocalDate fecha, String hora) {
        LocalTime inicio = LocalTime.parse(hora);
        return VentanaPlan.builder()
                .id(UUID.randomUUID())
                .fecha(fecha)
                .horaInicio(inicio)
                .horaFin(inicio.plusHours(2))
                .disponibilidadPorcentaje(100)
                .build();
    }

    private List<VotoVentana> votos(VentanaPlan a, int cuantosA, VentanaPlan b, int cuantosB) {
        List<VotoVentana> todos = new ArrayList<>();
        for (int i = 0; i < cuantosA; i++) todos.add(voto(a));
        for (int i = 0; i < cuantosB; i++) todos.add(voto(b));
        return todos;
    }

    private VotoVentana voto(VentanaPlan ventana) {
        return VotoVentana.builder()
                .ventana(ventana)
                .usuario(Usuario.builder().id(UUID.randomUUID()).build())
                .build();
    }
}
