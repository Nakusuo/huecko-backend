package com.huecko.backend.ia.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.ia.dto.EscaneoHorarioResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Escaneo del horario con IA, traído de la app móvil. */
@ExtendWith(MockitoExtension.class)
class EscaneoHorarioServiceTest {

    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final byte[] PNG = conFirma(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    private static final byte[] JPEG = conFirma(0xFF, 0xD8, 0xFF, 0xE0);
    private static final byte[] WEBP = conFirma('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
    private static final byte[] PDF = conFirma('%', 'P', 'D', 'F', '-');

    @Mock
    private ClienteIA clienteIA;

    @Mock
    private AccesoAsistente acceso;

    private EscaneoHorarioService service() {
        return new EscaneoHorarioService(clienteIA, acceso);
    }

    @Test
    @DisplayName("Manda la foto con el tipo de sus bytes, no con el que dijo el navegador")
    void usaElTipoReal() {
        when(clienteIA.escanearHorario(eq("image/png"), any())).thenReturn(new ClienteIA.RespuestaEscaneo(List.of(
                new ClienteIA.BloqueLeido(1, "08:00", "10:00", "Cálculo", false),
                new ClienteIA.BloqueLeido(3, "14:00", "16:00", "Física", true))));

        EscaneoHorarioResponse r = service().escanear(USUARIO, foto(PNG, "image/jpeg"));

        assertThat(r.bloques()).containsExactly(
                new EscaneoHorarioResponse.Bloque(1, "08:00", "10:00", "Cálculo", false),
                new EscaneoHorarioResponse.Bloque(3, "14:00", "16:00", "Física", true));
        verify(acceso).exigir(USUARIO);
    }

    @Test
    @DisplayName("Reconoce JPG, PNG y WEBP por su firma, y nada más")
    void reconoceLosTiposAdmitidos() {
        assertThat(EscaneoHorarioService.tipoReal(JPEG)).contains("image/jpeg");
        assertThat(EscaneoHorarioService.tipoReal(PNG)).contains("image/png");
        assertThat(EscaneoHorarioService.tipoReal(WEBP)).contains("image/webp");
        assertThat(EscaneoHorarioService.tipoReal(PDF)).isEmpty();
        assertThat(EscaneoHorarioService.tipoReal(new byte[]{(byte) 0xFF})).isEmpty();
    }

    @Test
    @DisplayName("Un archivo que no es imagen se rechaza sin gastar cupo ni llamar a la IA")
    void rechazaLoQueNoEsImagen() {
        assertThatThrownBy(() -> service().escanear(USUARIO, foto(PDF, "image/png")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JPG, PNG o WEBP");

        verifyNoInteractions(acceso, clienteIA);
    }

    @Test
    @DisplayName("Sin archivo, o vacío, pide la foto")
    void pideLaFoto() {
        assertThatThrownBy(() -> service().escanear(USUARIO, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service().escanear(USUARIO, foto(new byte[0], "image/png")))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(acceso, clienteIA);
    }

    @Test
    @DisplayName("Más de 5 MB se rechaza aunque el tope de Spring cambie")
    void topeDeTamano() {
        byte[] grande = Arrays.copyOf(PNG, (int) EscaneoHorarioService.MAX_BYTES + 1);

        assertThatThrownBy(() -> service().escanear(USUARIO, foto(grande, "image/png")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5 MB");
        verifyNoInteractions(clienteIA);
    }

    @Test
    @DisplayName("Si la IA no responde, 503 con un mensaje para la persona")
    void iaCaida() {
        when(clienteIA.escanearHorario(any(), any()))
                .thenThrow(new IaNoDisponibleException("/v1/horario/escaneo: ResourceAccessException", null));

        assertThatThrownBy(() -> service().escanear(USUARIO, foto(PNG, "image/png")))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessage(AccesoAsistente.NO_DISPONIBLE);
    }

    @Test
    @DisplayName("Sin cupo no se llama a la IA")
    void sinCupo() {
        doThrow(new DemasiadosIntentosException("Usaste mucho la IA en poco tiempo."))
                .when(acceso).exigir(USUARIO);

        assertThatThrownBy(() -> service().escanear(USUARIO, foto(PNG, "image/png")))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(clienteIA, never()).escanearHorario(any(), any());
    }

    @Test
    @DisplayName("Una foto que no era un horario devuelve una lista vacía, no un error")
    void fotoSinHorario() {
        when(clienteIA.escanearHorario(any(), any())).thenReturn(new ClienteIA.RespuestaEscaneo(null));

        assertThat(service().escanear(USUARIO, foto(JPEG, "image/jpeg")).bloques()).isEmpty();
    }

    @Test
    @DisplayName("Un bloque con campos nulos se descarta en vez de romper la respuesta")
    void descartaBloquesIncompletos() {
        when(clienteIA.escanearHorario(any(), any())).thenReturn(new ClienteIA.RespuestaEscaneo(Arrays.asList(
                null,
                new ClienteIA.BloqueLeido(null, "08:00", "10:00", "Sin día", false),
                new ClienteIA.BloqueLeido(2, "08:00", "10:00", "Química", null))));

        assertThat(service().escanear(USUARIO, foto(PNG, "image/png")).bloques())
                .containsExactly(new EscaneoHorarioResponse.Bloque(2, "08:00", "10:00", "Química", false));
    }

    private static MockMultipartFile foto(byte[] contenido, String tipoDeclarado) {
        return new MockMultipartFile("imagen", "horario", tipoDeclarado, contenido);
    }

    private static byte[] conFirma(int... firma) {
        byte[] datos = new byte[64];
        for (int i = 0; i < firma.length; i++) {
            datos[i] = (byte) firma[i];
        }
        return datos;
    }
}
