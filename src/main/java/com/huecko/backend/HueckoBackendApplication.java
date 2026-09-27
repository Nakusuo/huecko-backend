package com.huecko.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/*
 * Sin UserDetailsServiceAutoConfiguration: la identidad llega en el JWT, así
 * que el usuario en memoria que Spring crea por defecto sobraba, y su
 * contraseña salía impresa en el log de arranque (y en la consola del panel).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HueckoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(HueckoBackendApplication.class, args);
    }
}
