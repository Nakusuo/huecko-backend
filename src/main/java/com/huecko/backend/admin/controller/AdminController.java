package com.huecko.backend.admin.controller;

import ch.qos.logback.classic.Level;
import com.huecko.backend.admin.dto.ResumenAdminResponse;
import com.huecko.backend.admin.service.AdminService;
import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.repository.FalloRegistradoRepository;
import com.huecko.backend.mongo.repository.ReporteUsuarioRepository;
import com.huecko.backend.observabilidad.dto.SaludResponse;
import com.huecko.backend.observabilidad.service.BufferLogs;
import com.huecko.backend.observabilidad.service.ConfiguracionVisible;
import com.huecko.backend.observabilidad.service.RegistroFallos;
import com.huecko.backend.observabilidad.service.SaludService;
import com.huecko.backend.reporte.dto.FalloResponse;
import com.huecko.backend.reporte.dto.ReporteRequests;
import com.huecko.backend.reporte.dto.ReporteResponse;
import com.huecko.backend.reporte.service.ReporteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Panel de administración: observar, no gestionar. El rol no se comprueba
 * aquí: `SecurityConfig` ya exige ADMIN en todo `/api/admin/**`.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final SaludService saludService;
    private final RegistroFallos registroFallos;
    private final ReporteService reporteService;
    private final BufferLogs bufferLogs;
    private final ConfiguracionVisible configuracion;
    private final FalloRegistradoRepository fallos;
    private final ReporteUsuarioRepository reportes;

    /** Lo pendiente de mirar, para los contadores de la barra del panel. */
    public record Pendientes(long fallosNuevos, long reportesNuevos) {
    }

    @GetMapping("/resumen")
    public ResponseEntity<ResumenAdminResponse> resumen() {
        return ResponseEntity.ok(adminService.resumen());
    }

    @GetMapping("/pendientes")
    public ResponseEntity<Pendientes> pendientes() {
        return ResponseEntity.ok(new Pendientes(
                fallos.countByEstado(EstadoRevision.NUEVO), reportes.countByEstado(EstadoRevision.NUEVO)));
    }

    @GetMapping("/salud")
    public ResponseEntity<SaludResponse> salud() {
        return ResponseEntity.ok(saludService.salud());
    }

    /* ------------------------------ Fallos ------------------------------ */

    @GetMapping("/fallos")
    public ResponseEntity<List<FalloResponse>> fallos() {
        return ResponseEntity.ok(registroFallos.listar());
    }

    @PatchMapping("/fallos/{id}/estado")
    public ResponseEntity<FalloResponse> estadoFallo(@PathVariable String id,
                                                     @Valid @RequestBody ReporteRequests.CambiarEstado request) {
        return ResponseEntity.ok(registroFallos.cambiarEstado(id, request.estado()));
    }

    /* ----------------------------- Reportes ----------------------------- */

    @GetMapping("/reportes")
    public ResponseEntity<List<ReporteResponse>> reportes() {
        return ResponseEntity.ok(reporteService.listar());
    }

    @PatchMapping("/reportes/{id}/estado")
    public ResponseEntity<ReporteResponse> estadoReporte(@PathVariable String id,
                                                         @Valid @RequestBody ReporteRequests.CambiarEstado request) {
        return ResponseEntity.ok(reporteService.cambiarEstado(id, request.estado()));
    }

    /** La única acción sobre una cuenta: suspender o reactivar la que señala un reporte de conducta. */
    @PatchMapping("/reportes/{id}/suspension")
    public ResponseEntity<ReporteResponse> suspension(@PathVariable String id,
                                                      @Valid @RequestBody ReporteRequests.Suspension request) {
        return ResponseEntity.ok(reporteService.cambiarSuspension(id, request.suspendido()));
    }

    /* ------------------------------ Consola ----------------------------- */

    /** Log en vivo: el panel pide cada pocos segundos lo posterior a `desde`. */
    @GetMapping("/consola/logs")
    public ResponseEntity<BufferLogs.Pagina> logs(@RequestParam(defaultValue = "0") long desde,
                                                  @RequestParam(defaultValue = "INFO") String nivel) {
        return ResponseEntity.ok(bufferLogs.desde(desde, Level.toLevel(nivel, Level.INFO)));
    }

    @GetMapping("/consola/configuracion")
    public ResponseEntity<List<ConfiguracionVisible.Propiedad>> configuracion() {
        return ResponseEntity.ok(configuracion.propiedades());
    }
}
