package com.huecko.backend.postgres.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Guarda una lista corta de etiquetas en una sola columna, separadas por comas.
 * Vale porque ninguna etiqueta puede llevar comas (lo valida
 * PerfilSocialRequest). Una lista vacía se guarda como NULL.
 */
@Converter
public class ListaDeEtiquetas implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> etiquetas) {
        return etiquetas == null || etiquetas.isEmpty() ? null : String.join(",", etiquetas);
    }

    @Override
    public List<String> convertToEntityAttribute(String columna) {
        if (columna == null || columna.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(columna.split(",")).map(String::strip).filter(e -> !e.isEmpty()).toList());
    }
}
