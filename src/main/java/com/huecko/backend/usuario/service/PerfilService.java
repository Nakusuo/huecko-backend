package com.huecko.backend.usuario.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.UnauthorizedException;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.usuario.dto.ActualizarPerfilRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cambios en el perfil propio.
 *
 * El correo es la llave de la cuenta y, si está en HUECKO_ADMIN_EMAILS, del
 * rol de administración (AdminsPorEntorno promueve en cada arranque). Así que:
 *  - cambiarlo pide la contraseña actual (un token robado no basta);
 *  - nadie puede ponerse un correo de esa lista;
 *  - una cuenta admin no cambia el suyo desde la app: dejaría libre en la
 *    lista un correo que otra persona podría registrar.
 */
@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Set<String> correosAdmin;

    public PerfilService(UsuarioRepository usuarioRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${huecko.admin.emails:}") String correosAdmin) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.correosAdmin = Arrays.stream(correosAdmin.split(","))
                .map(email -> email.trim().toLowerCase())
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public Usuario cargar(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("Tu cuenta ya no existe. Inicia sesión de nuevo."));
    }

    @Transactional
    public Usuario actualizar(UUID id, ActualizarPerfilRequest request) {
        Usuario usuario = cargar(id);

        if (request.nombre() != null) {
            usuario.setNombre(request.nombre().trim());
        }

        if (request.email() != null) {
            String email = request.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(usuario.getEmail())) {
                cambiarCorreo(usuario, email, request.passwordActual());
            }
        }

        return usuarioRepository.save(usuario);
    }

    private void cambiarCorreo(Usuario usuario, String email, String passwordActual) {
        if (usuario.getRolSistema() == Usuario.RolSistema.ADMIN) {
            throw new ForbiddenException(
                    "El correo de una cuenta de administración se cambia en HUECKO_ADMIN_EMAILS, no desde la app.");
        }
        if (passwordActual == null || passwordActual.isEmpty()
                || !passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
            throw new BusinessException("Para cambiar el correo escribe tu contraseña actual.");
        }
        // Mismo mensaje que "ocupado": no se revela qué correos son de administración.
        if (correosAdmin.contains(email) || usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("El correo ya está en uso. Intenta con otro.");
        }
        usuario.setEmail(email);
    }
}
