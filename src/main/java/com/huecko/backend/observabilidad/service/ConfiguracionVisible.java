package com.huecko.backend.observabilidad.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * La configuración con la que corre el backend, en solo lectura.
 *
 * Es una lista cerrada, no un volcado del Environment: así una propiedad nueva
 * con un secreto no aparece aquí por accidente. De la clave JWT solo se dice si
 * es la de desarrollo; de las URLs de las bases se quitan usuario y contraseña.
 */
@Service
public class ConfiguracionVisible {

    public record Propiedad(String grupo, String clave, String valor, String descripcion) {
    }

    private static final Pattern CREDENCIALES_EN_URL = Pattern.compile("//[^/@]+@");
    private static final String PREFIJO_CLAVE_DE_DESARROLLO = "cambia-esta-clave";

    private final Environment env;

    public ConfiguracionVisible(Environment env) {
        this.env = env;
    }

    public List<Propiedad> propiedades() {
        List<Propiedad> lista = new ArrayList<>();

        String perfiles = String.join(", ", Arrays.asList(env.getActiveProfiles()));
        lista.add(new Propiedad("Aplicación", "spring.profiles.active", perfiles.isEmpty() ? "(ninguno)" : perfiles,
                "Perfil activo. `dev` trae datos de demo; `prod` es el de despliegue."));
        lista.add(valor("Aplicación", "huecko.version", "Versión desplegada."));
        lista.add(new Propiedad("Aplicación", "HUECKO_ZONA_HORARIA",
                System.getenv().getOrDefault("HUECKO_ZONA_HORARIA", "America/Lima"),
                "Zona en la que se interpretan las fechas de los grupos."));

        lista.add(new Propiedad("Seguridad", "huecko.jwt.secret", describirClave(env.getProperty("huecko.jwt.secret")),
                "Nunca se muestra. Solo se indica si sigue siendo la clave pública de desarrollo."));
        lista.add(valor("Seguridad", "huecko.jwt.expiration-minutes", "Minutos que dura una sesión."));
        lista.add(valor("Seguridad", "huecko.cors.allowed-origins", "Orígenes que pueden llamar a la API desde el navegador."));
        lista.add(valor("Seguridad", "huecko.admin.emails", "Cuentas que pasan a administrador al arrancar."));

        lista.add(new Propiedad("Datos", "spring.datasource.url", sinCredenciales(env.getProperty("spring.datasource.url")),
                "PostgreSQL: cuentas, grupos y planes."));
        lista.add(new Propiedad("Datos", "spring.data.mongodb.uri", sinCredenciales(env.getProperty("spring.data.mongodb.uri")),
                "MongoDB: horarios, retrasos, imprevistos, fallos y reportes."));
        lista.add(valor("Datos", "spring.jpa.hibernate.ddl-auto", "`update` crea columnas solo; `validate` exige migrar a mano."));
        lista.add(valor("Datos", "huecko.seed.enabled", "Si se cargan las cuentas y datos de demo al arrancar."));

        lista.add(valor("Planes", "huecko.planes.cierre-automatico", "Si corre el cierre automático de votaciones."));
        lista.add(valor("Planes", "huecko.planes.intervalo-cierre-ms", "Cada cuántos milisegundos corre."));

        lista.add(valor("Imprevistos", "huecko.imprevistos.evaluador", "Quién decide si una ausencia es crítica (`reglas` o `ia`)."));
        lista.add(valor("Imprevistos", "huecko.imprevistos.plazo-minutos", "Duración de una votación exprés."));
        lista.add(valor("Imprevistos", "huecko.imprevistos.resultado-por-defecto", "Qué se aplica si nadie vota a tiempo."));
        lista.add(valor("Imprevistos", "huecko.imprevistos.cierre-automatico", "Si corre el cierre automático de votaciones exprés."));
        lista.add(valor("Imprevistos", "huecko.imprevistos.intervalo-cierre-ms", "Cada cuántos milisegundos corre."));
        lista.add(valor("Imprevistos", "huecko.imprevistos.dias-purga", "Días que se conserva una votación cerrada."));

        return lista;
    }

    private Propiedad valor(String grupo, String clave, String descripcion) {
        String valor = env.getProperty(clave);
        return new Propiedad(grupo, clave, valor == null || valor.isBlank() ? "(vacío)" : valor, descripcion);
    }

    static String sinCredenciales(String url) {
        if (url == null || url.isBlank()) {
            return "(vacío)";
        }
        return CREDENCIALES_EN_URL.matcher(url).replaceFirst("//***@");
    }

    static String describirClave(String clave) {
        if (clave == null || clave.isBlank()) {
            return "(sin definir)";
        }
        return clave.startsWith(PREFIJO_CLAVE_DE_DESARROLLO)
                ? "Clave de desarrollo: no apta para producción"
                : "Propia (oculta)";
    }
}
