package com.huecko.backend.ia.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.ia.dto.BorradorPlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Borrador de plan a partir de una frase («repaso de 2 horas el viernes en la
 * tarde»), como en la app móvil.
 *
 * La IA solo propone; las reglas son de aquí. Elige huecos por número y horas
 * dentro de ellos, y este servicio comprueba las dos cosas, de modo que cada
 * opción del borrador cae en un tramo en el que el grupo está libre y pasa las
 * validaciones de PlanService (umbral, sin solapes, plazo antes de la primera
 * opción). No se guarda nada: el borrador solo rellena el formulario.
 */
@Service
public class BorradorPlanService {

    private static final Logger log = LoggerFactory.getLogger(BorradorPlanService.class);

    static final int MAX_OPCIONES = 3;
    static final int HORAS_PLAZO_POR_DEFECTO = 48;
    /** Si la IA no dio horas válidas, el plan dura esto (o el hueco entero si es más corto). */
    static final Duration DURACION_POR_DEFECTO = Duration.ofHours(2);
    /** La votación cierra al menos esto antes de la primera opción, para que dé tiempo a organizarse. */
    static final Duration MARGEN_ANTES_DEL_PLAN = Duration.ofHours(1);

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter AHORA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", ES);
    private static final Set<String> CATEGORIAS = Set.of("ESTUDIO", "REUNION", "COMIDA", "DEPORTE", "SALIDA", "OTRO");

    private final GrupoService grupoService;
    private final HuecosDelGrupo huecosDelGrupo;
    private final ClienteIA clienteIA;
    private final AccesoAsistente acceso;
    private final Clock reloj;

    @Autowired
    public BorradorPlanService(GrupoService grupoService, HuecosDelGrupo huecosDelGrupo,
                               ClienteIA clienteIA, AccesoAsistente acceso) {
        this(grupoService, huecosDelGrupo, clienteIA, acceso, Clock.systemUTC());
    }

    BorradorPlanService(GrupoService grupoService, HuecosDelGrupo huecosDelGrupo,
                        ClienteIA clienteIA, AccesoAsistente acceso, Clock reloj) {
        this.grupoService = grupoService;
        this.huecosDelGrupo = huecosDelGrupo;
        this.clienteIA = clienteIA;
        this.acceso = acceso;
        this.reloj = reloj;
    }

    public BorradorPlanResponse redactar(UUID usuarioId, UUID grupoId, String texto) {
        Instant ahora = reloj.instant();
        // Primero lo que no gasta cupo: si no es del grupo, 404 sin llamar a nadie.
        GrupoResponse grupo = grupoService.detalle(usuarioId, grupoId);
        List<HuecosDelGrupo.Hueco> huecos = huecosDelGrupo.calcular(usuarioId, grupoId, ahora);

        acceso.exigir(usuarioId);
        ClienteIA.RespuestaBorrador respuesta;
        try {
            respuesta = clienteIA.borradorPlan(new ClienteIA.PeticionBorrador(
                    texto.trim(), grupo.nombre(), grupo.miembros().size(),
                    LocalDateTime.ofInstant(ahora, ZonaHoraria.ZONA).format(AHORA),
                    huecos.stream().map(BorradorPlanService::aHuecoIA).toList()));
        } catch (IaNoDisponibleException ex) {
            log.warn("Borrador de plan sin IA en el grupo {}: {}", grupoId, ex.getMessage());
            throw new ServicioNoDisponibleException(AccesoAsistente.NO_DISPONIBLE);
        }
        // El servicio ya exige título; si aun así faltara, no hay borrador que enseñar.
        if (respuesta.titulo() == null || respuesta.titulo().isBlank()) {
            throw new ServicioNoDisponibleException(AccesoAsistente.NO_DISPONIBLE);
        }

        List<BorradorPlanResponse.Ventana> ventanas = ventanas(huecos, respuesta.opciones());
        return new BorradorPlanResponse(
                recortar(respuesta.titulo(), 120),
                CATEGORIAS.contains(respuesta.categoria()) ? respuesta.categoria() : "OTRO",
                recortar(respuesta.lugar(), 200),
                ventanas,
                plazo(ahora, respuesta.horasPlazo(), ventanas));
    }

