package com.huecko.backend.auth.service;

import com.huecko.backend.auth.dto.AuthResponse;
import com.huecko.backend.auth.dto.LoginRequest;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.postgres.entity.CodigoRespaldo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.CodigoRespaldoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Activar, entrar con el código y desactivar, con repositorios simulados en memoria. */
class DosPasosServiceTest {

    private static final String CLAVE = "clave-de-pruebas-huecko-con-mas-de-32-caracteres";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CodigoRespaldoRepository respaldos = mock(CodigoRespaldoRepository.class);
    private final CifradoSecretos cifrado = new CifradoSecretos(CLAVE);
    private final DosPasosService dosPasos = new DosPasosService(usuarios, respaldos, cifrado, encoder);
    private final JwtService jwt = new JwtService(CLAVE, 60, new MockEnvironment());
    private final AuthService auth = new AuthService(usuarios, encoder, jwt, dosPasos);

    /** Lo que habría en la tabla `codigos_respaldo`. */
    private final List<CodigoRespaldo> guardados = new ArrayList<>();
    private Usuario ana;
    private final Instant ahora = Instant.ofEpochSecond(1_700_000_000);

    @BeforeEach
    void preparar() {
        ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("ana@h.com")
                .passwordHash(encoder.encode("secreta123")).build();
        when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarios.findByEmailIgnoreCase("ana@h.com")).thenReturn(Optional.of(ana));
        when(respaldos.saveAll(any())).thenAnswer(inv -> {
            inv.<Iterable<CodigoRespaldo>>getArgument(0).forEach(guardados::add);
            return null;
        });
        when(respaldos.borrarDelUsuario(ana.getId())).thenAnswer(inv -> {
            guardados.clear();
            return 0;
        });
        when(respaldos.findByUsuarioIdAndCodigoHashAndUsadoEnIsNull(eq(ana.getId()), anyString()))
                .thenAnswer(inv -> guardados.stream()
                        .filter(c -> c.getUsadoEn() == null && c.getCodigoHash().equals(inv.getArgument(1)))
                        .findFirst());
    }

    /** Lo que mostraría la app del móvil en un instante. */
    private String codigoDeLaApp(String secreto, Instant momento) {
        return Totp.codigo(secreto, Totp.paso(momento));
    }

    private List<String> activar() {
        String secreto = dosPasos.iniciar(ana.getId()).secreto();
        return dosPasos.activar(ana.getId(), codigoDeLaApp(secreto, ahora), ahora);
    }

    @Test
    @DisplayName("el secreto se guarda cifrado y la verificación no cuenta hasta el primer código")
    void iniciarNoActiva() {
        DosPasosService.Configuracion config = dosPasos.iniciar(ana.getId());

        assertThat(ana.isDosPasosActivo()).isFalse();
        assertThat(ana.getDosPasosSecreto()).isNotEqualTo(config.secreto());
        assertThat(cifrado.descifrar(ana.getDosPasosSecreto())).isEqualTo(config.secreto());
        assertThat(config.uri()).startsWith("otpauth://totp/Huecko:");
    }

    @Test
    @DisplayName("con un código correcto se activa y entrega 10 códigos de respaldo")
    void activa() {
        List<String> codigos = activar();

        assertThat(ana.isDosPasosActivo()).isTrue();
        assertThat(codigos).hasSize(10).allMatch(c -> c.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertThat(guardados).hasSize(10).noneMatch(c -> codigos.contains(c.getCodigoHash()));
    }

    @Test
    @DisplayName("con un código incorrecto no se activa")
    void codigoMalNoActiva() {
        dosPasos.iniciar(ana.getId());

        assertThatThrownBy(() -> dosPasos.activar(ana.getId(), "000000", ahora))
                .isInstanceOf(BusinessException.class);
        assertThat(ana.isDosPasosActivo()).isFalse();
    }

    @Test
    @DisplayName("con la verificación activa, la contraseña sola no da sesión")
    void loginPideCodigo() {
        activar();

        AuthResponse respuesta = auth.login(new LoginRequest("ana@h.com", "secreta123"));

        assertThat(respuesta.token()).isNull();
        assertThat(respuesta.requiereDosPasos()).isTrue();
        assertThat(auth.usuarioDelDesafio(respuesta.desafio())).isEqualTo(ana.getId());
    }

    @Test
    @DisplayName("el código de la app completa el login, y no sirve dos veces")
    void loginConCodigo() {
        String secreto = dosPasos.iniciar(ana.getId()).secreto();
        dosPasos.activar(ana.getId(), codigoDeLaApp(secreto, ahora), ahora);
        Instant despues = ahora.plusSeconds(60);
        String codigo = codigoDeLaApp(secreto, despues);

        AuthResponse respuesta = auth.completarDosPasos(ana.getId(), codigo, despues);

        assertThat(respuesta.token()).isNotBlank();
        assertThat(jwt.validar(respuesta.token())).isPresent();
        assertThatThrownBy(() -> auth.completarDosPasos(ana.getId(), codigo, despues))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("un código de respaldo entra una sola vez, con o sin guion")
    void respaldoDeUnSoloUso() {
        String codigo = activar().get(0);

        assertThat(auth.completarDosPasos(ana.getId(), codigo.toLowerCase().replace("-", ""), ahora).token())
                .isNotBlank();
        assertThatThrownBy(() -> auth.completarDosPasos(ana.getId(), codigo, ahora))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("desactivar pide la contraseña correcta y borra secreto y códigos")
    void desactivar() {
        List<String> codigos = activar();

        assertThatThrownBy(() -> dosPasos.desactivar(ana.getId(), "otra", codigos.get(0), ahora))
                .isInstanceOf(BusinessException.class);
        assertThat(ana.isDosPasosActivo()).isTrue();

        dosPasos.desactivar(ana.getId(), "secreta123", codigos.get(1), ahora);

        assertThat(ana.isDosPasosActivo()).isFalse();
        assertThat(ana.getDosPasosSecreto()).isNull();
        assertThat(guardados).isEmpty();
        assertThat(auth.login(new LoginRequest("ana@h.com", "secreta123")).token()).isNotBlank();
    }
}
