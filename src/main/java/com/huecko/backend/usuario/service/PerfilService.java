package com.huecko.backend.usuario.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.UnauthorizedException;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.usuario.dto.ActualizarPerfilRequest;
import com.huecko.backend.usuario.dto.PerfilSocialRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Reemplaza el perfil social. Los textos vacíos se guardan como nulos. */
    @Transactional
    public Usuario actualizarSocial(UUID id, PerfilSocialRequest request) {
        Usuario usuario = cargar(id);
        usuario.setCarrera(texto(request.carrera()));
        usuario.setCiclo(request.ciclo());
        usuario.setBio(texto(request.bio()));
        usuario.setIntereses(intereses(request.intereses()));
        return usuarioRepository.save(usuario);
    }

    private static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    /** Sin vacíos ni repetidos («Fútbol» y «fútbol» son el mismo), en el orden en que llegan. */
    static List<String> intereses(List<String> recibidos) {
        if (recibidos == null) {
            return new ArrayList<>();
        }
        Map<String, String> unicos = new LinkedHashMap<>();
        for (String interes : recibidos) {
            String limpio = texto(interes);
            if (limpio != null) {
                unicos.putIfAbsent(limpio.toLowerCase(Locale.ROOT), limpio);
            }
        }
        return new ArrayList<>(unicos.values());
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
