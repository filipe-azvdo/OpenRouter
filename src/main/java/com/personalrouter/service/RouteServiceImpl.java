package com.personalrouter.service;

import com.personalrouter.client.OpenRouteServiceGateway;
import com.personalrouter.client.dto.OrsDirectionsResponse;
import com.personalrouter.domain.RouteProfiles;
import com.personalrouter.dto.Coordinate;
import com.personalrouter.dto.PlannedRouteDto;
import com.personalrouter.dto.RoutePlanRequest;
import com.personalrouter.dto.RoutePoint;
import com.personalrouter.dto.RouteResultDto;
import com.personalrouter.dto.TollPlazaDto;
import com.personalrouter.exception.RouteCalculationException;
import com.personalrouter.exception.RouteNotFoundException;
import com.personalrouter.mapper.RouteMapper;
import com.personalrouter.model.PlannedRoute;
import com.personalrouter.repository.PlannedRouteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final OpenRouteServiceGateway gateway;
    private final RouteMapper mapper;
    private final PlannedRouteRepository repository;
    private final TollMatchingService tollMatchingService;

    @Override
    public RouteResultDto planRoute(RoutePlanRequest request) {
        return calculate(request);
    }

    @Override
    @Transactional
    public PlannedRouteDto createRoute(RoutePlanRequest request) {
        RouteResultDto result = calculate(request);
        PlannedRoute entity = mapper.toEntity(request, result);
        PlannedRoute saved = repository.save(entity);
        log.info("Rota planejada salva com id {}", saved.getId());
        return mapper.toDto(saved).withTollPlazas(result.tollPlazas());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlannedRouteDto> listRoutes() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toDtoWithTolls)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlannedRouteDto getRoute(UUID id) {
        PlannedRoute route = repository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Rota não encontrada: " + id));
        return toDtoWithTolls(route);
    }

    @Override
    @Transactional
    public void deleteRoute(UUID id) {
        if (!repository.existsById(id)) {
            throw new RouteNotFoundException("Rota não encontrada: " + id);
        }
        repository.deleteById(id);
    }

    private RouteResultDto calculate(RoutePlanRequest request) {
        String profile = resolveProfile(request.profile());
        List<RoutePoint> orderedPoints = buildOrderedPoints(request);
        List<Coordinate> coordinates = orderedPoints.stream()
                .map(point -> new Coordinate(point.lat(), point.lon()))
                .toList();

        OrsDirectionsResponse response = gateway.getDirections(profile, coordinates);

        if (response.routes() == null || response.routes().isEmpty()) {
            throw new RouteCalculationException("Nenhuma rota encontrada para os pontos informados");
        }

        String geometry = response.routes().get(0).geometry();
        List<TollPlazaDto> tollPlazas = tollMatchingService.findTollPlazasAlongRoute(geometry);
        return mapper.toRouteResult(response.routes().get(0), profile, orderedPoints, tollPlazas);
    }

    private PlannedRouteDto toDtoWithTolls(PlannedRoute route) {
        List<TollPlazaDto> tollPlazas = tollMatchingService.findTollPlazasAlongRoute(route.getGeometry());
        return mapper.toDto(route).withTollPlazas(tollPlazas);
    }

    private List<RoutePoint> buildOrderedPoints(RoutePlanRequest request) {
        List<RoutePoint> points = new ArrayList<>();
        points.add(request.origin());
        if (request.stops() != null) {
            points.addAll(request.stops());
        }
        points.add(request.destination());
        return points;
    }

    private String resolveProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return RouteProfiles.DEFAULT;
        }
        return profile;
    }
}
