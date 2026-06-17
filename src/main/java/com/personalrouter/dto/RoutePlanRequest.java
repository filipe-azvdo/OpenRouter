package com.personalrouter.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Corpo da requisição para calcular (preview) ou calcular e salvar uma rota. */
public record RoutePlanRequest(
        String profile,
        @NotNull(message = "origin é obrigatório")
        @Valid
        RoutePoint origin,
        @NotNull(message = "destination é obrigatório")
        @Valid
        RoutePoint destination,
        @Size(max = 10, message = "máximo de 10 paradas")
        @Valid
        List<RoutePoint> stops,
        String name
) {}
