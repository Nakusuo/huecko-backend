package com.huecko.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Restringe los repositorios JPA (Postgres) al paquete postgres.repository.
 * Sin esto, Spring intenta tratar TODOS los repositorios (incluidos los de Mongo)
 * como si fueran JPA, y el arranque falla.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.huecko.backend.postgres.repository")
public class PostgresConfig {
}
