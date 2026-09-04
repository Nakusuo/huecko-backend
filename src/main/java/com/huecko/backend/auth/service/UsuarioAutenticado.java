package com.huecko.backend.auth.service;

import java.util.UUID;

/**
 * Identidad que viaja dentro del JWT y que queda como `principal` de la
 * petición. Los controladores la reciben con @AuthenticationPrincipal, en vez
 * de fiarse de un id que llegue del cliente.
 */
public record UsuarioAutenticado(UUID id, String email, String nombre) {
}
