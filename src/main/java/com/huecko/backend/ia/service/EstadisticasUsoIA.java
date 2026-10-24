package com.huecko.backend.ia.service;

import com.huecko.backend.ia.dto.UsoIAResponse;
import com.huecko.backend.mongo.document.LlamadaIA;
import com.huecko.backend.mongo.repository.LlamadaIARepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Uso de Huecko IA en el último mes, por función. */
@Service
public class EstadisticasUsoIA {

    private final LlamadaIARepository llamadas;
    private final Clock reloj;

    @Autowired
    public EstadisticasUsoIA(LlamadaIARepository llamadas) {
        this(llamadas, Clock.systemUTC());
    }

    EstadisticasUsoIA(LlamadaIARepository llamadas, Clock reloj) {
        this.llamadas = llamadas;
        this.reloj = reloj;
    }

    public UsoIAResponse calcular() {
        Instant desde = reloj.instant().minus(RegistroUsoIA.RETENCION);
        // Acotado por el cupo (20 por persona cada 15 min) y por el TTL de un mes.
        Map<LlamadaIA.Funcion, List<LlamadaIA>> porFuncion = llamadas.findByOcurridaEnAfter(desde).stream()
                .filter(l -> l.getFuncion() != null)
                .collect(Collectors.groupingBy(LlamadaIA::getFuncion));

        List<UsoIAResponse.PorFuncion> funciones = Arrays.stream(LlamadaIA.Funcion.values())
                .map(f -> resumir(f, porFuncion.getOrDefault(f, List.of())))
                .toList();
        long total = funciones.stream().mapToLong(UsoIAResponse.PorFuncion::llamadas).sum();
        return new UsoIAResponse((int) RegistroUsoIA.RETENCION.toDays(), total, funciones);
    }

    private static UsoIAResponse.PorFuncion resumir(LlamadaIA.Funcion funcion, List<LlamadaIA> de) {
        if (de.isEmpty()) {
            return new UsoIAResponse.PorFuncion(funcion.name(), 0, 0, null, null);
        }
        long exitosas = de.stream().filter(LlamadaIA::isOk).count();
        long msPromedio = Math.round(de.stream().mapToLong(LlamadaIA::getDuracionMs).average().orElse(0));
        return new UsoIAResponse.PorFuncion(funcion.name(), de.size(), exitosas,
                (double) exitosas / de.size(), msPromedio);
    }
}
