package com.personalrouter.dto;

import java.util.List;

/** Resultado do cálculo de rota (preview), traduzido da resposta do OpenRouteService. */
public record RouteResultDto(
        String profile,
        long distanceMeters,
        long durationSeconds,
        String geometry,
        List<RouteSegmentDto> segments
) {}
