package com.personalrouter.dto;

/** Trecho da rota entre dois pontos consecutivos. */
public record RouteSegmentDto(
        String fromLabel,
        String toLabel,
        long distanceMeters,
        long durationSeconds
) {}
