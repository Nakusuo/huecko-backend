package com.huecko.backend.grupo.service;

import com.huecko.backend.grupo.dto.CeldaDisponibilidadResponse;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.dto.VentanaSugeridaResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RF-05, RF-06: cruza los bloques ocupados de todos los integrantes y devuelve
 * el heatmap semanal más las ventanas que cumplen el umbral.
 *
 * No toca la base de datos ni depende del contexto de Spring: recibe los datos
 * ya cargados y devuelve la respuesta. Eso permite probar el algoritmo entero
 * sin Mongo ni Postgres levantados, que es donde de verdad se puede equivocar.
 *
 * RF-07 sale gratis por construcción: como el cruce se calcula en cada petición
 * a partir de los bloques vigentes, no hay ningún resultado cacheado que pueda
 * quedar desactualizado cuando alguien edita su horario.
 *
 * RNF-04 (menos de 1 s con 20 personas): el coste es
 * O(bloques x horas_de_la_franja), sin consultas dentro del bucle. Con 20
 * personas y unos 15 bloques cada una son unos 300 bloques x 12 horas, algo
 * más de 3600 comprobaciones aritméticas.
 */
@Component
public class CalculadoraDisponibilidad {

    /**
     * Franja que se dibuja. Fuera de ella todo el mundo está técnicamente libre
     * y el heatmap se llenaría de verde inútil a las 4 de la mañana.
     */
    public static final int HORA_DESDE = 8;
    public static final int HORA_HASTA = 20; // exclusiva: la última casilla es 19:00-20:00

    private static final int DIAS = 7;
    /** Ninguna respuesta trae más ventanas que esto; el organizador elige entre 2 y 5 (RF-08). */
    private static final int MAX_VENTANAS = 20;

    public DisponibilidadResponse calcular(UUID grupoId,
                                           List<UUID> miembros,
                                           List<BloqueHorario> bloques,
                                           int umbral,
                                           LocalDate referencia) {

        LocalDate lunes = referencia.with(DayOfWeek.MONDAY);
        LocalDate domingo = lunes.plusDays(6);
        int total = miembros.size();

        if (total == 0) {
            return new DisponibilidadResponse(grupoId, umbral, 0, lunes, domingo,
                    HORA_DESDE, HORA_HASTA, List.of(), List.of());
        }

        // Solo cuentan los bloques de quien realmente pertenece al grupo: si a
        // alguien se le acaba de dar de baja, sus clases no deben seguir
        // bloqueando huecos.
        Set<String> idsMiembros = new HashSet<>();
        for (UUID id : miembros) {
            idsMiembros.add(id.toString());
        }

        // ocupados[dia][hora] = quiénes están ocupados ahí. Es un Set y no un
        // contador porque dos bloques solapados de la MISMA persona (una clase
        // y una cita encima) tienen que contar como una sola ausencia.
        List<List<Set<String>>> ocupados = rejillaVacia();

        for (BloqueHorario bloque : bloques) {
            if (bloque.getUsuarioId() == null || !idsMiembros.contains(bloque.getUsuarioId())) {
                continue;
            }
            for (int dia : diasQueOcupa(bloque, lunes, domingo)) {
                marcarHoras(ocupados.get(dia - 1), bloque);
            }
        }

        List<CeldaDisponibilidadResponse> celdas = new ArrayList<>();
        for (int dia = 1; dia <= DIAS; dia++) {
            for (int hora = HORA_DESDE; hora < HORA_HASTA; hora++) {
                int disponibles = total - ocupados.get(dia - 1).get(hora - HORA_DESDE).size();
                int porcentaje = Math.round(disponibles * 100f / total);
                celdas.add(new CeldaDisponibilidadResponse(
                        dia, hora, disponibles, total, porcentaje, porcentaje >= umbral));
            }
        }

        return new DisponibilidadResponse(grupoId, umbral, total, lunes, domingo,
                HORA_DESDE, HORA_HASTA, celdas, ventanas(celdas, total));
    }

