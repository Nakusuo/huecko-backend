package com.huecko.backend.observabilidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        String recibido = request.getHeader(CABECERA);
        String id = recibido != null && VALIDO.matcher(recibido).matches() ? recibido : nuevo();
        MDC.put(CLAVE_MDC, id);
        response.setHeader(CABECERA, id);
        try {
            cadena.doFilter(request, response);
        } finally {
            MDC.remove(CLAVE_MDC);
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
