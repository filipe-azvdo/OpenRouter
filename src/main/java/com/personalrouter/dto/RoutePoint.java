package com.personalrouter.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** Ponto geográfico com rótulo opcional, usado em origem, destino e paradas. */
public record RoutePoint(
        @DecimalMin(value = "-90.0", message = "lat deve estar entre -90 e 90")
        @DecimalMax(value = "90.0", message = "lat deve estar entre -90 e 90")
        double lat,
        @DecimalMin(value = "-180.0", message = "lon deve estar entre -180 e 180")
        @DecimalMax(value = "180.0", message = "lon deve estar entre -180 e 180")
        double lon,
        String label
) {}
