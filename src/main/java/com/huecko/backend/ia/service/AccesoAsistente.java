package com.huecko.backend.ia.service;

import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * La puerta de las funciones de IA que usa la persona directamente (escaneo
 * del horario, y las que vengan): si están encendidas y si le queda cupo.
 *
 * Cada llamada gasta cuota gratuita de Gemini, compartida por todo Huecko.
 * El cupo es por persona y no por IP: en una universidad muchos salen por la
 * misma IP. Se gasta antes de llamar y no se devuelve si la IA falla; así un
 * reintento en bucle tampoco agota la cuota de los demás.
 */
@Component
public class AccesoAsistente {

    public static final String NO_DISPONIBLE =
            "La IA no está disponible en este momento. Inténtalo en unos minutos.";

    static final int MAX_USOS = 20;
    static final Duration VENTANA = Duration.ofMinutes(15);

    private final boolean activo;
    private final Clock reloj;
    private final LimitadorDeIntentos usos = new LimitadorDeIntentos(MAX_USOS, VENTANA, 10_000);

    @Autowired
    public AccesoAsistente(@Value("${huecko.ia.asistente:true}") boolean activo) {
        this(activo, Clock.systemUTC());
    }

    AccesoAsistente(boolean activo, Clock reloj) {
        this.activo = activo;
        this.reloj = reloj;
    }

    public boolean activo() {
        return activo;
    }

    /** Antes de cada llamada a la IA. Lanza 503 si está apagada y 429 si se acabó el cupo. */
    public void exigir(UUID usuarioId) {
        if (!activo) {
            throw new ServicioNoDisponibleException(NO_DISPONIBLE);
        }
        if (!usos.permitir(usuarioId.toString(), reloj.instant())) {
            throw new DemasiadosIntentosException("Usaste mucho la IA en poco tiempo. Espera unos minutos.");
        }
    }
}
