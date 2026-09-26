package com.huecko.backend.admin.controller;

import com.huecko.backend.admin.dto.ResumenAdminResponse;
import com.huecko.backend.admin.dto.SuspensionRequest;
import com.huecko.backend.admin.dto.UsuarioAdminResponse;
import com.huecko.backend.admin.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Panel de administración. El rol no se comprueba aquí: `SecurityConfig` ya
 * exige ADMIN en todo `/api/admin/**` antes de llegar.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/resumen")
    public ResponseEntity<ResumenAdminResponse> resumen() {
        return ResponseEntity.ok(adminService.resumen());
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<UsuarioAdminResponse>> usuarios() {
        return ResponseEntity.ok(adminService.usuarios());
    }

    @PatchMapping("/usuarios/{usuarioId}/suspension")
    public ResponseEntity<UsuarioAdminResponse> suspension(@PathVariable UUID usuarioId,
                                                           @Valid @RequestBody SuspensionRequest request) {
        return ResponseEntity.ok(adminService.cambiarSuspension(usuarioId, request.suspendido()));
    }
}
