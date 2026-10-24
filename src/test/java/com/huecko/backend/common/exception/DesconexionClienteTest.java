package com.huecko.backend.common.exception;

import com.huecko.backend.observabilidad.service.RegistroFallos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cerrar el visor de fotos a mitad de una descarga corta la conexión. Eso no
 * es un fallo del servidor y no debe llenar la bandeja del admin.
 */
class DesconexionClienteTest {

    @RestController
    static class Prueba {
        @GetMapping("/se-fue")
        String seFue() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("ServletOutputStream failed to write: Tubería rota");
        }

        @GetMapping("/falla")
        String falla() {
            throw new IllegalStateException("esto sí es un fallo");
        }
    }

    private final RegistroFallos registroFallos = mock(RegistroFallos.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Prueba())
            .setControllerAdvice(new GlobalExceptionHandler(registroFallos))
            .build();

    @Test
    @DisplayName("Si el cliente se fue, no se registra ningún fallo")
    void clienteDesconectado() throws Exception {
        mvc.perform(get("/se-fue"));

        verifyNoInteractions(registroFallos);
    }

    @Test
    @DisplayName("Un error de verdad sigue yendo a la bandeja")
    void errorDeVerdad() throws Exception {
        mvc.perform(get("/falla")).andExpect(status().isInternalServerError());

        verify(registroFallos).registrarServidor(any(IllegalStateException.class), eq("GET"), eq("/falla"));
    }
}
