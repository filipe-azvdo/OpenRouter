package com.personalrouter.dto;

import java.math.BigDecimal;

public record TollPlazaDto(
        String nome,
        String concessionaria,
        String rodovia,
        String uf,
        BigDecimal km,
        String sentido,
        double latitude,
        double longitude
) {}
