package com.personalrouter.service;

import com.personalrouter.dto.PlannedRouteDto;
import com.personalrouter.dto.RoutePlanRequest;
import com.personalrouter.dto.RouteResultDto;

import java.util.List;
import java.util.UUID;

/** Regra de negócio de planejamento de rotas: cálculo (preview) e CRUD de rotas salvas. */
public interface RouteService {

    /** Calcula a rota sem persistir (preview). */
    RouteResultDto planRoute(RoutePlanRequest request);

    /** Calcula a rota e a persiste, retornando a rota salva. */
    PlannedRouteDto createRoute(RoutePlanRequest request);

    /** Lista as rotas salvas, mais recentes primeiro. */
    List<PlannedRouteDto> listRoutes();

    /** Detalha uma rota salva pelo identificador. */
    PlannedRouteDto getRoute(UUID id);

    /** Remove uma rota salva pelo identificador. */
    void deleteRoute(UUID id);
}
