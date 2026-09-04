package com.huecko.backend.seed;

import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Carga los datos de demo en Postgres y en Mongo al arrancar.
 *
 * Vive aquí y no en un .sql / .js de Docker por dos motivos que ninguno de esos
 * dos archivos puede resolver por su cuenta:
 *
 *   1. La contraseña del usuario de demo tiene que quedar cifrada con el mismo
 *      PasswordEncoder que luego valida el login. Un hash escrito a mano en un
 *      .sql se desincroniza en cuanto se cambia el encoder.
 *   2. `bloques_horario` (Mongo) referencia el UUID de `usuarios` (Postgres).
 *      Como el UUID lo genera Hibernate al insertar, solo se conoce en tiempo
 *      de ejecución: aquí se inserta el usuario, se lee su id y con él se
 *      escriben los bloques.
 *
 * Es idempotente: si el usuario de demo ya existe, no vuelve a insertar nada.
 * Se desactiva con `huecko.seed.enabled=false` (así lo hace el perfil `prod`).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "huecko.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Las mismas credenciales que usa el modo demo del frontend. */
    private static final String EMAIL_DEMO = "alex.rodriguez@huecko.com";
    private static final String PASSWORD_DEMO = "demo1234";

    private static final String EMAIL_COMPANERA = "diana.torres@huecko.com";
    private static final String NOMBRE_GRUPO = "Proyecto Integrador 2026-II";

    private final UsuarioRepository usuarioRepository;
    private final GrupoRepository grupoRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final BloqueHorarioRepository bloqueHorarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        Usuario alex = asegurarUsuario("Alex Rodríguez", EMAIL_DEMO, PASSWORD_DEMO);
        Usuario diana = asegurarUsuario("Diana Torres", EMAIL_COMPANERA, PASSWORD_DEMO);

        asegurarGrupo(alex, diana);
        asegurarBloques(alex);
        asegurarBloques(diana);

        log.info("Datos de demo listos. Entra con {} / {}", EMAIL_DEMO, PASSWORD_DEMO);
    }

    private Usuario asegurarUsuario(String nombre, String email, String password) {
        return usuarioRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    log.info("Creando usuario de demo: {}", email);
                    return usuarioRepository.save(Usuario.builder()
                            .nombre(nombre)
                            .email(email)
                            .passwordHash(passwordEncoder.encode(password))
                            .build());
                });
    }

    /** Un grupo con Alex de organizador y Diana de miembro, para que el Módulo 2 tenga con qué trabajar. */
    private void asegurarGrupo(Usuario organizador, Usuario miembro) {
        Grupo grupo = grupoRepository.findByNombreIgnoreCase(NOMBRE_GRUPO)
                .orElseGet(() -> grupoRepository.save(Grupo.builder()
                        .nombre(NOMBRE_GRUPO)
                        .creadoPor(organizador)
                        .build()));

        asegurarMembresia(grupo, organizador, MiembroGrupo.Rol.ORGANIZADOR, true);
        asegurarMembresia(grupo, miembro, MiembroGrupo.Rol.MIEMBRO, false);
    }

    private void asegurarMembresia(Grupo grupo, Usuario usuario, MiembroGrupo.Rol rol, boolean imprescindible) {
        if (miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(grupo.getId(), usuario.getId())) {
            return;
        }
        miembroGrupoRepository.save(MiembroGrupo.builder()
                .grupo(grupo)
                .usuario(usuario)
                .rol(rol)
                .esImprescindible(imprescindible)
                .build());
    }

    /**
     * Horario de ejemplo. Solo se escribe si el usuario no tiene ningún bloque,
     * para no duplicar ni pisar lo que se haya creado probando la app.
     */
    private void asegurarBloques(Usuario usuario) {
        String usuarioId = usuario.getId().toString();
        if (!bloqueHorarioRepository.findByUsuarioId(usuarioId).isEmpty()) {
            return;
        }

        boolean esAlex = EMAIL_DEMO.equalsIgnoreCase(usuario.getEmail());

        List<BloqueHorario> bloques = esAlex
                ? List.of(
                        recurrente(usuarioId, 1, "08:00", "11:00", "Cálculo II", "Clase", "#7C3AED"),
                        recurrente(usuarioId, 2, "14:00", "18:00", "Prácticas en la empresa", "Trabajo", "#0EA5E9"),
                        recurrente(usuarioId, 3, "08:00", "11:00", "Cálculo II", "Clase", "#7C3AED"),
                        recurrente(usuarioId, 5, "19:00", "21:00", "Gimnasio", "Personal", "#22C55E"),
                        // RF-03: así se ve un bloque recién importado por OCR, pendiente de revisar.
                        borradorOcr(usuarioId, 4, "16:00", "18:30", "Laboratorio de Redes"))
                : List.of(
                        recurrente(usuarioId, 1, "09:00", "13:00", "Base de Datos", "Clase", "#F59E0B"),
                        recurrente(usuarioId, 3, "15:00", "19:00", "Trabajo part-time", "Trabajo", "#0EA5E9"),
                        puntual(usuarioId, LocalDate.now().plusDays(5), "10:00", "12:00",
                                "Sustentación parcial", "Clase", "#EF4444"));

        bloqueHorarioRepository.saveAll(bloques);
        log.info("Sembrados {} bloques de horario para {}", bloques.size(), usuario.getEmail());
    }

    private BloqueHorario recurrente(String usuarioId, int diaSemana, String inicio, String fin,
                                     String etiqueta, String categoria, String color) {
        return base(usuarioId, etiqueta, categoria, color, inicio, fin)
                .tipo(BloqueHorario.Tipo.RECURRENTE)
                .diaSemana(diaSemana)
                .fuente(BloqueHorario.Fuente.MANUAL)
                .estado(BloqueHorario.Estado.CONFIRMADO)
                .build();
    }

    private BloqueHorario puntual(String usuarioId, LocalDate fecha, String inicio, String fin,
                                  String etiqueta, String categoria, String color) {
        return base(usuarioId, etiqueta, categoria, color, inicio, fin)
                .tipo(BloqueHorario.Tipo.PUNTUAL)
                .fecha(fecha)
                .fuente(BloqueHorario.Fuente.MANUAL)
                .estado(BloqueHorario.Estado.CONFIRMADO)
                .build();
    }

    private BloqueHorario borradorOcr(String usuarioId, int diaSemana, String inicio, String fin, String etiqueta) {
        return base(usuarioId, etiqueta, "Clase", "#94A3B8", inicio, fin)
                .tipo(BloqueHorario.Tipo.RECURRENTE)
                .diaSemana(diaSemana)
                .fuente(BloqueHorario.Fuente.OCR)
                .estado(BloqueHorario.Estado.BORRADOR)
                .build();
    }

    private BloqueHorario.BloqueHorarioBuilder base(String usuarioId, String etiqueta, String categoria,
                                                    String color, String inicio, String fin) {
        Instant ahora = Instant.now();
        return BloqueHorario.builder()
                .usuarioId(usuarioId)
                .horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin))
                .etiqueta(etiqueta)
                .categoria(categoria)
                .color(color)
                .creadoEn(ahora)
                .actualizadoEn(ahora);
    }
}
