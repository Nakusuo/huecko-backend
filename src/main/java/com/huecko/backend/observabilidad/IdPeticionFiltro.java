package com.huecko.backend.observabilidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Da a cada petición un identificador corto que aparece en todas sus líneas
 * del log (`[id]`), en la cabecera `X-Request-Id` de la respuesta y en el JSON
 * de los errores. Con él, un «me salió un error» del usuario lleva a las
 * líneas exactas del servidor.
 *
 * Si la petición ya trae uno válido (un proxy o el propio frontend), se
 * respeta para seguirla de punta a punta. Va antes que la seguridad para que
 * también los 401 y 403 lo lleven.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdPeticionFiltro extends OncePerRequestFilter {

    public static final String CABECERA = "X-Request-Id";
    /** Clave en el MDC de SLF4J; el patrón del log la imprime con `%X{idPeticion}`. */
    public static final String CLAVE_MDC = "idPeticion";

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final String ALFABETO = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int LARGO = 12;
    private static final SecureRandom AZAR = new SecureRandom();
    /** A partir de aquí una petición se marca como lenta en el log. */
    static final long LENTA_MS = 2000;
    private static final Logger log = LoggerFactory.getLogger(IdPeticionFiltro.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        String recibido = request.getHeader(CABECERA);
        String id = recibido != null && VALIDO.matcher(recibido).matches() ? recibido : nuevo();
        MDC.put(CLAVE_MDC, id);
        response.setHeader(CABECERA, id);
        long inicio = System.nanoTime();
        try {
            cadena.doFilter(request, response);
        } finally {
            registrar(request, response.getStatus(), (System.nanoTime() - inicio) / 1_000_000);
            MDC.remove(CLAVE_MDC);
        }
    }

    /**
     * Una línea por petición: `GET /api/grupos → 200 (12 ms)`. En DEBUG lo
     * normal; en WARN los errores del servidor y las lentas, que así llegan a
     * la consola del panel con su id.
     */
    private static void registrar(HttpServletRequest request, int estado, long ms) {
        String ruta = request.getRequestURI();
        if (ruta.startsWith("/api/actuator")) {
            return;
        }
        if (estado >= 500 || ms >= LENTA_MS) {
            log.warn("{} {} → {} ({} ms){}", request.getMethod(), ruta, estado, ms, ms >= LENTA_MS ? " lenta" : "");
        } else if (log.isDebugEnabled()) {
            log.debug("{} {} → {} ({} ms)", request.getMethod(), ruta, estado, ms);
        }
    }

    /** El de la petición en curso, o nulo fuera de una petición (tareas programadas). */
    public static String actual() {
        return MDC.get(CLAVE_MDC);
    }

    static String nuevo() {
        StringBuilder id = new StringBuilder(LARGO);
        for (int i = 0; i < LARGO; i++) {
            id.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return id.toString();
    }
}
