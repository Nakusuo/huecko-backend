package com.huecko.backend.ia;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente del servicio de IA (repo huecko-ai-service).
 *
 * <p>Todo lo que pasa por aquí es opcional para el producto: quien lo use debe
 * tener un plan B sin IA. Por eso el tiempo máximo es corto y cualquier fallo
 * —red, 5xx, respuesta ilegible— sale como {@link IaNoDisponibleException}, una
 * sola excepción que capturar.
 */
@Component
public class ClienteIA {

    private final RestClient http;

    @Autowired
    public ClienteIA(RestClient.Builder builder,
                     @Value("${huecko.ia.url:http://localhost:8000}") String url,
                     @Value("${huecko.ia.token:}") String token,
                     @Value("${huecko.ia.tiempo-maximo-ms:3000}") long tiempoMaximoMs) {
        this(builder.requestFactory(conTiempoMaximo(tiempoMaximoMs)), url, token);
    }

    /** Sin tocar la fábrica de peticiones: así las pruebas pueden poner la suya. */
    ClienteIA(RestClient.Builder builder, String url, String token) {
        this.http = builder.baseUrl(url).defaultHeader("X-Huecko-Token", token).build();
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

    private <T> T post(String ruta, Object cuerpo, Class<T> tipo) {
        try {
            T respuesta = http.post().uri(ruta).body(cuerpo).retrieve().body(tipo);
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
