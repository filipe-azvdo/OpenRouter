package com.personalrouter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personalrouter.dto.PlannedRouteDto;
import com.personalrouter.dto.RoutePoint;
import com.personalrouter.dto.RouteResultDto;
import com.personalrouter.dto.RouteSegmentDto;
import com.personalrouter.exception.OpenRouteServiceException;
import com.personalrouter.exception.RouteCalculationException;
import com.personalrouter.exception.RouteNotFoundException;
import com.personalrouter.service.RouteService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(value = RouteController.class, excludeAutoConfiguration = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class
})
class RouteControllerTest {

    private static final String BASE_URL = "/api/v1/routes";
    private static final String PLAN_URL = BASE_URL + "/plan";
    private static final UUID FIXED_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteService routeService;

    // -----------------------------------------------------------------------
    // Fixture builders
    // -----------------------------------------------------------------------

    private static RoutePoint saoPaulo() {
        return new RoutePoint(-23.5505, -46.6333, "São Paulo");
    }

    private static RoutePoint rioDeJaneiro() {
        return new RoutePoint(-22.9068, -43.1729, "Rio de Janeiro");
    }

    private static RouteResultDto sampleRouteResult() {
        RouteSegmentDto seg = new RouteSegmentDto("São Paulo", "Rio de Janeiro", 434_000L, 16_200L);
        return new RouteResultDto("driving-car", 434_000L, 16_200L, "encodedGeometry", List.of(seg));
    }

    private static RouteResultDto sampleHgvRouteResult() {
        RouteSegmentDto seg = new RouteSegmentDto("São Paulo", "Rio de Janeiro", 434_000L, 16_200L);
        return new RouteResultDto("driving-hgv", 434_000L, 16_200L, "encodedGeometry", List.of(seg));
    }

    private static PlannedRouteDto samplePlannedRoute() {
        return new PlannedRouteDto(
            FIXED_ID,
            "SP → RJ",
            "driving-car",
            saoPaulo(),
            rioDeJaneiro(),
            List.of(),
            434_000L,
            16_200L,
            "encodedGeometry",
            Instant.parse("2024-01-15T10:00:00Z")
        );
    }

    private static PlannedRouteDto sampleHgvPlannedRoute() {
        return new PlannedRouteDto(
            FIXED_ID,
            "SP → RJ HGV",
            "driving-hgv",
            saoPaulo(),
            rioDeJaneiro(),
            List.of(),
            434_000L,
            16_200L,
            "encodedGeometry",
            Instant.parse("2024-01-15T10:00:00Z")
        );
    }

    /** Minimal valid JSON body — only origin and destination required. */
    private static String validBody() {
        return """
            {
              "origin":      {"lat": -23.5505, "lon": -46.6333, "label": "São Paulo"},
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"}
            }
            """;
    }

    private static String validHgvBody() {
        return """
            {
              "profile":     "driving-hgv",
              "origin":      {"lat": -23.5505, "lon": -46.6333, "label": "São Paulo"},
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"}
            }
            """;
    }

    // -----------------------------------------------------------------------
    // Happy path — all 5 endpoints
    // -----------------------------------------------------------------------

    @Test
    void planRoute_validBody_returns200WithDto() throws Exception {
        when(routeService.planRoute(any())).thenReturn(sampleRouteResult());

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile").value("driving-car"))
            .andExpect(jsonPath("$.distanceMeters").value(434_000))
            .andExpect(jsonPath("$.durationSeconds").value(16_200))
            .andExpect(jsonPath("$.geometry").value("encodedGeometry"))
            .andExpect(jsonPath("$.segments").isArray());
    }

    @Test
    void planRoute_drivingHgvProfile_returns200WithHgvProfile() throws Exception {
        when(routeService.planRoute(any())).thenReturn(sampleHgvRouteResult());

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validHgvBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile").value("driving-hgv"));
    }

    @Test
    void createRoute_drivingHgvProfile_returns201WithHgvProfile() throws Exception {
        when(routeService.createRoute(any())).thenReturn(sampleHgvPlannedRoute());

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validHgvBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.profile").value("driving-hgv"));
    }

