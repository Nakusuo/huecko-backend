-- «Llegué»: quién llegó a un plan confirmado y a qué hora. Con esto los logros
-- dejan de suponer la asistencia y el álbum puede decir quiénes estuvieron.
CREATE TABLE llegadas (
    plan_id    UUID                        NOT NULL,
    usuario_id UUID                        NOT NULL,
    llego_en   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_llegadas PRIMARY KEY (plan_id, usuario_id),
    CONSTRAINT fk_llegadas_plan FOREIGN KEY (plan_id) REFERENCES planes ON DELETE CASCADE,
    CONSTRAINT fk_llegadas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios ON DELETE CASCADE
);

-- Recordatorios: cuándo se avisó de que el plan empieza en una hora y de que
-- la votación está por cerrar. NULL = aún no; así cada aviso sale una sola
-- vez aunque el barrido pase muchas veces. Reagendar los vuelve a NULL.
ALTER TABLE planes ADD COLUMN recordatorio_enviado_en TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE planes ADD COLUMN aviso_cierre_enviado_en TIMESTAMP(6) WITH TIME ZONE;
