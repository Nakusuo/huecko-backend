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
 * <p>Leer una foto no cabe en esos 3 s, así que el escaneo del horario va por
 * un segundo cliente con su propia espera: ahí la persona está mirando una
 * barra de progreso, no esperando a que se cierre una votación.
 */
@Component
public class ClienteIA {

    private final RestClient http;
    private final RestClient httpEscaneo;

    @Autowired
    public ClienteIA(RestClient.Builder builder,
                     @Value("${huecko.ia.url:http://localhost:8000}") String url,
                     @Value("${huecko.ia.token:}") String token,
                     @Value("${huecko.ia.tiempo-maximo-ms:3000}") long tiempoMaximoMs,
                     @Value("${huecko.ia.tiempo-maximo-escaneo-ms:30000}") long tiempoMaximoEscaneoMs) {
        this(builder.clone().requestFactory(conTiempoMaximo(tiempoMaximoMs)),
                builder.clone().requestFactory(conTiempoMaximo(tiempoMaximoEscaneoMs)), url, token);
    }

    /** Sin tocar la fábrica de peticiones: así las pruebas pueden poner la suya. */
    ClienteIA(RestClient.Builder builder, String url, String token) {
        this(builder, builder, url, token);
    }

    private ClienteIA(RestClient.Builder normal, RestClient.Builder escaneo, String url, String token) {
        this.http = normal.baseUrl(url).defaultHeader("X-Huecko-Token", token).build();
        this.httpEscaneo = escaneo.baseUrl(url).defaultHeader("X-Huecko-Token", token).build();
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
        return post(httpEscaneo, "/v1/horario/escaneo", peticion, RespuestaEscaneo.class);
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
