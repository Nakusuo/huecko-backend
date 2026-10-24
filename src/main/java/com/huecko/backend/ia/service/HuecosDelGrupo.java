package com.huecko.backend.ia.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.dto.VentanaSugeridaResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.ia.ClienteIA;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Los huecos reales de un grupo en los próximos días, con fecha concreta y
 * numerados: es la lista entre la que la IA elige.
 *
 * La app móvil le pasa una semana tipo; aquí los planes se votan con fecha, así
 * que cada hueco ya es un día del calendario. Solo entran los que empiezan con
 * margen ({@link #ANTELACION}): así siempre cabe un plazo de votación que
 * cierre antes de la primera opción, como exige PlanService.
 */
@Component
public class HuecosDelGrupo {

    /** Hasta dónde se mira: dos semanas bastan para «el viernes» o «la próxima semana». */
    static final int DIAS = 14;
    /** La IA elige mejor entre pocos; más allá de 30 casi siempre son los mismos días repetidos. */
    static final int MAX_HUECOS = 30;
    static final Duration ANTELACION = Duration.ofHours(2);

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter AHORA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", ES);

    /**
     * @param numero desde 1, en orden de fecha y hora
     * @param porcentaje del grupo libre en el peor momento del tramo original
     */
    public record Hueco(int numero, LocalDate fecha, LocalTime inicio, LocalTime fin,
                        int porcentaje, int libres, int total) {
    }

    private final GrupoService grupoService;

    public HuecosDelGrupo(GrupoService grupoService) {
        this.grupoService = grupoService;
    }

    /** Lanza lo mismo que {@link GrupoService#disponibilidad} si la persona no es del grupo. */
    public List<Hueco> calcular(UUID usuarioId, UUID grupoId, Instant ahora) {
        LocalDateTime local = LocalDateTime.ofInstant(ahora, ZonaHoraria.ZONA);
        LocalDate hoy = local.toLocalDate();
        LocalDate ultimo = hoy.plusDays(DIAS - 1L);
        LocalDateTime desde = enPunto(local.plus(ANTELACION));

        List<Hueco> sinNumero = new ArrayList<>();
        // Una consulta por semana: los bloques puntuales solo cuentan en la suya.
        for (LocalDate lunes = hoy.with(DayOfWeek.MONDAY); !lunes.isAfter(ultimo); lunes = lunes.plusWeeks(1)) {
            DisponibilidadResponse cruce = grupoService.disponibilidad(usuarioId, grupoId, null, lunes);
            for (VentanaSugeridaResponse v : cruce.ventanasSugeridas()) {
                LocalDate fecha = lunes.plusDays(v.diaSemana() - 1L);
                if (fecha.isBefore(desde.toLocalDate()) || fecha.isAfter(ultimo)) {
                    continue;
                }
                LocalTime inicio = fecha.equals(desde.toLocalDate()) && v.horaInicio().isBefore(desde.toLocalTime())
                        ? desde.toLocalTime()
                        : v.horaInicio();
                if (!v.horaFin().isAfter(inicio)) {
                    continue;
                }
                sinNumero.add(new Hueco(0, fecha, inicio, v.horaFin(),
                        v.disponibilidadPorcentaje(), v.miembrosDisponibles(), v.totalMiembros()));
            }
        }

        sinNumero.sort(Comparator.comparing(Hueco::fecha).thenComparing(Hueco::inicio));
        List<Hueco> huecos = new ArrayList<>();
        for (Hueco h : sinNumero.subList(0, Math.min(MAX_HUECOS, sinNumero.size()))) {
            huecos.add(new Hueco(huecos.size() + 1, h.fecha(), h.inicio(), h.fin(), h.porcentaje(), h.libres(), h.total()));
        }
        return huecos;
    }

    /** «jueves 22 de octubre de 2026, 10:00», en la zona de los grupos: así la IA entiende «mañana». */
    public static String ahoraLegible(Instant ahora) {
        return LocalDateTime.ofInstant(ahora, ZonaHoraria.ZONA).format(AHORA);
    }

    /** Lo que ve la IA de cada hueco: su número, la fecha, el día en palabras y las horas. */
    public static ClienteIA.HuecoIA paraIA(Hueco h) {
        return new ClienteIA.HuecoIA(h.numero(), h.fecha().toString(),
                h.fecha().getDayOfWeek().getDisplayName(TextStyle.FULL, ES),
                h.inicio().toString(), h.fin().toString(), h.porcentaje(), h.libres(), h.total());
    }

    /** La siguiente hora en punto (o la misma si ya lo es): las celdas del cruce son horas enteras. */
    private static LocalDateTime enPunto(LocalDateTime momento) {
        LocalDateTime truncado = momento.truncatedTo(ChronoUnit.HOURS);
        return truncado.equals(momento) ? truncado : truncado.plusHours(1);
    }
}
