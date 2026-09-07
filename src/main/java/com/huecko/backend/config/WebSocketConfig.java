package com.huecko.backend.config;

import com.huecko.backend.tiemporeal.SeguridadStompInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Canal en tiempo real sobre STOMP (RNF-05: menos de 3 s hasta todos los
 * clientes conectados).
 *
 * <b>Broker en memoria, no RabbitMQ.</b> RNF-09 obliga a que todo corra en
 * capas gratuitas, y RNF-07 acota los grupos a ~20 personas: un broker externo
 * añadiría un servicio que mantener y pagar para un volumen que cabe de sobra
 * en el proceso. El precio de esta decisión es que el día que haya más de una
 * instancia del backend, dos clientes conectados a instancias distintas dejarán
 * de verse — ahí toca `enableStompBrokerRelay`, y solo ahí.
 *
 * <b>Con SockJS.</b> RNF-08 pide navegadores de escritorio y móviles; algunas
 * redes corporativas y proxies cortan el upgrade a WebSocket, y el respaldo por
 * HTTP evita que la app quede muda sin explicación.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SeguridadStompInterceptor seguridadStompInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(SeguridadStompInterceptor seguridadStompInterceptor,
                           @Value("${huecko.cors.allowed-origins}") String allowedOrigins) {
        this.seguridadStompInterceptor = seguridadStompInterceptor;
        // Mismo origen permitido que en HTTP: dos listas separadas acabarían
        // divergiendo y el fallo aparecería solo en producción.
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        // El cliente no publica: todo lo que va del cliente al servidor pasa por
        // REST, donde ya están la validación y el control de acceso. Dejar
        // abierto un prefijo de aplicación sería una segunda puerta de entrada
        // con otras reglas.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(seguridadStompInterceptor);
    }
}
