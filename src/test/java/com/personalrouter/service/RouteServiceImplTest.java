package com.personalrouter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.personalrouter.client.OpenRouteServiceGateway;
import com.personalrouter.client.dto.OrsDirectionsResponse;
import com.personalrouter.client.dto.OrsRoute;
import com.personalrouter.client.dto.OrsRouteSummary;
import com.personalrouter.client.dto.OrsSegment;
import com.personalrouter.dto.Coordinate;
import com.personalrouter.dto.PlannedRouteDto;
import com.personalrouter.dto.RoutePlanRequest;
import com.personalrouter.dto.RoutePoint;
import com.personalrouter.dto.RouteResultDto;
import com.personalrouter.exception.OpenRouteServiceQuotaExceededException;
import com.personalrouter.exception.OpenRouteServiceUnavailableException;
import com.personalrouter.exception.RouteCalculationException;
import com.personalrouter.exception.RouteNotFoundException;
import com.personalrouter.mapper.RouteMapper;
import com.personalrouter.model.PlannedRoute;
import com.personalrouter.repository.PlannedRouteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class RouteServiceImplTest {

    @Mock
    private OpenRouteServiceGateway gateway;

    @Mock
    private RouteMapper mapper;

    @Mock
    private PlannedRouteRepository repository;

    @Mock
    private TollMatchingService tollMatchingService;

    @InjectMocks
    private RouteServiceImpl service;

    private final RoutePoint origin = new RoutePoint(-23.5505, -46.6333, "São Paulo");
    private final RoutePoint destination = new RoutePoint(-22.9068, -43.1729, "Rio de Janeiro");

    private static OrsDirectionsResponse singleRouteResponse() {
        return new OrsDirectionsResponse(List.of(
                new OrsRoute(new OrsRouteSummary(100.0, 60.0), "geo", List.of(new OrsSegment(100.0, 60.0)))));
    }

    private static PlannedRouteDto dummyDto() {
        return new PlannedRouteDto(UUID.randomUUID(), "n", "driving-car", null, null, List.of(),
                100L, 60L, "geo", List.of(), Instant.now());
    }

    @Test
    void planRoute_noStops_callsGatewayWithDefaultProfileAndOrderedCoordinates() {
        RoutePlanRequest request = new RoutePlanRequest(null, origin, destination, null, null);
        RouteResultDto expected = new RouteResultDto("driving-car", 100L, 60L, "geo", List.of(), List.of());
        when(gateway.getDirections(eq("driving-car"), anyList())).thenReturn(singleRouteResponse());
        when(tollMatchingService.findTollPlazasAlongRoute(any())).thenReturn(List.of());
        when(mapper.toRouteResult(any(), eq("driving-car"), anyList(), anyList())).thenReturn(expected);

        RouteResultDto result = service.planRoute(request);

        assertThat(result).isSameAs(expected);
        List<Coordinate> coordinates = captureGatewayCoordinates();
        assertThat(coordinates).containsExactly(
                new Coordinate(-23.5505, -46.6333),
                new Coordinate(-22.9068, -43.1729));
    }

    @Test
    void planRoute_withStops_buildsOriginThenStopsThenDestination() {
        RoutePoint stop1 = new RoutePoint(-23.1865, -45.8841, "SJC");
        RoutePoint stop2 = new RoutePoint(-22.97, -44.30, "Resende");
        RoutePlanRequest request =
                new RoutePlanRequest("driving-car", origin, destination, List.of(stop1, stop2), null);
        when(gateway.getDirections(eq("driving-car"), anyList())).thenReturn(singleRouteResponse());
        when(tollMatchingService.findTollPlazasAlongRoute(any())).thenReturn(List.of());
        when(mapper.toRouteResult(any(), eq("driving-car"), anyList(), anyList())).thenReturn(
                new RouteResultDto("driving-car", 1L, 1L, "geo", List.of(), List.of()));

        service.planRoute(request);

        assertThat(captureGatewayCoordinates()).containsExactly(
                new Coordinate(-23.5505, -46.6333),
                new Coordinate(-23.1865, -45.8841),
                new Coordinate(-22.97, -44.30),
                new Coordinate(-22.9068, -43.1729));
        assertThat(captureMapperOrderedPoints()).containsExactly(origin, stop1, stop2, destination);
    }

    @Test
    void planRoute_drivingHgvProfile_callsGatewayWithHgvProfile() {
        RoutePlanRequest request = new RoutePlanRequest("driving-hgv", origin, destination, null, null);
        RouteResultDto expected = new RouteResultDto("driving-hgv", 100L, 60L, "geo", List.of(), List.of());
        when(gateway.getDirections(eq("driving-hgv"), anyList())).thenReturn(singleRouteResponse());
        when(tollMatchingService.findTollPlazasAlongRoute(any())).thenReturn(List.of());
        when(mapper.toRouteResult(any(), eq("driving-hgv"), anyList(), anyList())).thenReturn(expected);

        RouteResultDto result = service.planRoute(request);

        assertThat(result).isSameAs(expected);
        assertThat(result.profile()).isEqualTo("driving-hgv");
        verify(gateway).getDirections(eq("driving-hgv"), anyList());
    }

    @Test
    void planRoute_emptyRoutes_throwsRouteCalculationException() {
        RoutePlanRequest request = new RoutePlanRequest("driving-car", origin, destination, null, null);
        when(gateway.getDirections(any(), anyList())).thenReturn(new OrsDirectionsResponse(List.of()));

        assertThatThrownBy(() -> service.planRoute(request))
                .isInstanceOf(RouteCalculationException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void planRoute_propagatesQuotaExceededException() {
        RoutePlanRequest request = new RoutePlanRequest("driving-car", origin, destination, null, null);
        when(gateway.getDirections(any(), anyList()))
                .thenThrow(new OpenRouteServiceQuotaExceededException("cota"));

        assertThatThrownBy(() -> service.planRoute(request))
                .isInstanceOf(OpenRouteServiceQuotaExceededException.class);
    }

    @Test
    void planRoute_propagatesUnavailableException() {
        RoutePlanRequest request = new RoutePlanRequest("driving-car", origin, destination, null, null);
        when(gateway.getDirections(any(), anyList()))
                .thenThrow(new OpenRouteServiceUnavailableException("indisponível"));

        assertThatThrownBy(() -> service.planRoute(request))
                .isInstanceOf(OpenRouteServiceUnavailableException.class);
    }

    @Test
    void createRoute_calculatesPersistsAndReturnsDto() {
        RoutePlanRequest request = new RoutePlanRequest("driving-car", origin, destination, null, "Minha rota");
        RouteResultDto result = new RouteResultDto("driving-car", 100L, 60L, "geo", List.of(), List.of());
        PlannedRoute entity = new PlannedRoute();
        PlannedRoute saved = new PlannedRoute();
        saved.setId(UUID.randomUUID());
        PlannedRouteDto dto = dummyDto();
        when(gateway.getDirections(eq("driving-car"), anyList())).thenReturn(singleRouteResponse());
        when(tollMatchingService.findTollPlazasAlongRoute(any())).thenReturn(List.of());
        when(mapper.toRouteResult(any(), eq("driving-car"), anyList(), anyList())).thenReturn(result);
        when(mapper.toEntity(request, result)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(dto);

        PlannedRouteDto out = service.createRoute(request);

        assertThat(out.name()).isEqualTo(dto.name());
        assertThat(out.tollPlazas()).isEmpty();
        verify(repository).save(entity);
    }

    @Test
    void listRoutes_returnsMappedRoutesSortedByCreatedAtDesc() {
        PlannedRoute e1 = new PlannedRoute();
        PlannedRoute e2 = new PlannedRoute();
        PlannedRouteDto d1 = dummyDto();
        PlannedRouteDto d2 = dummyDto();
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(e1, e2));
        when(mapper.toDto(e1)).thenReturn(d1);
        when(mapper.toDto(e2)).thenReturn(d2);
        when(tollMatchingService.findTollPlazasAlongRoute(any())).thenReturn(List.of());

        List<PlannedRouteDto> out = service.listRoutes();

        assertThat(out).hasSize(2);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findAll(sortCaptor.capture());
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void getRoute_found_returnsDto() {
        UUID id = UUID.randomUUID();
        PlannedRoute entity = new PlannedRoute();
        PlannedRouteDto dto = dummyDto();
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(dto);
        when(tollMatchingService.findTollPlazasAlongRoute(any())).thenReturn(List.of());

        PlannedRouteDto result = service.getRoute(id);
        assertThat(result.name()).isEqualTo(dto.name());
    }

    @Test
    void getRoute_missing_throwsRouteNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoute(id)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void deleteRoute_existing_deletes() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.deleteRoute(id);

        verify(repository).deleteById(id);
    }

    @Test
    void deleteRoute_missing_throwsAndDoesNotDelete() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteRoute(id)).isInstanceOf(RouteNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @SuppressWarnings("unchecked")
    private List<Coordinate> captureGatewayCoordinates() {
        ArgumentCaptor<List<Coordinate>> captor = ArgumentCaptor.forClass(List.class);
        verify(gateway).getDirections(eq("driving-car"), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<RoutePoint> captureMapperOrderedPoints() {
        ArgumentCaptor<List<RoutePoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).toRouteResult(any(), eq("driving-car"), captor.capture(), anyList());
        return captor.getValue();
    }
}
