package com.huecko.backend.tiemporeal;

import com.huecko.backend.auth.service.JwtService;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Seguridad del canal WebSocket (RNF-01 y RNF-02).
 *
 * Hace dos cosas, en dos momentos distintos del protocolo STOMP:
 *
 * <ol>
 *   <li><b>CONNECT</b> — autentica. El JWT viaja en la cabecera del frame, no
 *       en la URL del handshake: un token en el query string acaba escrito en
 *       los logs de acceso del servidor y en el historial del navegador.</li>
 *   <li><b>SUBSCRIBE</b> — autoriza. Sin esto, `/topic/grupos/{id}` sería
 *       legible por cualquier usuario autenticado con solo adivinar el id del
 *       grupo, y por ahí se escaparían los retrasos y las ausencias de gente
 *       ajena al grupo.</li>
 * </ol>
 *
 * <p>Cualquier otro comando (SEND, ACK, BEGIN…) se rechaza. El cliente nunca
 * publica: sin esta regla, un SEND a `/topic/grupos/{id}` llegaría tal cual a
 * todo el grupo y permitiría inventar retrasos o planes confirmados.</p>
 *
 * El handshake HTTP de `/api/ws` es público en {@code SecurityConfig} a
 * propósito: cuando llega, el cliente todavía no ha podido mandar el CONNECT.
 * La puerta real es este interceptor, no la cadena de filtros.
 */
@Component
@RequiredArgsConstructor
public class SeguridadStompInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SeguridadStompInterceptor.class);
    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final MiembroGrupoRepository miembroGrupoRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // `getAccessor` devuelve el accessor VIVO del mensaje; `wrap` devolvería
        // una copia y el `setUser` del CONNECT se perdería sin dar ningún error.
        StompHeaderAccessor mutable =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompHeaderAccessor lectura = mutable != null ? mutable : StompHeaderAccessor.wrap(message);
        StompCommand comando = lectura.getCommand();

        // Latidos y mensajes internos del broker no llevan comando.
        if (comando == null) {
            return message;
        }

        switch (comando) {
            // STOMP es un alias de CONNECT en el protocolo: sin tratarlo igual,
            // se podía abrir una sesión sin pasar por la autenticación.
            case CONNECT, STOMP -> {
                if (mutable == null) {
                    // Sin accessor mutable no hay forma de dejar la identidad en la
                    // sesión. Rechazar: dejarlo pasar autenticaría a nadie.
                    throw new MessageDeliveryException("No se pudo establecer la identidad de la sesión");
                }
                autenticar(mutable);
                return message;
            }
            case SUBSCRIBE -> {
                return autorizarSuscripcion(lectura) ? message : null;
            }
            case UNSUBSCRIBE, DISCONNECT -> {
                return message;
            }
            default -> throw new MessageDeliveryException("Comando no permitido: " + comando);
        }
    }

    private void autenticar(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(PREFIJO)) {
            throw new MessageDeliveryException("Falta la cabecera Authorization en el CONNECT");
        }

        UsuarioAutenticado usuario = jwtService.validar(header.substring(PREFIJO.length()).trim())
                .orElseThrow(() -> new MessageDeliveryException("Token inválido o caducado"));

        // Mismo principal que en HTTP: los dos caminos dejan un
        // UsuarioAutenticado, así nada aguas abajo tiene que distinguirlos.
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    /**
     * Devuelve {@code false} si la suscripción no se permite. En ese caso el
     * mensaje se descarta sin lanzar: una excepción aquí cierra la conexión
     * entera, y un solo grupo del que te sacaron dejaba sin tiempo real a
     * todos los demás (el cliente reconectaba y volvía a caer cada 5 s).
     */
    private boolean autorizarSuscripcion(StompHeaderAccessor accessor) {
        // Sin CONNECT previo la sesión está rota: eso sí cierra la conexión.
        UsuarioAutenticado usuario = usuarioDe(accessor);
        String destino = accessor.getDestination();

        Optional<UUID> grupoId = Destinos.grupoDe(destino);
        if (grupoId.isEmpty()) {
            log.warn("Suscripción rechazada: destino no permitido {} (usuario {})", destino, usuario.id());
            return false;
        }

        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(grupoId.get(), usuario.id())) {
            // Mismo trato que si el grupo no existiera: distinguirlos
            // permitiría averiguar qué grupos hay probando identificadores.
            log.warn("Suscripción rechazada: el usuario {} no pertenece al grupo {}", usuario.id(), grupoId.get());
            return false;
        }
        return true;
    }

    private UsuarioAutenticado usuarioDe(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new MessageDeliveryException("La sesión no está autenticada");
    }
}
