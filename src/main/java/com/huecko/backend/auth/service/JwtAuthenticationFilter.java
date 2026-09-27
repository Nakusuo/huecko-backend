package com.huecko.backend.auth.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee `Authorization: Bearer <jwt>` y, si el token es válido, deja la identidad
 * en el SecurityContext. Si no hay cabecera o el token no sirve, no autentica y
 * deja que la cadena decida (las rutas públicas siguen; el resto da 401).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            /* El token vive 12 h y no guarda si la cuenta se suspendió después.
               Una consulta por clave primaria en cada petición es el precio de
               que suspender surta efecto al momento y no al caducar el token. */
            jwtService.validar(header.substring(PREFIJO.length()).trim())
                    .filter(usuario -> !usuarioRepository.existsByIdAndSuspendidoTrue(usuario.id()))
                    .ifPresent(usuario -> {
                        String authority = usuario.esAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
                        var authentication = new UsernamePasswordAuthenticationToken(
                                usuario, null, List.of(new SimpleGrantedAuthority(authority)));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }
}
