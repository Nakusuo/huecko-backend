package com.huecko.backend.auth.service;

import com.huecko.backend.postgres.entity.Usuario.RolSistema;

import java.util.UUID;

/**
 * Identidad que viaja dentro del JWT y que queda como `principal` de la
 * petición. Los controladores la reciben con @AuthenticationPrincipal, en vez
 * de fiarse de un id que llegue del cliente.
 */
public record UsuarioAutenticado(UUID id, String email, String nombre, RolSistema rolSistema) {

    /** Sin rol explícito es una cuenta normal. */
    public UsuarioAutenticado(UUID id, String email, String nombre) {
        this(id, email, nombre, RolSistema.USUARIO);
    }

    public boolean esAdmin() {
        return rolSistema == RolSistema.ADMIN;
    }
}
