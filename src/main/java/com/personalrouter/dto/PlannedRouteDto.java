package com.personalrouter.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representação de uma rota planejada salva. */
public record PlannedRouteDto(
        UUID id,
        String name,
        String profile,
        RoutePoint origin,
        RoutePoint destination,
        List<RoutePoint> stops,
        long distanceMeters,
        long durationSeconds,
        String geometry,
        List<TollPlazaDto> tollPlazas,
        Instant createdAt
) {
    public PlannedRouteDto withTollPlazas(List<TollPlazaDto> tollPlazas) {
        return new PlannedRouteDto(id, name, profile, origin, destination, stops,
                distanceMeters, durationSeconds, geometry, tollPlazas, createdAt);
    }
}
