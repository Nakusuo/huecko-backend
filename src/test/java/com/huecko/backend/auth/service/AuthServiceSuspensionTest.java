package com.huecko.backend.auth.service;

import com.huecko.backend.auth.dto.LoginRequest;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceSuspensionTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final AuthService auth = new AuthService(repo, encoder, mock(JwtService.class));

    private void cuentaSuspendida() {
        Usuario ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("ana@h.com")
                .passwordHash(encoder.encode("secreta123")).suspendido(true).build();
        when(repo.findByEmailIgnoreCase("ana@h.com")).thenReturn(Optional.of(ana));
    }

    @Test
    @DisplayName("una cuenta suspendida no entra aunque la contraseña sea correcta")
    void suspendidaNoEntra() {
        cuentaSuspendida();
        assertThatThrownBy(() -> auth.login(new LoginRequest("ana@h.com", "secreta123")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(AuthService.CUENTA_SUSPENDIDA);
    }

    @Test
    @DisplayName("con la contraseña mal no se revela que la cuenta está suspendida")
    void noDelata() {
        cuentaSuspendida();
        assertThatThrownBy(() -> auth.login(new LoginRequest("ana@h.com", "otra-cosa")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Credenciales incorrectas");
    }
}
