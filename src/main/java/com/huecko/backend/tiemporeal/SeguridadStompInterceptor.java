package com.huecko.backend.tiemporeal;

import com.huecko.backend.auth.service.JwtService;
import com.huecko.backend.auth.service.UsuarioAutenticado;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import lombok.RequiredArgsConstructor;
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
 * El handshake HTTP de `/api/ws` es público en {@code SecurityConfig} a
 * propósito: cuando llega, el cliente todavía no ha podido mandar el CONNECT.
 * La puerta real es este interceptor, no la cadena de filtros.
 */
@Component
@RequiredArgsConstructor
public class SeguridadStompInterceptor implements ChannelInterceptor {

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

        if (StompCommand.CONNECT.equals(comando)) {
            if (mutable == null) {
                // Sin accessor mutable no hay forma de dejar la identidad en la
                // sesión. Rechazar: dejarlo pasar autenticaría a nadie.
                throw new MessageDeliveryException("No se pudo establecer la identidad de la sesión");
            }
            autenticar(mutable);
        } else if (StompCommand.SUBSCRIBE.equals(comando)) {
            autorizarSuscripcion(lectura);
        }

        return message;
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

    private void autorizarSuscripcion(StompHeaderAccessor accessor) {
        UsuarioAutenticado usuario = usuarioDe(accessor);
        String destino = accessor.getDestination();

        UUID grupoId = Destinos.grupoDe(destino).orElseThrow(() ->
                new MessageDeliveryException("Destino no permitido: " + destino));

        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(grupoId, usuario.id())) {
            // Mismo mensaje que si el grupo no existiera: distinguirlos
            // permitiría averiguar qué grupos hay probando identificadores.
            throw new MessageDeliveryException("No perteneces a ese grupo");
        }
    }

    private UsuarioAutenticado usuarioDe(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new MessageDeliveryException("La sesión no está autenticada");
    }
}
