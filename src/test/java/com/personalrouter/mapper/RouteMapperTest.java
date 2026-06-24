package com.personalrouter.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalrouter.client.dto.OrsRoute;
import com.personalrouter.client.dto.OrsRouteSummary;
import com.personalrouter.client.dto.OrsSegment;
import com.personalrouter.dto.PlannedRouteDto;
import com.personalrouter.dto.RoutePlanRequest;
import com.personalrouter.dto.RoutePoint;
import com.personalrouter.dto.RouteResultDto;
import com.personalrouter.dto.TollPlazaDto;
import com.personalrouter.model.PlannedRoute;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class RouteMapperTest {

    private final RouteMapper mapper = Mappers.getMapper(RouteMapper.class);

    private final RoutePoint sp = new RoutePoint(-23.5505, -46.6333, "São Paulo");
    private final RoutePoint sjc = new RoutePoint(-23.1865, -45.8841, "São José dos Campos");
    private final RoutePoint rj = new RoutePoint(-22.9068, -43.1729, "Rio de Janeiro");

    @Test
    void toRouteResult_twoPoints_buildsSingleSegmentAndRoundsValues() {
        OrsRoute orsRoute = new OrsRoute(
                new OrsRouteSummary(1000.4, 600.6),
                "geo",
                List.of(new OrsSegment(1000.4, 600.6)));

        RouteResultDto dto = mapper.toRouteResult(orsRoute, "driving-car", List.of(sp, rj), List.of());

        assertThat(dto.profile()).isEqualTo("driving-car");
        assertThat(dto.distanceMeters()).isEqualTo(1000L);
        assertThat(dto.durationSeconds()).isEqualTo(601L);
        assertThat(dto.geometry()).isEqualTo("geo");
        assertThat(dto.segments()).hasSize(1);
        assertThat(dto.segments().get(0).fromLabel()).isEqualTo("São Paulo");
        assertThat(dto.segments().get(0).toLabel()).isEqualTo("Rio de Janeiro");
        assertThat(dto.segments().get(0).distanceMeters()).isEqualTo(1000L);
        assertThat(dto.segments().get(0).durationSeconds()).isEqualTo(601L);
        assertThat(dto.tollPlazas()).isEmpty();
    }

    @Test
    void toRouteResult_threePoints_zipsSegmentsWithConsecutiveLabels() {
        OrsRoute orsRoute = new OrsRoute(
                new OrsRouteSummary(2000.0, 1200.0),
                "geo2",
                List.of(new OrsSegment(800.0, 500.0), new OrsSegment(1200.0, 700.0)));

        RouteResultDto dto = mapper.toRouteResult(orsRoute, "driving-car", List.of(sp, sjc, rj), List.of());

        assertThat(dto.segments()).hasSize(2);
        assertThat(dto.segments().get(0).fromLabel()).isEqualTo("São Paulo");
        assertThat(dto.segments().get(0).toLabel()).isEqualTo("São José dos Campos");
        assertThat(dto.segments().get(0).distanceMeters()).isEqualTo(800L);
        assertThat(dto.segments().get(1).fromLabel()).isEqualTo("São José dos Campos");
        assertThat(dto.segments().get(1).toLabel()).isEqualTo("Rio de Janeiro");
        assertThat(dto.segments().get(1).durationSeconds()).isEqualTo(700L);
    }

    @Test
    void toRouteResult_drivingHgvProfile_carriesProfile() {
        OrsRoute orsRoute = new OrsRoute(
                new OrsRouteSummary(2000.0, 900.0),
                "hgvGeo",
                List.of(new OrsSegment(2000.0, 900.0)));

        RouteResultDto dto = mapper.toRouteResult(orsRoute, "driving-hgv", List.of(sp, rj), List.of());

        assertThat(dto.profile()).isEqualTo("driving-hgv");
        assertThat(dto.distanceMeters()).isEqualTo(2000L);
        assertThat(dto.durationSeconds()).isEqualTo(900L);
    }

    @Test
    void toEntity_drivingHgvProfile_persistsProfile() {
        RoutePlanRequest request = new RoutePlanRequest(
                "driving-hgv", sp, rj, null, "Rota HGV");
        RouteResultDto result = new RouteResultDto("driving-hgv", 500_000L, 25_000L, "hgvGeo", List.of(), List.of());

        PlannedRoute entity = mapper.toEntity(request, result);

        assertThat(entity.getProfile()).isEqualTo("driving-hgv");
    }

    @Test
    void toDto_drivingHgvProfile_roundTrips() {
        RoutePlanRequest request = new RoutePlanRequest(
                "driving-hgv", sp, rj, null, "Rota HGV");
        RouteResultDto result = new RouteResultDto("driving-hgv", 500_000L, 25_000L, "hgvGeo", List.of(), List.of());
        PlannedRoute entity = mapper.toEntity(request, result);
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(Instant.parse("2026-06-23T00:00:00Z"));

        PlannedRouteDto dto = mapper.toDto(entity);

        assertThat(dto.profile()).isEqualTo("driving-hgv");
    }

    @Test
    void toEntity_flattensPointsAndAssignsStopOrderAndBackReference() {
        RoutePlanRequest request = new RoutePlanRequest(
                "driving-car", sp, rj, List.of(sjc), "Minha rota");
        RouteResultDto result = new RouteResultDto("driving-car", 430120L, 19800L, "geoX", List.of(), List.of());

        PlannedRoute entity = mapper.toEntity(request, result);

        assertThat(entity.getName()).isEqualTo("Minha rota");
        assertThat(entity.getProfile()).isEqualTo("driving-car");
        assertThat(entity.getOriginLat()).isEqualTo(-23.5505);
        assertThat(entity.getOriginLabel()).isEqualTo("São Paulo");
        assertThat(entity.getDestinationLabel()).isEqualTo("Rio de Janeiro");
        assertThat(entity.getDistanceMeters()).isEqualTo(430120L);
        assertThat(entity.getDurationSeconds()).isEqualTo(19800L);
        assertThat(entity.getGeometry()).isEqualTo("geoX");
        assertThat(entity.getStops()).hasSize(1);
        assertThat(entity.getStops().get(0).getLabel()).isEqualTo("São José dos Campos");
        assertThat(entity.getStops().get(0).getStopOrder()).isZero();
        assertThat(entity.getStops().get(0).getPlannedRoute()).isSameAs(entity);
    }

    @Test
    void toDto_reconstructsPointsFromFlatColumns() {
        RoutePlanRequest request = new RoutePlanRequest(
                "driving-car", sp, rj, List.of(sjc), "Minha rota");
        RouteResultDto result = new RouteResultDto("driving-car", 430120L, 19800L, "geoX", List.of(), List.of());
        PlannedRoute entity = mapper.toEntity(request, result);
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(Instant.parse("2026-06-17T00:00:00Z"));

        PlannedRouteDto dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(entity.getId());
        assertThat(dto.origin()).isEqualTo(sp);
        assertThat(dto.destination()).isEqualTo(rj);
        assertThat(dto.stops()).containsExactly(sjc);
        assertThat(dto.distanceMeters()).isEqualTo(430120L);
        assertThat(dto.geometry()).isEqualTo("geoX");
        assertThat(dto.createdAt()).isEqualTo(Instant.parse("2026-06-17T00:00:00Z"));
    }
}
