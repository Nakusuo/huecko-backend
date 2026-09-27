package com.huecko.backend.observabilidad.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BufferLogsTest {

    private final LoggerContext contexto = new LoggerContext();

    private LoggingEvent evento(String logger, Level nivel, String mensaje) {
        LoggingEvent e = new LoggingEvent();
        e.setLoggerName(logger);
        e.setLevel(nivel);
        e.setMessage(mensaje);
        e.setThreadName("main");
        e.setTimeStamp(System.currentTimeMillis());
        e.setLoggerContext(contexto);
        return e;
    }

    private BufferLogs buffer() {
        BufferLogs b = new BufferLogs();
        b.setContext(contexto);
        b.start();
        return b;
    }

    @Test
    @DisplayName("guarda INFO de nuestro código y solo WARN o más de las librerías")
    void filtra() {
        assertThat(BufferLogs.interesa(evento("com.huecko.backend.X", Level.INFO, "m"))).isTrue();
        assertThat(BufferLogs.interesa(evento("com.huecko.backend.X", Level.DEBUG, "m"))).isFalse();
        assertThat(BufferLogs.interesa(evento("org.hibernate.SQL", Level.INFO, "m"))).isFalse();
        assertThat(BufferLogs.interesa(evento("org.hibernate.SQL", Level.WARN, "m"))).isTrue();
    }

    @Test
    @DisplayName("se pide lo posterior a un id y se filtra por nivel")
    void pagina() {
        BufferLogs b = buffer();
        b.doAppend(evento("com.huecko.A", Level.INFO, "uno"));
        b.doAppend(evento("com.huecko.A", Level.ERROR, "dos"));
        b.doAppend(evento("com.huecko.A", Level.INFO, "tres"));

        BufferLogs.Pagina todo = b.desde(0, Level.INFO);
        assertThat(todo.eventos()).extracting(BufferLogs.Evento::mensaje).containsExactly("uno", "dos", "tres");
        assertThat(todo.ultimoId()).isEqualTo(3);
        assertThat(todo.eventos().get(0).logger()).isEqualTo("A");

        assertThat(b.desde(1, Level.INFO).eventos()).extracting(BufferLogs.Evento::mensaje).containsExactly("dos", "tres");
        assertThat(b.desde(0, Level.ERROR).eventos()).extracting(BufferLogs.Evento::mensaje).containsExactly("dos");
    }

    @Test
    @DisplayName("al llenarse descarta lo más antiguo y los ids siguen creciendo")
    void capacidad() {
        BufferLogs b = buffer();
        for (int i = 0; i < BufferLogs.CAPACIDAD + 5; i++) {
            b.doAppend(evento("com.huecko.A", Level.INFO, "m" + i));
        }
        BufferLogs.Pagina p = b.desde(BufferLogs.CAPACIDAD, Level.INFO);
        assertThat(p.ultimoId()).isEqualTo(BufferLogs.CAPACIDAD + 5);
        assertThat(p.eventos()).hasSize(5);
        assertThat(b.desde(0, Level.INFO).eventos()).hasSize(BufferLogs.MAX_POR_PAGINA);
    }
}
