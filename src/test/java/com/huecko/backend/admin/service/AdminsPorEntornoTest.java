package com.huecko.backend.admin.service;

import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminsPorEntornoTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);

    private static Usuario cuenta(String email) {
        return Usuario.builder().nombre("X").email(email).passwordHash("h").build();
    }

    @Test
    @DisplayName("promueve las cuentas de la lista, sin importar espacios ni mayúsculas")
    void promueve() {
        Usuario ana = cuenta("ana@huecko.com");
        when(repo.findByEmailIgnoreCase("ana@huecko.com")).thenReturn(Optional.of(ana));

        new AdminsPorEntorno(repo, "  Ana@Huecko.com , ").run();

        assertThat(ana.getRolSistema()).isEqualTo(Usuario.RolSistema.ADMIN);
        verify(repo).save(ana);
    }

    @Test
    @DisplayName("un correo sin cuenta se ignora y la lista vacía no toca nada")
    void sinCuentaOListaVacia() {
        when(repo.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        new AdminsPorEntorno(repo, "nadie@huecko.com").run();
        new AdminsPorEntorno(repo, "").run();

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("una cuenta nueva nace como usuario normal")
    void porDefecto() {
        assertThat(cuenta("x@huecko.com").getRolSistema()).isEqualTo(Usuario.RolSistema.USUARIO);
    }
}
