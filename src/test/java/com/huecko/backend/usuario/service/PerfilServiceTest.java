package com.huecko.backend.usuario.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.usuario.dto.ActualizarPerfilRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cambiar el correo es la vía para quedarse con una cuenta, o con el rol de
 * admin si el correo está en HUECKO_ADMIN_EMAILS. Por eso pide la contraseña
 * y no deja tocar los correos de administración.
 */
class PerfilServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PerfilService perfil = new PerfilService(repo, encoder, "jefa@huecko.com");
    private Usuario ana;

    @BeforeEach
    void cuenta() {
        ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("ana@h.com")
                .passwordHash(encoder.encode("secreta123")).build();
        when(repo.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("cambiar el nombre no pide contraseña")
    void nombreSinContrasena() {
        perfil.actualizar(ana.getId(), new ActualizarPerfilRequest("Ana María", null, null));

        assertThat(ana.getNombre()).isEqualTo("Ana María");
    }

    @Test
    @DisplayName("cambiar el correo exige la contraseña actual")
    void correoPideContrasena() {
        assertThatThrownBy(() -> perfil.actualizar(ana.getId(), new ActualizarPerfilRequest(null, "nueva@h.com", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("contraseña");
        assertThatThrownBy(() -> perfil.actualizar(ana.getId(), new ActualizarPerfilRequest(null, "nueva@h.com", "mal")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("contraseña");
        assertThat(ana.getEmail()).isEqualTo("ana@h.com");

        perfil.actualizar(ana.getId(), new ActualizarPerfilRequest(null, " Nueva@h.com ", "secreta123"));
        assertThat(ana.getEmail()).isEqualTo("nueva@h.com");
    }

    @Test
    @DisplayName("mandar el mismo correo que ya tiene no pide contraseña")
    void mismoCorreo() {
        perfil.actualizar(ana.getId(), new ActualizarPerfilRequest("Ana", "ANA@h.com", null));

        assertThat(ana.getEmail()).isEqualTo("ana@h.com");
    }

    @Test
    @DisplayName("nadie puede ponerse un correo de la lista de administración")
    void noCorreoDeAdmin() {
        assertThatThrownBy(() -> perfil.actualizar(ana.getId(),
                new ActualizarPerfilRequest(null, "Jefa@huecko.com", "secreta123")))
                .isInstanceOf(BusinessException.class);
        assertThat(ana.getEmail()).isEqualTo("ana@h.com");
    }

    @Test
    @DisplayName("una cuenta de administración no cambia su correo desde la app")
    void adminNoCambiaCorreo() {
        ana.setRolSistema(Usuario.RolSistema.ADMIN);

        assertThatThrownBy(() -> perfil.actualizar(ana.getId(),
                new ActualizarPerfilRequest(null, "otra@h.com", "secreta123")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("un correo que ya usa otra cuenta se rechaza")
    void correoOcupado() {
        when(repo.existsByEmailIgnoreCase("bea@h.com")).thenReturn(true);

        assertThatThrownBy(() -> perfil.actualizar(ana.getId(),
                new ActualizarPerfilRequest(null, "bea@h.com", "secreta123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");
    }
}
