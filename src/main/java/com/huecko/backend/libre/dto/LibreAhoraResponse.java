package com.huecko.backend.libre.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * «¿Quién está libre ahora?» visto por una persona.
 *
 * RNF-02: de los demás solo viaja un recuento por grupo (como en el heatmap)
 * y los nombres de quienes avisaron de que están libres. Que
 * alguien no tenga clase nunca se dice con su nombre.
 *
 * @param yo         tu aviso, o nulo si no avisaste (o ya pasó la hora)
 * @param enClase    si según tu propio horario ahora tienes un bloque
 * @param sugerencia hasta cuándo estás libre según tu horario; nula si estás en clase o ya es de noche
 */
public record LibreAhoraResponse(MiAviso yo, boolean enClase, Instant sugerencia, List<GrupoLibre> grupos) {

    public record MiAviso(Instant hasta, String nota) {
    }

    /** `sinClase` cuenta a todo el grupo, también a ti; `personas` nunca te incluye. */
    public record GrupoLibre(UUID grupoId, String nombre, int sinClase, int total, List<PersonaLibre> personas) {
    }

    public record PersonaLibre(UUID usuarioId, String nombre, Instant fotoActualizadaEn, Instant hasta, String nota) {
    }
}