    /**
     * Días de la semana mostrada que toca un bloque.
     *
     * Un recurrente toca siempre el mismo. Uno puntual toca los días reales
     * entre `fecha` y `fechaFin`, y solo si caen dentro de la semana pedida:
     * un viaje del mes que viene no debe teñir el heatmap de esta semana.
     */
    private List<Integer> diasQueOcupa(BloqueHorario bloque, LocalDate lunes, LocalDate domingo) {
        if (bloque.getTipo() == BloqueHorario.Tipo.RECURRENTE) {
            Integer dia = bloque.getDiaSemana();
            return (dia == null || dia < 1 || dia > DIAS) ? List.of() : List.of(dia);
        }

        LocalDate inicio = bloque.getFecha();
        if (inicio == null) {
            return List.of();
        }
        LocalDate fin = bloque.getFechaFin() == null ? inicio : bloque.getFechaFin();
        if (fin.isBefore(inicio) || inicio.isAfter(domingo) || fin.isBefore(lunes)) {
            return List.of();
        }

        LocalDate desde = inicio.isBefore(lunes) ? lunes : inicio;
        LocalDate hasta = fin.isAfter(domingo) ? domingo : fin;

        List<Integer> dias = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            dias.add(d.getDayOfWeek().getValue());
        }
        return dias;
    }

    /**
     * Marca como ocupadas las casillas que el bloque solapa, aunque sea en parte.
     *
     * El solape se mide en minutos y no en horas enteras a propósito: un bloque
     * de 08:00 a 10:30 deja a esa persona ocupada también en la franja de las
     * 10, porque no puede entrar a un plan que empieza a las 10:00.
     */
    private void marcarHoras(List<Set<String>> diaDeLaRejilla, BloqueHorario bloque) {
        int inicio = minutos(bloque.getHoraInicio(), 0);
        int fin = minutos(bloque.getHoraFin(), 24 * 60);
        // horaFin a medianoche, o anterior al inicio, se lee como "hasta el final del día".
        if (fin <= inicio) {
            fin = 24 * 60;
        }

        for (int hora = HORA_DESDE; hora < HORA_HASTA; hora++) {
            int desde = hora * 60;
            int hasta = desde + 60;
            if (inicio < hasta && fin > desde) {
                diaDeLaRejilla.get(hora - HORA_DESDE).add(bloque.getUsuarioId());
            }
        }
    }

    /**
     * Agrupa casillas consecutivas del mismo día que cumplen el umbral.
     *
     * El porcentaje de la ventana es el MÍNIMO de sus casillas, no el promedio:
     * si de 10 a 11 está libre todo el grupo pero de 11 a 12 solo el 80 %, la
     * ventana 10-12 se ofrece como 80 %, que es lo que el organizador puede
     * dar por seguro durante todo el tramo.
     */
    private List<VentanaSugeridaResponse> ventanas(List<CeldaDisponibilidadResponse> celdas, int total) {
        List<VentanaSugeridaResponse> ventanas = new ArrayList<>();

        for (int dia = 1; dia <= DIAS; dia++) {
            Integer inicio = null;
            int fin = 0;
            int minPorcentaje = 100;
            int minDisponibles = total;

            // Se recorre una hora de más para poder cerrar la ventana que
            // llegue pegada al final de la franja.
            for (int hora = HORA_DESDE; hora <= HORA_HASTA; hora++) {
                CeldaDisponibilidadResponse celda = hora < HORA_HASTA ? celda(celdas, dia, hora) : null;
                boolean sigue = celda != null && celda.cumpleUmbral();

                if (sigue) {
                    if (inicio == null) {
                        inicio = hora;
                        minPorcentaje = 100;
                        minDisponibles = total;
                    }
                    fin = hora + 1;
                    minPorcentaje = Math.min(minPorcentaje, celda.porcentaje());
                    minDisponibles = Math.min(minDisponibles, celda.disponibles());
                } else if (inicio != null) {
                    ventanas.add(new VentanaSugeridaResponse(
                            "d" + dia + "-" + inicio,
                            dia, LocalTime.of(inicio, 0), horaDeCierre(fin),
                            minPorcentaje, minDisponibles, total));
                    inicio = null;
                }
            }
        }

        // Primero las de mayor cobertura; a igual cobertura, las más largas;
        // y a igualdad de ambas, el orden natural de la semana.
        ventanas.sort(Comparator
                .comparingInt(VentanaSugeridaResponse::disponibilidadPorcentaje).reversed()
                .thenComparing(Comparator.comparingInt(this::duracionHoras).reversed())
                .thenComparingInt(VentanaSugeridaResponse::diaSemana)
                .thenComparing(VentanaSugeridaResponse::horaInicio));

        return ventanas.size() > MAX_VENTANAS ? List.copyOf(ventanas.subList(0, MAX_VENTANAS)) : ventanas;
    }

    private int duracionHoras(VentanaSugeridaResponse v) {
        int fin = v.horaFin().equals(LocalTime.MIDNIGHT) ? 24 : v.horaFin().getHour();
        return fin - v.horaInicio().getHour();
    }

    /** LocalTime.of(24,0) no existe; una ventana que llega al final del día cierra en 00:00. */
    private LocalTime horaDeCierre(int hora) {
        return hora >= 24 ? LocalTime.MIDNIGHT : LocalTime.of(hora, 0);
    }

    private CeldaDisponibilidadResponse celda(List<CeldaDisponibilidadResponse> celdas, int dia, int hora) {
        int porDia = HORA_HASTA - HORA_DESDE;
        return celdas.get((dia - 1) * porDia + (hora - HORA_DESDE));
    }

    private int minutos(LocalTime hora, int siEsNula) {
        return hora == null ? siEsNula : hora.getHour() * 60 + hora.getMinute();
    }

    private List<List<Set<String>>> rejillaVacia() {
        List<List<Set<String>>> rejilla = new ArrayList<>(DIAS);
        for (int dia = 0; dia < DIAS; dia++) {
            List<Set<String>> horas = new ArrayList<>(HORA_HASTA - HORA_DESDE);
            for (int hora = HORA_DESDE; hora < HORA_HASTA; hora++) {
                horas.add(new HashSet<>());
            }
            rejilla.add(horas);
        }
        return rejilla;
    }
}
