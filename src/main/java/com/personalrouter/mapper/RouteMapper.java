package com.personalrouter.mapper;

import com.personalrouter.client.dto.OrsRoute;
import com.personalrouter.client.dto.OrsSegment;
import com.personalrouter.dto.PlannedRouteDto;
import com.personalrouter.dto.RoutePlanRequest;
import com.personalrouter.dto.RoutePoint;
import com.personalrouter.dto.RouteResultDto;
import com.personalrouter.dto.RouteSegmentDto;
import com.personalrouter.dto.TollPlazaDto;
import com.personalrouter.model.PlannedRoute;
import com.personalrouter.model.PlannedRouteStop;
import com.personalrouter.model.PlannedRouteToll;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Traduções entre a resposta do OpenRouteService, os DTOs de domínio e as entidades. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RouteMapper {

    /**
     * Traduz a rota do ORS para o DTO de domínio, casando cada trecho do ORS com o par de pontos
     * consecutivos (segmento {@code i} = trecho do ponto {@code i} ao ponto {@code i + 1}).
     */
    default RouteResultDto toRouteResult(OrsRoute orsRoute, String profile,
                                         List<RoutePoint> orderedPoints,
                                         List<TollPlazaDto> tollPlazas) {
        List<OrsSegment> orsSegments =
                orsRoute.segments() == null ? List.of() : orsRoute.segments();
        List<RouteSegmentDto> segments = new ArrayList<>();
        for (int i = 0; i < orsSegments.size(); i++) {
            RoutePoint from = orderedPoints.get(i);
            RoutePoint to = orderedPoints.get(i + 1);
            OrsSegment segment = orsSegments.get(i);
            segments.add(new RouteSegmentDto(
                    from.label(),
                    to.label(),
                    Math.round(segment.distance()),
                    Math.round(segment.duration())
            ));
        }
        return new RouteResultDto(
                profile,
                Math.round(orsRoute.summary().distance()),
                Math.round(orsRoute.summary().duration()),
                orsRoute.geometry(),
                segments,
                tollPlazas
        );
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "profile", source = "result.profile")
    @Mapping(target = "originLat", source = "request.origin.lat")
    @Mapping(target = "originLon", source = "request.origin.lon")
    @Mapping(target = "originLabel", source = "request.origin.label")
    @Mapping(target = "destinationLat", source = "request.destination.lat")
    @Mapping(target = "destinationLon", source = "request.destination.lon")
    @Mapping(target = "destinationLabel", source = "request.destination.label")
    @Mapping(target = "distanceMeters", source = "result.distanceMeters")
    @Mapping(target = "durationSeconds", source = "result.durationSeconds")
    @Mapping(target = "geometry", source = "result.geometry")
    @Mapping(target = "stops", source = "request.stops")
    @Mapping(target = "tollPlazas", source = "result.tollPlazas")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PlannedRoute toEntity(RoutePlanRequest request, RouteResultDto result);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plannedRoute", ignore = true)
    @Mapping(target = "stopOrder", ignore = true)
    PlannedRouteStop toStopEntity(RoutePoint point);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plannedRoute", ignore = true)
    @Mapping(target = "matchOrder", ignore = true)
    @Mapping(target = "kmM", source = "km")
    PlannedRouteToll toTollEntity(TollPlazaDto dto);

    /** Atribui a ordem e a referência inversa às paradas e praças após a construção da entidade. */
    @AfterMapping
    default void linkChildren(@MappingTarget PlannedRoute route) {
        List<PlannedRouteStop> stops = route.getStops();
        if (stops != null) {
            for (int i = 0; i < stops.size(); i++) {
                PlannedRouteStop stop = stops.get(i);
                stop.setPlannedRoute(route);
                stop.setStopOrder(i);
            }
        }
        List<PlannedRouteToll> tollPlazas = route.getTollPlazas();
        if (tollPlazas != null) {
            for (int i = 0; i < tollPlazas.size(); i++) {
                PlannedRouteToll toll = tollPlazas.get(i);
                toll.setPlannedRoute(route);
                toll.setMatchOrder(i);
            }
        }
    }

    @Mapping(target = "origin.lat", source = "originLat")
    @Mapping(target = "origin.lon", source = "originLon")
    @Mapping(target = "origin.label", source = "originLabel")
    @Mapping(target = "destination.lat", source = "destinationLat")
    @Mapping(target = "destination.lon", source = "destinationLon")
    @Mapping(target = "destination.label", source = "destinationLabel")
    PlannedRouteDto toDto(PlannedRoute entity);

    @Mapping(target = "km", source = "kmM")
    TollPlazaDto toTollDto(PlannedRouteToll entity);

    RoutePoint toPoint(PlannedRouteStop stop);
}
