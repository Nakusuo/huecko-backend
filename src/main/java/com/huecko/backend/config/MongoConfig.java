package com.huecko.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Restringe los repositorios Mongo al paquete mongo.repository.
 * Junto con PostgresConfig, esto es lo que le permite a la misma app
 * hablar con las dos bases sin que Spring se confunda sobre cuál usar
 * para cada interfaz Repository.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.huecko.backend.mongo.repository")
public class MongoConfig {
}
