package com.huecko.backend.plan.service;

import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.entity.VotoVentana;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RF-10: decide qué ventana gana al cerrarse la votación.
 *
 * Está aparte del servicio, y sin acceso a la base, porque es la regla que más
 * fácil se rompe sin que nadie lo note: un desempate mal resuelto no lanza
 * ninguna excepción, simplemente confirma el día equivocado.
 */
@Component
public class SelectorVentanaGanadora {

    /**
     * Devuelve la ventana ganadora, o vacío si nadie votó.
     *
     * Que nadie vote NO se resuelve confirmando una opción al azar: dejaría al
     * grupo con un evento "oficial" que ninguno aceptó. El plan se cancela, y
     * quien quiera puede volver a proponerlo.
     *
     * A igualdad de votos gana la ventana más temprana. Cualquier criterio
     * sirve mientras sea determinista, y la más próxima es la que menos
     * sorprende: es la primera que el grupo vio en la lista.
     *
     * <b>Ojo: el Módulo 5 desempata con otro criterio.</b> Allí
     * ({@code ImprevistoService.masVotada}) gana la opción más conservadora
     * —MANTENER sobre REAGENDAR sobre CANCELAR— porque lo que se empata son
     * consecuencias de distinto peso y deshacer una cancelación cuesta más que
     * reagendar después. Aquí las opciones son horas equivalentes entre sí, así
     * que el criterio es la cercanía. No son incoherentes: desempatan cosas
     * distintas.
     */
    public Optional<VentanaPlan> elegir(List<VentanaPlan> ventanas, List<VotoVentana> votos) {
        if (ventanas.isEmpty() || votos.isEmpty()) {
            return Optional.empty();
        }

        Map<UUID, Integer> recuento = new HashMap<>();
        for (VotoVentana voto : votos) {
            recuento.merge(voto.getVentana().getId(), 1, Integer::sum);
        }

        return ventanas.stream()
                .filter(v -> recuento.getOrDefault(v.getId(), 0) > 0)
                .max(Comparator
                        .<VentanaPlan>comparingInt(v -> recuento.getOrDefault(v.getId(), 0))
                        // `max` con orden invertido en el desempate: de dos
                        // ventanas con los mismos votos se queda la "mayor"
                        // según este comparador, que aquí es la más temprana.
                        .thenComparing(VentanaPlan::getFecha, Comparator.reverseOrder())
                        .thenComparing(VentanaPlan::getHoraInicio, Comparator.reverseOrder()));
    }

    /** Cuántos votos tiene cada ventana, para pintarlo en la respuesta. */
    public Map<UUID, Integer> recuentoPorVentana(List<VotoVentana> votos) {
        Map<UUID, Integer> recuento = new HashMap<>();
        for (VotoVentana voto : votos) {
            recuento.merge(voto.getVentana().getId(), 1, Integer::sum);
        }
        return recuento;
    }
}
