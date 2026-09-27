package com.huecko.backend.observabilidad.service;

import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.FalloRegistrado;
import com.huecko.backend.mongo.document.FalloRegistrado.Origen;
import com.huecko.backend.mongo.repository.FalloRegistradoRepository;
import com.huecko.backend.reporte.dto.FalloResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bandeja de fallos automáticos: excepciones no controladas del backend,
 * errores de las tareas programadas y errores de JavaScript del frontend.
 *
 * Registrar nunca lanza. Si Mongo está caído, el fallo se pierde (queda en el
 * log) en vez de convertir un error en dos.
 */
@Service
public class RegistroFallos {

    private static final Logger log = LoggerFactory.getLogger(RegistroFallos.class);

    static final int MAX_MENSAJE = 500;
    static final int MAX_TRAZA = 4000;
    static final int MAX_LINEAS_TRAZA = 15;
    private static final String PAQUETE_PROPIO = "com.huecko";

    /** UUIDs, ObjectIds de Mongo y números en una ruta: `/api/planes/{id}` agrupa todos los planes. */
    private static final Pattern IDENTIFICADOR = Pattern.compile(
            "(?i)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{24}|\\d+)(?=/|$)");

    private final MongoTemplate mongoTemplate;
    private final FalloRegistradoRepository repositorio;

    public RegistroFallos(MongoTemplate mongoTemplate, FalloRegistradoRepository repositorio) {
        this.mongoTemplate = mongoTemplate;
        this.repositorio = repositorio;
    }

    /** Excepción no controlada al atender una petición. */
    public void registrarServidor(Throwable ex, String metodo, String ruta) {
        String ubicacion = metodo + " " + normalizarRuta(ruta);
        anotar(Origen.SERVIDOR, ex.getClass().getName(), mensajeDe(ex), ubicacion, traza(ex), primeraLineaPropia(ex), null);
    }

    /** Excepción al procesar un elemento dentro de una tarea programada. */
    public void registrarTarea(Tarea tarea, Throwable ex) {
        anotar(Origen.TAREA, ex.getClass().getName(), mensajeDe(ex), tarea.nombre(), traza(ex), primeraLineaPropia(ex), null);
    }

    /** Error de JavaScript enviado por el frontend. La traza la manda el cliente: se recorta. */
    public void registrarCliente(String tipo, String mensaje, String traza, String ruta, String navegador) {
        String trazaRecortada = recortarTraza(traza);
        String primeraLinea = trazaRecortada.lines().skip(1).findFirst().orElse("");
        anotar(Origen.CLIENTE, recortar(tipo, 120), recortar(mensaje, MAX_MENSAJE), normalizarRuta(ruta),
                trazaRecortada, primeraLinea, recortar(navegador, 300));
    }

    public List<FalloResponse> listar() {
        return repositorio.findAll(Sort.by(Sort.Direction.DESC, "ultimaVez")).stream()
                .map(FalloResponse::from)
                .toList();
    }

    public FalloResponse cambiarEstado(String id, EstadoRevision estado) {
        FalloRegistrado fallo = repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Ese fallo no existe."));
        fallo.setEstado(estado);
        if (estado == EstadoRevision.RESUELTO) {
            fallo.setReabierto(false);
        }
        return FalloResponse.from(repositorio.save(fallo));
    }

    private void anotar(Origen origen, String tipo, String mensaje, String ubicacion, String traza,
                        String primeraLinea, String navegador) {
        try {
            String huella = huella(origen, tipo, ubicacion, primeraLinea);
            Instant ahora = Instant.now();
            Query porHuella = Query.query(Criteria.where("huella").is(huella));
            Update update = new Update()
                    .inc("ocurrencias", 1)
                    .set("ultimaVez", ahora)
                    .set("mensaje", mensaje)
                    .set("traza", traza)
                    .set("navegador", navegador)
                    .setOnInsert("origen", origen)
                    .setOnInsert("tipo", tipo)
                    .setOnInsert("ubicacion", ubicacion)
                    .setOnInsert("primeraVez", ahora)
                    .setOnInsert("estado", EstadoRevision.NUEVO)
                    .setOnInsert("reabierto", false);
            try {
                mongoTemplate.upsert(porHuella, update, FalloRegistrado.class);
            } catch (DuplicateKeyException carrera) {
                // Dos peticiones insertaron la misma huella a la vez: la segunda ya encuentra la fila.
                mongoTemplate.upsert(porHuella, update, FalloRegistrado.class);
            }
            // Si ya se había dado por resuelto, que vuelva a la bandeja.
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("huella").is(huella).and("estado").is(EstadoRevision.RESUELTO)),
                    new Update().set("estado", EstadoRevision.NUEVO).set("reabierto", true),
                    FalloRegistrado.class);
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar un fallo {} ({}): {}", origen, tipo, ex.getClass().getSimpleName());
        }
    }

    static String huella(Origen origen, String tipo, String ubicacion, String primeraLinea) {
        String base = origen + "|" + tipo + "|" + ubicacion + "|" + primeraLinea;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException(imposible);
        }
    }

    static String normalizarRuta(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return "(desconocida)";
        }
        String sinQuery = ruta.split("[?#]", 2)[0];
        return recortar(IDENTIFICADOR.matcher(sinQuery).replaceAll("/{id}"), 300);
    }

    /** El mensaje de la causa raíz suele ser el que explica algo; el envoltorio no. */
    private static String mensajeDe(Throwable ex) {
        Throwable raiz = ex;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String mensaje = raiz == ex ? ex.getMessage()
                : ex.getMessage() + " ← " + raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
        return recortar(mensaje == null ? "(sin mensaje)" : mensaje, MAX_MENSAJE);
    }

    private static String traza(Throwable ex) {
        String lineas = Arrays.stream(ex.getStackTrace())
                .limit(MAX_LINEAS_TRAZA)
                .map(e -> "    at " + e)
                .collect(Collectors.joining("\n"));
        return recortar(ex + "\n" + lineas, MAX_TRAZA);
    }

    /** La primera línea de nuestro código: agrupa el mismo fallo aunque cambie la librería de debajo. */
    private static String primeraLineaPropia(Throwable ex) {
        StackTraceElement[] pila = ex.getStackTrace();
        return Arrays.stream(pila)
                .filter(e -> e.getClassName().startsWith(PAQUETE_PROPIO))
                .findFirst()
                .or(() -> Arrays.stream(pila).findFirst())
                .map(e -> e.getClassName() + "." + e.getMethodName())
                .orElse("");
    }

    private static String recortarTraza(String traza) {
        if (traza == null) {
            return "";
        }
        return recortar(traza.lines().limit(MAX_LINEAS_TRAZA + 1).collect(Collectors.joining("\n")), MAX_TRAZA);
    }

    static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
