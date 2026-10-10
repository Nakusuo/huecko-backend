package com.huecko.backend.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta intentos por clave (IP, correo, usuario…) en una ventana deslizante.
 *
 * Vive en memoria del proceso: con una sola instancia (así se despliega hoy,
 * ver WebSocketConfig) basta, y un reinicio solo regala unos intentos.
 * El número de claves está acotado para que un atacante que cambie de clave
 * en cada petición no pueda llenar la memoria.
 */
public class LimitadorDeIntentos {

    private final int maximo;
    private final Duration ventana;
    private final int maxClaves;
    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();

    public LimitadorDeIntentos(int maximo, Duration ventana, int maxClaves) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.maxClaves = maxClaves;
    }

    /** Registra un intento si cabe. Devuelve `false` (sin registrarlo) si la clave ya llegó al máximo. */
    public boolean permitir(String clave, Instant ahora) {
        Deque<Instant> marcas = marcasDe(clave, ahora);
        synchronized (marcas) {
            descartarCaducadas(marcas, ahora);
            if (marcas.size() >= maximo) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    /** Si la clave ya llegó al máximo, sin gastar un intento. */
    public boolean bloqueado(String clave, Instant ahora) {
        Deque<Instant> marcas = intentos.get(clave);
        if (marcas == null) {
            return false;
        }
        synchronized (marcas) {
            descartarCaducadas(marcas, ahora);
            return marcas.size() >= maximo;
        }
    }

    /** Registra un intento aunque supere el máximo (p. ej. un login fallido). */
    public void registrar(String clave, Instant ahora) {
        Deque<Instant> marcas = marcasDe(clave, ahora);
        synchronized (marcas) {
            descartarCaducadas(marcas, ahora);
            marcas.addLast(ahora);
        }
    }

    public void olvidar(String clave) {
        intentos.remove(clave);
    }

    int claves() {
        return intentos.size();
    }

    private Deque<Instant> marcasDe(String clave, Instant ahora) {
        if (intentos.size() >= maxClaves && !intentos.containsKey(clave)) {
            purgar(ahora);
        }
        return intentos.computeIfAbsent(clave, c -> new ArrayDeque<>());
    }

    /**
     * Quita las claves sin intentos vigentes. Si aun así no hay sitio (un
     * ataque con muchas claves a la vez), se vacía entero: preferible a
     * quedarse sin memoria, y como mucho regala unos intentos.
     */
    private void purgar(Instant ahora) {
        intentos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                descartarCaducadas(e.getValue(), ahora);
                return e.getValue().isEmpty();
            }
        });
        if (intentos.size() >= maxClaves) {
            intentos.clear();
        }
    }

    private void descartarCaducadas(Deque<Instant> marcas, Instant ahora) {
        Instant limite = ahora.minus(ventana);
        while (!marcas.isEmpty() && !marcas.peekFirst().isAfter(limite)) {
            marcas.pollFirst();
        }
    }
}
