package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.FotoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FotoPerfilRepository extends JpaRepository<FotoPerfil, UUID> {
}
