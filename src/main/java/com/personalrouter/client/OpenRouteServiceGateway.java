package com.personalrouter.client;

import com.personalrouter.client.dto.OrsDirectionsResponse;
import com.personalrouter.dto.Coordinate;
import java.util.List;

/** Fachada para o serviço de rotas do OpenRouteService. */
public interface OpenRouteServiceGateway {

    /** Calcula direções para o perfil e pontos ordenados informados. */
    OrsDirectionsResponse getDirections(String profile, List<Coordinate> orderedPoints);
}
