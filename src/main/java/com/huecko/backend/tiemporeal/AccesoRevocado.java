package com.huecko.backend.tiemporeal;

import java.util.UUID;

/**
 * Alguien ha perdido acceso a datos que le llegaban por tiempo real: le han
 * sacado de un grupo (o se ha ido) o han suspendido su cuenta. Sus sesiones
 * WebSocket se cierran (SesionesTiempoReal); al reconectar, el cliente vuelve
 * a suscribirse y SeguridadStompInterceptor ya no le deja entrar donde no debe.
 */
public record AccesoRevocado(UUID usuarioId) {
}