    @Test
    void createRoute_validBody_returns201WithLocationHeader() throws Exception {
        when(routeService.createRoute(any())).thenReturn(samplePlannedRoute());

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                "/api/v1/routes/" + FIXED_ID)))
            .andExpect(jsonPath("$.id").value(FIXED_ID.toString()))
            .andExpect(jsonPath("$.name").value("SP → RJ"))
            .andExpect(jsonPath("$.profile").value("driving-car"))
            .andExpect(jsonPath("$.distanceMeters").value(434_000));
    }

    @Test
    void listRoutes_returns200WithJsonArray() throws Exception {
        when(routeService.listRoutes()).thenReturn(List.of(samplePlannedRoute()));

        mockMvc.perform(get(BASE_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value(FIXED_ID.toString()));
    }

    @Test
    void getRoute_existingId_returns200WithDto() throws Exception {
        when(routeService.getRoute(FIXED_ID)).thenReturn(samplePlannedRoute());

        mockMvc.perform(get(BASE_URL + "/{id}", FIXED_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(FIXED_ID.toString()))
            .andExpect(jsonPath("$.profile").value("driving-car"));
    }

    @Test
    void deleteRoute_existingId_returns204WithNoBody() throws Exception {
        doNothing().when(routeService).deleteRoute(FIXED_ID);

        mockMvc.perform(delete(BASE_URL + "/{id}", FIXED_ID))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    // -----------------------------------------------------------------------
    // Validation errors — 400 + application/problem+json
    // -----------------------------------------------------------------------

    @Test
    void planRoute_missingOrigin_returns400ProblemJson() throws Exception {
        String body = """
            {
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"}
            }
            """;

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void planRoute_missingDestination_returns400ProblemJson() throws Exception {
        String body = """
            {
              "origin": {"lat": -23.5505, "lon": -46.6333, "label": "São Paulo"}
            }
            """;

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void planRoute_stopsExceedMaxSize_returns400ProblemJson() throws Exception {
        String elevenStops = "[" +
            "{\"lat\": 0.0, \"lon\": 0.0}," +
            "{\"lat\": 1.0, \"lon\": 1.0}," +
            "{\"lat\": 2.0, \"lon\": 2.0}," +
            "{\"lat\": 3.0, \"lon\": 3.0}," +
            "{\"lat\": 4.0, \"lon\": 4.0}," +
            "{\"lat\": 5.0, \"lon\": 5.0}," +
            "{\"lat\": 6.0, \"lon\": 6.0}," +
            "{\"lat\": 7.0, \"lon\": 7.0}," +
            "{\"lat\": 8.0, \"lon\": 8.0}," +
            "{\"lat\": 9.0, \"lon\": 9.0}," +
            "{\"lat\": 10.0, \"lon\": 10.0}" +
            "]";
        String body = """
            {
              "origin":      {"lat": -23.5505, "lon": -46.6333},
              "destination": {"lat": -22.9068, "lon": -43.1729},
              "stops": %s
            }
            """.formatted(elevenStops);

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void planRoute_latOutOfRange_returns400ProblemJson() throws Exception {
        String body = """
            {
              "origin":      {"lat": 91.0, "lon": -46.6333},
              "destination": {"lat": -22.9068, "lon": -43.1729}
            }
            """;

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void planRoute_lonOutOfRange_returns400ProblemJson() throws Exception {
        String body = """
            {
              "origin":      {"lat": -23.5505, "lon": 200.0},
              "destination": {"lat": -22.9068, "lon": -43.1729}
            }
            """;

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void planRoute_unsupportedProfile_returns400ProblemJsonWithBothProfiles() throws Exception {
        String body = """
            {
              "profile":     "foot-walking",
              "origin":      {"lat": -23.5505, "lon": -46.6333},
              "destination": {"lat": -22.9068, "lon": -43.1729}
            }
            """;

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(content().string(containsString("driving-car")))
            .andExpect(content().string(containsString("driving-hgv")));
    }

    // -----------------------------------------------------------------------
    // Malformed input
    // -----------------------------------------------------------------------

    @Test
    void planRoute_malformedJson_returns400ProblemJson() throws Exception {
        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not valid json }"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // -----------------------------------------------------------------------
    // Service exceptions
    // -----------------------------------------------------------------------

    @Test
    void getRoute_notFound_returns404ProblemJson() throws Exception {
        when(routeService.getRoute(FIXED_ID))
            .thenThrow(new RouteNotFoundException("Route " + FIXED_ID + " not found"));

        mockMvc.perform(get(BASE_URL + "/{id}", FIXED_ID))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void deleteRoute_notFound_returns404ProblemJson() throws Exception {
        doThrow(new RouteNotFoundException("Route " + FIXED_ID + " not found"))
            .when(routeService).deleteRoute(FIXED_ID);

        mockMvc.perform(delete(BASE_URL + "/{id}", FIXED_ID))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void planRoute_routeCalculationException_returns422ProblemJson() throws Exception {
        when(routeService.planRoute(any()))
            .thenThrow(new RouteCalculationException("No route could be calculated"));

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void planRoute_openRouteServiceException_returns503ProblemJson() throws Exception {
        when(routeService.planRoute(any()))
            .thenThrow(new OpenRouteServiceException("ORS service down"));

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
