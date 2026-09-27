package com.huecko.backend.observabilidad.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Las últimas líneas del log, en memoria, para la consola del panel.
 *
 * Se engancha a Logback como un destino más: el log de siempre (consola,
 * archivo) no cambia. Guarda INFO o más de nuestro código y WARN o más de las
 * librerías; el DEBUG y el INFO de Spring o Hibernate llenarían el búfer de
 * ruido en segundos.
 */
@Component
public class BufferLogs extends AppenderBase<ILoggingEvent> {

    static final int CAPACIDAD = 1000;
    static final int MAX_POR_PAGINA = 300;
    static final int MAX_MENSAJE = 2000;
    private static final String PAQUETE_PROPIO = "com.huecko";

    /** `id` crece siempre: el panel pide «lo posterior a X» y nunca recibe una línea dos veces. */
    public record Evento(long id, Instant momento, String nivel, String logger, String hilo, String mensaje,
                         String excepcion) {
    }

    /** `ultimoId` es desde dónde pedir la próxima vez, haya llegado algo o no. */
    public record Pagina(List<Evento> eventos, long ultimoId, int capacidad) {
    }

    private final Deque<Evento> eventos = new ArrayDeque<>(CAPACIDAD);
    private long secuencia;

    @PostConstruct
    void enganchar() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(contexto);
        setName("PANEL_ADMIN");
        start();
        contexto.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @PreDestroy
    void desenganchar() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        contexto.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(this);
        stop();
    }

    @Override
    protected void append(ILoggingEvent evento) {
        if (!interesa(evento)) {
            return;
        }
        String nombre = evento.getLoggerName();
        Evento linea = new Evento(0, Instant.ofEpochMilli(evento.getTimeStamp()), evento.getLevel().toString(),
                nombre.substring(nombre.lastIndexOf('.') + 1), evento.getThreadName(),
                RegistroFallos.recortar(evento.getFormattedMessage(), MAX_MENSAJE),
                excepcion(evento.getThrowableProxy()));
        synchronized (eventos) {
            if (eventos.size() == CAPACIDAD) {
                eventos.pollFirst();
            }
            secuencia++;
            eventos.addLast(new Evento(secuencia, linea.momento(), linea.nivel(), linea.logger(), linea.hilo(),
                    linea.mensaje(), linea.excepcion()));
        }
    }

    static boolean interesa(ILoggingEvent evento) {
        Level nivel = evento.getLevel();
        return nivel.isGreaterOrEqual(Level.WARN)
                || (nivel.isGreaterOrEqual(Level.INFO) && evento.getLoggerName().startsWith(PAQUETE_PROPIO));
    }

    /** Las líneas posteriores a `despuesDe` con al menos `nivelMinimo`, las más antiguas primero. */
    public Pagina desde(long despuesDe, Level nivelMinimo) {
        synchronized (eventos) {
            List<Evento> nuevos = eventos.stream()
                    .filter(e -> e.id() > despuesDe)
                    .filter(e -> Level.toLevel(e.nivel()).isGreaterOrEqual(nivelMinimo))
                    .toList();
            // Si hay más de una página, se dan las últimas: en una consola en vivo importa lo reciente.
            List<Evento> pagina = nuevos.size() > MAX_POR_PAGINA
                    ? nuevos.subList(nuevos.size() - MAX_POR_PAGINA, nuevos.size())
                    : nuevos;
            return new Pagina(pagina, secuencia, CAPACIDAD);
        }
    }

    private static String excepcion(IThrowableProxy proxy) {
        if (proxy == null) {
            return null;
        }
        String mensaje = proxy.getMessage();
        return RegistroFallos.recortar(proxy.getClassName() + (mensaje == null ? "" : ": " + mensaje), 500);
    }
}
