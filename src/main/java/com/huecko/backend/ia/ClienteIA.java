package com.huecko.backend.ia;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Cliente del servicio de IA (repo huecko-ai-service).
 *
 * <p>Todo lo que pasa por aquí es opcional para el producto: quien lo use debe
 * tener un plan B sin IA. Por eso el tiempo máximo es corto y cualquier fallo
 * —red, 5xx, respuesta ilegible— sale como {@link IaNoDisponibleException}, una
 * sola excepción que capturar.
 *
 * <p>Leer una foto o redactar un plan no cabe en esos 3 s, así que lo que pide
 * la persona (el «asistente») va por un segundo cliente con su propia espera:
 * ahí alguien está mirando un «Pensando…», no esperando a que se cierre una
 * votación.
 */
@Component
public class ClienteIA {

    private final RestClient http;
    private final RestClient httpAsistente;

    @Autowired
    public ClienteIA(RestClient.Builder builder,
                     @Value("${huecko.ia.url:http://localhost:8000}") String url,
                     @Value("${huecko.ia.token:}") String token,
                     @Value("${huecko.ia.tiempo-maximo-ms:3000}") long tiempoMaximoMs,
                     @Value("${huecko.ia.tiempo-maximo-asistente-ms:30000}") long tiempoMaximoAsistenteMs) {
        this(builder.clone().requestFactory(conTiempoMaximo(tiempoMaximoMs)),
                builder.clone().requestFactory(conTiempoMaximo(tiempoMaximoAsistenteMs)), url, token);
    }

    /** Sin tocar la fábrica de peticiones: así las pruebas pueden poner la suya. */
    ClienteIA(RestClient.Builder builder, String url, String token) {
        this(builder, builder, url, token);
    }

    private ClienteIA(RestClient.Builder normal, RestClient.Builder asistente, String url, String token) {
        this.http = normal.baseUrl(url).defaultHeader("X-Huecko-Token", token).build();
        this.httpAsistente = asistente.baseUrl(url).defaultHeader("X-Huecko-Token", token).build();
    }

    private static SimpleClientHttpRequestFactory conTiempoMaximo(long ms) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(Math.min(ms, 1000)));
        fabrica.setReadTimeout(Duration.ofMillis(ms));
        return fabrica;
    }

    public record PeticionCriticidad(String titulo, String lugar, String rol, String motivo) {}

    public record RespuestaCriticidad(String criticidad, String razon) {}

    /** RF-16: el servicio lee el motivo y dice si la ausencia es crítica. */
    public RespuestaCriticidad criticidad(PeticionCriticidad peticion) {
        return post("/v1/criticidad", peticion, RespuestaCriticidad.class);
    }

    public record PeticionRecomendacion(String titulo, String lugar, String inicio, Double horasHastaElPlan,
                                        String motivo, String razonCriticidad, int miembrosQueVotan,
                                        String resultadoPorDefecto) {}

    public record RespuestaRecomendacion(String opcion, String razon) {}

    /** Votación exprés: qué opción conviene al grupo y por qué. */
    public RespuestaRecomendacion recomendacion(PeticionRecomendacion peticion) {
        return post("/v1/votacion-expres/recomendacion", peticion, RespuestaRecomendacion.class);
    }

    record PeticionEscaneo(String tipo, String imagen) {}

    public record BloqueLeido(Integer diaSemana, String horaInicio, String horaFin, String titulo, Boolean dudoso) {}

    public record RespuestaEscaneo(List<BloqueLeido> bloques) {}

    /**
     * Lee los bloques de la foto de un horario. `tipo` debe ser el tipo real de
     * los bytes (ya comprobado), no el que dijo el navegador.
     */
    public RespuestaEscaneo escanearHorario(String tipo, byte[] imagen) {
        PeticionEscaneo peticion = new PeticionEscaneo(tipo, Base64.getEncoder().encodeToString(imagen));
        return post(httpAsistente, "/v1/horario/escaneo", peticion, RespuestaEscaneo.class);
    }

    /** Un hueco del grupo, numerado para que el modelo lo elija por número y nunca invente una hora. */
    public record HuecoIA(int numero, String fecha, String dia, String horaInicio, String horaFin,
                          int porcentaje, int libres, int total) {}

    public record PeticionBorrador(String texto, String grupo, int integrantes, String ahora, List<HuecoIA> huecos) {}

    public record OpcionIA(Integer numero, String horaInicio, String horaFin) {}

    public record RespuestaBorrador(String titulo, String categoria, String lugar, List<OpcionIA> opciones,
                                    Integer horasPlazo) {}

    /** Borrador de plan a partir de lo que escribió la persona. Lo que devuelve aún hay que validarlo. */
    public RespuestaBorrador borradorPlan(PeticionBorrador peticion) {
        return post(httpAsistente, "/v1/planes/borrador", peticion, RespuestaBorrador.class);
    }

    public record PeticionIdeas(String grupo, String descripcion, int integrantes, String ahora,
                                List<String> planesRecientes, List<HuecoIA> huecos) {}

    public record IdeaIA(String titulo, String categoria, String lugar, Integer numero, String razon) {}

    public record RespuestaIdeas(List<IdeaIA> ideas) {}

    /** Tres ideas de plan para los huecos del grupo. El número de hueco aún hay que validarlo. */
    public RespuestaIdeas ideasPlan(PeticionIdeas peticion) {
        return post(httpAsistente, "/v1/planes/ideas", peticion, RespuestaIdeas.class);
    }

    public record VentanaVotadaIA(String opcion, int votos, int disponibilidad, boolean elegida) {}

    public record AusenciaIA(String quien, boolean critica, String motivo) {}

    public record RetrasoIA(String quien, int minutos) {}

    public record ExpresIA(String quien, int votos, int de) {}

    /** @param estado EN_VOTACION, CONFIRMADO o POR_REPROGRAMAR; @param opciones las recomendaciones que admite */
    public record PeticionResumen(String titulo, String lugar, String estado, boolean votacionAbierta, String cierre,
                                  String ahora, int integrantes, int votantes, List<VentanaVotadaIA> ventanas,
                                  List<AusenciaIA> ausencias, List<RetrasoIA> retrasos, ExpresIA votacionExpres,
                                  List<String> opciones) {}

    public record RespuestaResumen(String resumen, String recomendacion, String razon) {}

    /** Cómo va la votación de un plan y qué conviene hacer. Que la recomendación sea admisible lo comprueba quien llama. */
    public RespuestaResumen resumenVotacion(PeticionResumen peticion) {
        return post(httpAsistente, "/v1/planes/resumen", peticion, RespuestaResumen.class);
    }

    private <T> T post(String ruta, Object cuerpo, Class<T> tipo) {
        return post(http, ruta, cuerpo, tipo);
    }

    private <T> T post(RestClient cliente, String ruta, Object cuerpo, Class<T> tipo) {
        try {
            T respuesta = cliente.post().uri(ruta).body(cuerpo).retrieve().body(tipo);
            if (respuesta == null) {
                throw new IaNoDisponibleException(ruta + " respondió vacío", null);
            }
            return respuesta;
        } catch (IaNoDisponibleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IaNoDisponibleException(ruta + ": " + ex.getClass().getSimpleName(), ex);
        }
    }
}
