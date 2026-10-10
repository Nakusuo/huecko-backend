package com.huecko.backend.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del cliente real con la API detrás del proxy de Render.
 *
 * Render no documenta una cabecera fiable. Lo que se sabe:
 *  - delante va Cloudflare, que pone `CF-Connecting-IP` y pisa la que mande el
 *    cliente, así que no se puede falsear;
 *  - Render pone la IP real al principio de `X-Forwarded-For`, pero no limpia
 *    lo que traiga el cliente, así que esa sí se puede falsear.
 * Por eso solo sirve para límites "de cortesía" (ProteccionAcceso). La
 * protección de cada cuenta va por correo, que no depende de esto.
 */
public final class IpCliente {

    private IpCliente() {
    }

    public static String de(HttpServletRequest request) {
        String cloudflare = request.getHeader("CF-Connecting-IP");
        if (cloudflare != null && !cloudflare.isBlank()) {
            return cloudflare.trim();
        }
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