    /**
     * Cada opción, en su hueco y sin pisarse con las anteriores. Un número que
     * no está en la lista se descarta: la IA no puede inventar un momento.
     */
    static List<BorradorPlanResponse.Ventana> ventanas(List<HuecosDelGrupo.Hueco> huecos,
                                                       List<ClienteIA.OpcionIA> opciones) {
        if (opciones == null) {
            return List.of();
        }
        Map<Integer, HuecosDelGrupo.Hueco> porNumero = huecos.stream()
                .collect(Collectors.toMap(HuecosDelGrupo.Hueco::numero, Function.identity()));
        List<BorradorPlanResponse.Ventana> elegidas = new ArrayList<>();
        for (ClienteIA.OpcionIA opcion : opciones.stream().filter(Objects::nonNull).toList()) {
            HuecosDelGrupo.Hueco hueco = opcion.numero() == null ? null : porNumero.get(opcion.numero());
            if (hueco == null) {
                continue;
            }
            BorradorPlanResponse.Ventana tramo = tramo(hueco, opcion.horaInicio(), opcion.horaFin());
            boolean pisa = elegidas.stream().anyMatch(e -> e.fecha().equals(tramo.fecha())
                    && tramo.horaInicio().isBefore(e.horaFin()) && e.horaInicio().isBefore(tramo.horaFin()));
            if (!pisa) {
                elegidas.add(tramo);
            }
            if (elegidas.size() == MAX_OPCIONES) {
                break;
            }
        }
        return elegidas;
    }

    /**
     * Las horas que pidió la IA si caen dentro del hueco; si no, las primeras
     * dos horas del hueco. Así una hora mal escrita no cuela un momento en el
     * que alguien está ocupado.
     */
    static BorradorPlanResponse.Ventana tramo(HuecosDelGrupo.Hueco hueco, String inicio, String fin) {
        LocalTime desde = hora(inicio);
        LocalTime hasta = hora(fin);
        boolean dentro = desde != null && hasta != null && desde.isBefore(hasta)
                && !desde.isBefore(hueco.inicio()) && !hasta.isAfter(hueco.fin());
        if (!dentro) {
            desde = hueco.inicio();
            LocalTime dosHoras = desde.plus(DURACION_POR_DEFECTO);
            // Sin pasar de medianoche: 23:00 + 2 h no puede dar 01:00.
            hasta = dosHoras.isAfter(desde) && dosHoras.isBefore(hueco.fin()) ? dosHoras : hueco.fin();
        }
        return new BorradorPlanResponse.Ventana(hueco.fecha(), desde, hasta, hueco.porcentaje());
    }

    /**
     * Las horas que sugirió la IA, en punto, y nunca más tarde que una hora
     * antes de la primera opción: PlanService rechaza un plazo que cierra
     * después de que empiece el plan.
     */
    static Instant plazo(Instant ahora, Integer horasSugeridas, List<BorradorPlanResponse.Ventana> ventanas) {
        int horas = horasSugeridas != null && horasSugeridas >= 1 && horasSugeridas <= 168
                ? horasSugeridas : HORAS_PLAZO_POR_DEFECTO;
        Instant plazo = enPunto(ahora.plus(Duration.ofHours(horas)));
        Instant limite = ventanas.stream()
                .map(v -> ZonaHoraria.instante(v.fecha(), v.horaInicio()).minus(MARGEN_ANTES_DEL_PLAN))
                .min(Instant::compareTo)
                .orElse(plazo);
        Instant minimo = ahora.plus(Duration.ofHours(1));
        if (plazo.isAfter(limite)) {
            plazo = limite;
        }
        return plazo.isBefore(minimo) ? minimo : plazo;
    }

    private static Instant enPunto(Instant momento) {
        Instant truncado = momento.truncatedTo(ChronoUnit.HOURS);
        return truncado.equals(momento) ? truncado : truncado.plus(Duration.ofHours(1));
    }

    private static LocalTime hora(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return LocalTime.parse(valor);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.strip();
        return limpio.length() > maximo ? limpio.substring(0, maximo) : limpio;
    }

    private static ClienteIA.HuecoIA aHuecoIA(HuecosDelGrupo.Hueco h) {
        return new ClienteIA.HuecoIA(h.numero(), h.fecha().toString(),
                h.fecha().getDayOfWeek().getDisplayName(TextStyle.FULL, ES),
                h.inicio().toString(), h.fin().toString(), h.porcentaje(), h.libres(), h.total());
    }
}
