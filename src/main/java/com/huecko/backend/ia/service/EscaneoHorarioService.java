package com.huecko.backend.ia.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.ia.dto.EscaneoHorarioResponse;
import com.huecko.backend.mongo.document.LlamadaIA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Escaneo del horario con IA (RF-03, como en la app móvil).
 *
 * La foto no se guarda en ningún sitio: se comprueba, se manda al servicio de
 * IA y se devuelve lo leído para que la persona lo revise. Los bloques que
 * acepte se crean después por {@code BloqueHorarioController}, con sus
 * validaciones de siempre.
 *
 * Si la IA no responde, 503: el frontend cae a su lector local.
 */
@Service
public class EscaneoHorarioService {

    private static final Logger log = LoggerFactory.getLogger(EscaneoHorarioService.class);

    /** El mismo tope que spring.servlet.multipart.max-file-size; aquí por si aquel cambia. */
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ClienteIA clienteIA;
    private final AccesoAsistente acceso;
    private final RegistroUsoIA registro;

    public EscaneoHorarioService(ClienteIA clienteIA, AccesoAsistente acceso, RegistroUsoIA registro) {
        this.clienteIA = clienteIA;
        this.acceso = acceso;
        this.registro = registro;
    }

    public EscaneoHorarioResponse escanear(UUID usuarioId, MultipartFile archivo) {
        byte[] imagen = leer(archivo);
        // El tipo sale de los bytes y no del navegador: un PDF renombrado a
        // .png llegaba a Gemini como «imagen» y gastaba cuota para nada.
        String tipo = tipoReal(imagen)
                .orElseThrow(() -> new BusinessException("La imagen debe ser JPG, PNG o WEBP."));

        // Después de validar: una foto inválida no gasta cupo.
        acceso.exigir(usuarioId);
        ClienteIA.RespuestaEscaneo leido;
        try {
            leido = registro.medir(LlamadaIA.Funcion.ESCANEO_HORARIO, () -> clienteIA.escanearHorario(tipo, imagen));
        } catch (IaNoDisponibleException ex) {
            log.warn("Escaneo de horario sin IA: {}", ex.getMessage());
            throw new ServicioNoDisponibleException(AccesoAsistente.NO_DISPONIBLE);
        }
        return new EscaneoHorarioResponse(aBloques(leido.bloques()));
    }

    private static byte[] leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Adjunta la foto de tu horario.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new BusinessException("La imagen supera los 5 MB.");
        }
        try {
            return archivo.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer la imagen. Inténtalo de nuevo.");
        }
    }

    /** JPG, PNG o WEBP por sus primeros bytes; cualquier otra cosa, vacío. */
    static Optional<String> tipoReal(byte[] b) {
        if (empieza(b, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (empieza(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (b.length >= 12 && empieza(b, 'R', 'I', 'F', 'F')
                && Arrays.equals(Arrays.copyOfRange(b, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean empieza(byte[] datos, int... firma) {
        if (datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((datos[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    /** El servicio ya filtra; esto solo evita que un campo nulo reviente la respuesta. */
    private static List<EscaneoHorarioResponse.Bloque> aBloques(List<ClienteIA.BloqueLeido> bloques) {
        if (bloques == null) {
            return List.of();
        }
        return bloques.stream()
                .filter(Objects::nonNull)
                .filter(b -> b.diaSemana() != null && b.horaInicio() != null
                        && b.horaFin() != null && b.titulo() != null)
                .map(b -> new EscaneoHorarioResponse.Bloque(b.diaSemana(), b.horaInicio(), b.horaFin(),
                        b.titulo(), Boolean.TRUE.equals(b.dudoso())))
                .toList();
    }
}
