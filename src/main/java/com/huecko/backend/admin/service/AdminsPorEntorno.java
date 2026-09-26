package com.huecko.backend.admin.service;

import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Da el rol ADMIN a las cuentas de `huecko.admin.emails` al arrancar.
 *
 * Es la forma de tener administradores en producción, donde no hay seed: la
 * persona se registra como cualquier otra y en el siguiente arranque queda
 * promovida. Solo promueve, nunca degrada: quitar un correo de la lista no le
 * retira el rol a nadie. Un correo sin cuenta se avisa en el log y se ignora.
 */
@Component
public class AdminsPorEntorno implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminsPorEntorno.class);

    private final UsuarioRepository usuarioRepository;
    private final List<String> emails;

    public AdminsPorEntorno(UsuarioRepository usuarioRepository,
                            @Value("${huecko.admin.emails:}") String emails) {
        this.usuarioRepository = usuarioRepository;
        this.emails = Arrays.stream(emails.split(","))
                .map(email -> email.trim().toLowerCase())
                .filter(email -> !email.isEmpty())
                .toList();
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (String email : emails) {
            usuarioRepository.findByEmailIgnoreCase(email).ifPresentOrElse(
                    this::promover,
                    () -> log.warn("HUECKO_ADMIN_EMAILS: {} no tiene cuenta todavía. Se promoverá cuando se registre y se reinicie.", email));
        }
    }

    private void promover(Usuario usuario) {
        if (usuario.getRolSistema() == Usuario.RolSistema.ADMIN) {
            return;
        }
        usuario.setRolSistema(Usuario.RolSistema.ADMIN);
        usuarioRepository.save(usuario);
        log.info("{} ahora es administrador.", usuario.getEmail());
    }
}
