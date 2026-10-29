package com.huecko.backend.auth.service;

import com.huecko.backend.common.LimitadorDeIntentos;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Freno a la fuerza bruta en login y registro.
 *
 * - Por correo: tras unos pocos fallos seguidos, ese correo no puede intentarlo
 *   durante un rato, venga de la IP que venga. Protege cada cuenta.
 * - Por IP: una misma IP no puede probar muchos correos ni crear cuentas en
 *   masa. Además cada login cuesta un BCrypt, que en el plan gratuito de
 *   Render bastaría para tumbar la API.
 *
 * La IP es la del cliente real gracias a `server.forward-headers-strategy`
 * (Render pone la API detrás de su proxy).
 */
@Component
public class ProteccionAcceso {

    static final int MAX_FALLOS_POR_CORREO = 5;
    static final int MAX_LOGINS_POR_IP = 20;
    static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    static final int MAX_REGISTROS_POR_IP = 5;
    static final Duration VENTANA_REGISTRO = Duration.ofHours(1);
    /**
     * Códigos de dos pasos fallidos por cuenta. Con 5 cada 15 minutos, adivinar
     * uno de entre un millón llevaría siglos.
     */
    static final int MAX_FALLOS_DOS_PASOS = 5;

    private static final int MAX_CLAVES = 10_000;
    private static final String MENSAJE = "Demasiados intentos. Espera unos minutos y vuelve a probar.";

    private final LimitadorDeIntentos fallosPorCorreo =
            new LimitadorDeIntentos(MAX_FALLOS_POR_CORREO, VENTANA_LOGIN, MAX_CLAVES);
    private final LimitadorDeIntentos loginsPorIp =
            new LimitadorDeIntentos(MAX_LOGINS_POR_IP, VENTANA_LOGIN, MAX_CLAVES);
    private final LimitadorDeIntentos registrosPorIp =
            new LimitadorDeIntentos(MAX_REGISTROS_POR_IP, VENTANA_REGISTRO, MAX_CLAVES);
    private final LimitadorDeIntentos fallosDosPasos =
            new LimitadorDeIntentos(MAX_FALLOS_DOS_PASOS, VENTANA_LOGIN, MAX_CLAVES);

    /** Antes de comprobar la contraseña: así un correo frenado no gasta ni un BCrypt. */
    public void antesDeLogin(String ip, String email, Instant ahora) {
        if (fallosPorCorreo.bloqueado(normalizar(email), ahora) || !loginsPorIp.permitir(ip, ahora)) {
            throw new DemasiadosIntentosException(MENSAJE);
        }
    }

    public void loginFallido(String email, Instant ahora) {
        fallosPorCorreo.registrar(normalizar(email), ahora);
    }

    public void loginCorrecto(String email) {
        fallosPorCorreo.olvidar(normalizar(email));
    }

    /**
     * Antes de comprobar un código de dos pasos, en el login o al cambiar la
     * configuración. Se cuenta por cuenta, no por desafío: pedir otro desafío
     * con la contraseña no da intentos nuevos.
     */
    public void antesDeCodigo(String ip, UUID usuarioId, Instant ahora) {
        if (fallosDosPasos.bloqueado(usuarioId.toString(), ahora) || !loginsPorIp.permitir(ip, ahora)) {
            throw new DemasiadosIntentosException(MENSAJE);
        }
    }

    public void codigoFallido(UUID usuarioId, Instant ahora) {
        fallosDosPasos.registrar(usuarioId.toString(), ahora);
    }

    public void codigoCorrecto(UUID usuarioId) {
        fallosDosPasos.olvidar(usuarioId.toString());
    }

    public void antesDeRegistro(String ip, Instant ahora) {
        if (!registrosPorIp.permitir(ip, ahora)) {
            throw new DemasiadosIntentosException(MENSAJE);
        }
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
