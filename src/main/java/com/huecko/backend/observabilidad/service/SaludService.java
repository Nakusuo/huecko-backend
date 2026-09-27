package com.huecko.backend.observabilidad.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.mongo.repository.VotacionExpresRepository;
import com.huecko.backend.observabilidad.dto.SaludResponse;
import com.huecko.backend.observabilidad.dto.SaludResponse.Componente;
import com.huecko.backend.observabilidad.dto.SaludResponse.Estado;
import com.huecko.backend.observabilidad.dto.SaludResponse.TareaEstado;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.repository.PlanRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Comprueba en el momento cada pieza de la que depende Huecko. Nada se cachea:
 * el panel pregunta y el backend va a mirar.
 */
@Service
public class SaludService {

    /** Por encima de esto una base responde, pero lenta. */
    static final long LATENCIA_LENTA_MS = 500;

    /** Margen mínimo antes de dar algo por atrasado: el barrido corre cada 30–60 s. */
    static final Duration MARGEN_MINIMO = Duration.ofMinutes(2);

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final SimpUserRegistry usuariosConectados;
    private final RegistroTareas registroTareas;
    private final PlanRepository planRepository;
    private final VotacionExpresRepository votacionRepository;
    private final Environment environment;
    private final String version;

    public SaludService(JdbcTemplate jdbcTemplate, MongoTemplate mongoTemplate, SimpUserRegistry usuariosConectados,
                        RegistroTareas registroTareas, PlanRepository planRepository,
                        VotacionExpresRepository votacionRepository, Environment environment,
                        @Value("${huecko.version:desconocida}") String version) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoTemplate = mongoTemplate;
        this.usuariosConectados = usuariosConectados;
        this.registroTareas = registroTareas;
        this.planRepository = planRepository;
        this.votacionRepository = votacionRepository;
        this.environment = environment;
        this.version = version;
    }

    public SaludResponse salud() {
        Instant ahora = Instant.now();

        List<Componente> componentes = List.of(
                medir("postgres", "PostgreSQL", "Cuentas, grupos y planes",
                        () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class)),
                medir("mongo", "MongoDB", "Horarios, retrasos e imprevistos",
                        () -> mongoTemplate.executeCommand(new Document("ping", 1))),
                tiempoReal());

        List<TareaEstado> tareas = new ArrayList<>();
        for (Tarea tarea : Tarea.values()) {
            tareas.add(tarea(tarea, ahora));
        }

        Estado general = peor(componentes.stream().map(Componente::estado).toList(),
                tareas.stream().map(TareaEstado::estado).toList());

        return new SaludResponse(general, ahora, componentes, tareas, aplicacion(ahora));
    }

    /** Ejecuta la comprobación midiendo cuánto tarda. Si lanza, el componente está caído. */
    private Componente medir(String clave, String nombre, String uso, Supplier<Object> comprobacion) {
        long inicio = System.nanoTime();
        try {
            comprobacion.get();
            long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            boolean lenta = ms > LATENCIA_LENTA_MS;
            return new Componente(clave, nombre, lenta ? Estado.DEGRADADO : Estado.OK, ms,
                    lenta ? uso + ". Responde, pero lenta." : uso + ".");
        } catch (RuntimeException ex) {
            // Solo el tipo: el mensaje de un driver puede traer la URL con credenciales.
            return new Componente(clave, nombre, Estado.CAIDO, null,
                    "No responde (" + ex.getClass().getSimpleName() + ").");
        }
    }

    /** El broker es parte del propio proceso: si el backend contesta, el canal está arriba. */
    private Componente tiempoReal() {
        int personas = usuariosConectados.getUserCount();
        int sesiones = usuariosConectados.getUsers().stream().mapToInt(u -> u.getSessions().size()).sum();
        return new Componente("tiempoReal", "Tiempo real (WebSocket)", Estado.OK, null,
                personas + (personas == 1 ? " persona conectada" : " personas conectadas")
                        + " en " + sesiones + (sesiones == 1 ? " sesión." : " sesiones."));
    }

    private TareaEstado tarea(Tarea tarea, Instant ahora) {
        boolean activa = environment.getProperty(tarea.propiedadActiva(), Boolean.class, true);
        long intervalo = environment.getProperty(tarea.propiedadIntervalo(), Long.class, tarea.intervaloPorDefectoMs());
        Duration margen = Duration.ofMillis(intervalo * 2).compareTo(MARGEN_MINIMO) > 0
                ? Duration.ofMillis(intervalo * 2) : MARGEN_MINIMO;
        long atrasadas = atrasadas(tarea, ahora.minus(margen));

        RegistroTareas.Ejecuciones ejecuciones = registroTareas.de(tarea);
        Estado estado = atrasadas > 0 || ejecuciones.ultimosFallidos() > 0 ? Estado.DEGRADADO : Estado.OK;

        return new TareaEstado(tarea.name(), tarea.nombre(), tarea.descripcion(), activa, intervalo, estado,
                atrasadas, ejecuciones.total(), ejecuciones.ultimaEjecucion(), ejecuciones.duracionMs(),
                ejecuciones.ultimosProcesados(), ejecuciones.fallidosTotales(), ejecuciones.ultimoError(),
                ejecuciones.ultimoErrorEn());
    }

    private long atrasadas(Tarea tarea, Instant limite) {
        try {
            return switch (tarea) {
                case CIERRE_PLANES ->
                        planRepository.findByEstadoAndPlazoVotacionLessThanEqual(Plan.Estado.PROPUESTO, limite).size();
                case CIERRE_VOTACIONES_EXPRES ->
                        votacionRepository.findByEstadoAndExpiraEnLessThanEqual(VotacionExpres.Estado.ABIERTA, limite)
                                .size();
            };
        } catch (RuntimeException ex) {
            // Si la base está caída ya lo dice su componente; aquí no se sabe.
            return 0;
        }
    }

    private SaludResponse.Aplicacion aplicacion(Instant ahora) {
        Instant arranque = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        Runtime runtime = Runtime.getRuntime();
        long mb = 1024 * 1024;
        return new SaludResponse.Aplicacion(
                version,
                Arrays.asList(environment.getActiveProfiles()),
                arranque,
                Duration.between(arranque, ahora).toSeconds(),
                System.getProperty("java.version"),
                (runtime.totalMemory() - runtime.freeMemory()) / mb,
                runtime.maxMemory() / mb,
                ZonaHoraria.ZONA.getId());
    }

    @SafeVarargs
    static Estado peor(List<Estado>... grupos) {
        return Arrays.stream(grupos).flatMap(List::stream)
                .max(Comparator.naturalOrder())
                .orElse(Estado.OK);
    }
}
