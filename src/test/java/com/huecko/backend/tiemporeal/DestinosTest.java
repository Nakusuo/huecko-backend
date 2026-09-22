package com.huecko.backend.tiemporeal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF-05. Estas dos funciones son las que mantienen alineados al que publica y
 * al que escucha; si dejaran de ser simétricas, el canal quedaría mudo sin que
 * ninguna excepción lo delatara.
 */
class DestinosTest {

    private static final UUID GRUPO = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("Lo que se construye se puede volver a leer")
    void idaYVuelta() {
        assertThat(Destinos.grupoDe(Destinos.grupo(GRUPO))).contains(GRUPO);
    }

    @Test
    @DisplayName("Un destino de otro prefijo no es de grupo")
    void otroPrefijo() {
        assertThat(Destinos.grupoDe("/topic/usuarios/" + GRUPO)).isEmpty();
    }

    @Test
    @DisplayName("Un identificador que no es UUID no cuela")
    void identificadorInvalido() {
        assertThat(Destinos.grupoDe("/topic/grupos/todos")).isEmpty();
    }

    @Test
    @DisplayName("No se admiten segmentos extra despues del grupo")
    void segmentosExtra() {
        assertThat(Destinos.grupoDe("/topic/grupos/" + GRUPO + "/privado")).isEmpty();
    }

    @Test
    @DisplayName("Destino vacio o nulo no revienta")
    void vacioONulo() {
        assertThat(Destinos.grupoDe(null)).isEmpty();
        assertThat(Destinos.grupoDe("")).isEmpty();
        assertThat(Destinos.grupoDe("/topic/grupos/")).isEmpty();
    }
}
