package com.huecko.backend.tiemporeal;

import com.huecko.backend.auth.service.JwtService;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RNF-01 y RNF-02 sobre el canal WebSocket.
 *
 * La prueba que más importa aquí es la de la suscripción ajena: sin ella, el
 * canal deja leer los retrasos y las ausencias de un grupo al que no perteneces
 * con solo conocer su identificador, y eso no lo detecta ninguna prueba de los
 * módulos de negocio.
 */
class SeguridadStompInterceptorTest {

    private static final UUID GRUPO = UUID.randomUUID();
    private static final UUID OTRO_GRUPO = UUID.randomUUID();
    private static final UsuarioAutenticado ANA =
            new UsuarioAutenticado(UUID.randomUUID(), "ana@huecko.app", "Ana");

    private JwtService jwtService;
    private MiembroGrupoRepository miembros;
    private SeguridadStompInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        miembros = mock(MiembroGrupoRepository.class);
        interceptor = new SeguridadStompInterceptor(jwtService, miembros);
    }

    // --- CONNECT ---

    @Test
    @DisplayName("CONNECT con token valido deja la identidad en la sesion")
    void connectValido() {
        when(jwtService.validar("bueno")).thenReturn(Optional.of(ANA));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bueno");

        interceptor.preSend(mensaje(accessor), null);

        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal())
                .isEqualTo(ANA);
    }

    @Test
    @DisplayName("CONNECT sin cabecera Authorization se rechaza")
    void connectSinCabecera() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(mensaje(accessor), null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Authorization");
    }

    @Test
    @DisplayName("CONNECT con token invalido se rechaza")
    void connectTokenInvalido() {
        when(jwtService.validar(any())).thenReturn(Optional.empty());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer caducado");

        assertThatThrownBy(() -> interceptor.preSend(mensaje(accessor), null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("caducado");
    }

    // --- SUBSCRIBE ---

    @Test
    @DisplayName("Un miembro puede suscribirse al topic de su grupo")
    void suscripcionPropia() {
        when(miembros.existsByGrupo_IdAndUsuario_Id(GRUPO, ANA.id())).thenReturn(true);

        StompHeaderAccessor accessor = suscripcionAutenticada(Destinos.grupo(GRUPO));

        assertThat(interceptor.preSend(mensaje(accessor), null)).isNotNull();
    }

    @Test
    @DisplayName("Suscribirse al grupo de otro se rechaza aunque el token sea valido")
    void suscripcionAjena() {
        when(miembros.existsByGrupo_IdAndUsuario_Id(OTRO_GRUPO, ANA.id())).thenReturn(false);

        StompHeaderAccessor accessor = suscripcionAutenticada(Destinos.grupo(OTRO_GRUPO));

        assertThatThrownBy(() -> interceptor.preSend(mensaje(accessor), null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("No perteneces");
    }

    @Test
    @DisplayName("Un destino que no es de grupo se rechaza")
    void destinoDesconocido() {
        StompHeaderAccessor accessor = suscripcionAutenticada("/topic/todo");

        assertThatThrownBy(() -> interceptor.preSend(mensaje(accessor), null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("no permitido");
    }

    @Test
    @DisplayName("SUBSCRIBE sin CONNECT previo se rechaza")
    void suscripcionSinAutenticar() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(Destinos.grupo(GRUPO));

        assertThatThrownBy(() -> interceptor.preSend(mensaje(accessor), null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("no está autenticada");
    }

    // --- ayudas ---

    private StompHeaderAccessor suscripcionAutenticada(String destino) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destino);
        accessor.setUser(new UsernamePasswordAuthenticationToken(ANA, null, List.of()));
        return accessor;
    }

    /**
     * Cabeceras mutables, como las que crea StompSubProtocolHandler para los
     * frames entrantes. Sin `setLeaveMutable` el interceptor no encontraría el
     * accessor vivo, y la prueba dejaría de reproducir lo que pasa en ejecución.
     */
    private Message<byte[]> mensaje(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
