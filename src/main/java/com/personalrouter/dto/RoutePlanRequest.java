package com.personalrouter.dto;

import com.personalrouter.domain.RouteProfiles;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpo da requisição para calcular (preview) ou calcular e salvar uma rota. */
public record RoutePlanRequest(
        @Schema(description = "Perfil de transporte (opcional, default driving-car)",
                allowableValues = {"driving-car", "driving-hgv"}, example = "driving-car")
        @Pattern(regexp = RouteProfiles.PATTERN, message = RouteProfiles.UNSUPPORTED_MESSAGE)
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
