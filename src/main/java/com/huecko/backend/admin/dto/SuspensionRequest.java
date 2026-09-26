package com.huecko.backend.admin.dto;

import jakarta.validation.constraints.NotNull;

/** `PATCH /api/admin/usuarios/{id}/suspension`: `true` suspende, `false` reactiva. */
public record SuspensionRequest(@NotNull Boolean suspendido) {
}
