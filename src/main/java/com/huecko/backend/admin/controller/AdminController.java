package com.huecko.backend.admin.controller;

import com.huecko.backend.admin.dto.ResumenAdminResponse;
import com.huecko.backend.admin.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
