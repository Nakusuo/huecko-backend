package com.huecko.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita @Scheduled. Vive en su propia clase y no sobre la aplicacion para
 * que las pruebas que carguen solo una parte del contexto no arrastren el
 * barrido de cierre de votaciones (CierreVotacionScheduler).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
