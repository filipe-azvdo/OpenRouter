package com.personalrouter.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.personalrouter.repository.PlannedRouteRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração end-to-end da API de rotas: exercita a stack completa
 * (controller → service → repository → PostgreSQL real via Testcontainers) com o OpenRouteService
 * mockado por HTTP (WireMock). Cobre os cenários de aceitação da KAN-11.
 *
 * <p>Skipa automaticamente em máquinas sem Docker ({@code disabledWithoutDocker = true}).
 */
@SpringBootTest(properties = "ors.api.key=test-key")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RouteApiIntegrationTest {

    private static final String BASE_URL = "/api/v1/routes";
    private static final String PLAN_URL = BASE_URL + "/plan";
    private static final String ORS_CAR_PATH = "/v2/directions/driving-car";
    private static final String ORS_HGV_PATH = "/v2/directions/driving-hgv";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void orsProperties(DynamicPropertyRegistry registry) {
        registry.add("ors.api.base-url", wireMock::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannedRouteRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // Stubs e fixtures
    // -----------------------------------------------------------------------

    private void stubOrs(int status, String body) {
        stubOrs(ORS_CAR_PATH, status, body);
    }

    private void stubOrsHgv(int status, String body) {
        stubOrs(ORS_HGV_PATH, status, body);
    }

    private void stubOrs(String path, int status, String body) {
        wireMock.stubFor(WireMock.post(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String fixture(String name) throws IOException {
        try (java.io.InputStream in = RouteApiIntegrationTest.class
                .getResourceAsStream("/ors/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Corpo válido A→B sem paradas. */
    private static String bodyNoStops() {
        return """
            {
              "origin":      {"lat": -23.5505, "lon": -46.6333, "label": "São Paulo"},
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"}
            }
            """;
    }

    /** Corpo válido A→B com perfil driving-hgv. */
    private static String bodyHgvNoStops() {
        return """
            {
              "profile":     "driving-hgv",
              "origin":      {"lat": -23.5505, "lon": -46.6333, "label": "São Paulo"},
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"}
            }
            """;
    }

    /** Corpo válido com duas paradas intermediárias na ordem origin → Campinas → SJC → destino. */
    private static String bodyTwoStops() {
        return """
            {
              "name":        "SP -> RJ",
              "origin":      {"lat": -23.5505, "lon": -46.6333, "label": "São Paulo"},
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"},
              "stops": [
                {"lat": -22.9099, "lon": -47.0626, "label": "Campinas"},
                {"lat": -23.1896, "lon": -45.8841, "label": "São José dos Campos"}
              ]
            }
            """;
    }

    // -----------------------------------------------------------------------
    // Cenário 1: rota válida A → B (sem paradas) → 200
    // -----------------------------------------------------------------------

    @Test
    void planRotaValidaSemParadas_retorna200() throws Exception {
        stubOrs(200, fixture("directions-single-segment.json"));

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyNoStops()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile").value("driving-car"))
            .andExpect(jsonPath("$.distanceMeters").value(950))
            .andExpect(jsonPath("$.durationSeconds").value(120))
            .andExpect(jsonPath("$.geometry").value("singleSegmentGeometry"))
            .andExpect(jsonPath("$.segments.length()").value(1))
            .andExpect(jsonPath("$.segments[0].fromLabel").value("São Paulo"))
            .andExpect(jsonPath("$.segments[0].toLabel").value("Rio de Janeiro"));
    }

    // -----------------------------------------------------------------------
    // Cenários 2, 7, 8, 9: criar com paradas em ordem → recuperar → listar → excluir → 404
    // -----------------------------------------------------------------------

    @Test
    void criarComParadasEmOrdem_recuperar_listar_excluir_e404() throws Exception {
        stubOrs(200, fixture("directions-three-segments.json"));

        MvcResult created = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyTwoStops()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString(BASE_URL + "/")))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("SP -> RJ"))
            .andExpect(jsonPath("$.profile").value("driving-car"))
            .andExpect(jsonPath("$.distanceMeters").value(3000))
            .andExpect(jsonPath("$.durationSeconds").value(600))
            .andExpect(jsonPath("$.geometry").value("threeSegmentGeometry"))
            .andExpect(jsonPath("$.stops.length()").value(2))
            .andExpect(jsonPath("$.stops[0].label").value("Campinas"))
            .andExpect(jsonPath("$.stops[1].label").value("São José dos Campos"))
            .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // Recuperar (GET) → 200 com paradas na ordem persistida
        mockMvc.perform(get(BASE_URL + "/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.stops.length()").value(2))
            .andExpect(jsonPath("$.stops[0].label").value("Campinas"))
            .andExpect(jsonPath("$.stops[1].label").value("São José dos Campos"));

        // Listar → 200 com a rota recém-criada
        mockMvc.perform(get(BASE_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id));

        // Excluir → 204
        mockMvc.perform(delete(BASE_URL + "/{id}", id))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        // GET posterior → 404 ProblemDetail
        mockMvc.perform(get(BASE_URL + "/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // -----------------------------------------------------------------------
    // Cenário: rota driving-hgv → calcular, salvar, recuperar com perfil persistido
    // -----------------------------------------------------------------------

    @Test
    void fluxoDrivingHgv_calcularSalvarRecuperar_persistePerfil() throws Exception {
        stubOrsHgv(200, fixture("directions-single-segment.json"));

        // Plan → 200 com perfil driving-hgv
        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyHgvNoStops()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile").value("driving-hgv"));

        // Create → 201, persiste com driving-hgv
        MvcResult created = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyHgvNoStops()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.profile").value("driving-hgv"))
            .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // GET → 200 com perfil driving-hgv persistido
        mockMvc.perform(get(BASE_URL + "/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile").value("driving-hgv"));
    }

    // -----------------------------------------------------------------------
    // Cenário 3: coordenadas inválidas → 400 (ProblemDetail)
    // -----------------------------------------------------------------------

    @Test
    void coordenadasInvalidas_retorna400() throws Exception {
        String body = """
            {
              "origin":      {"lat": 91.0, "lon": -46.6333, "label": "Inválida"},
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"}
            }
            """;

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // -----------------------------------------------------------------------
    // Cenário 4: perfil inválido → 400
    // -----------------------------------------------------------------------

    @Test
    void perfilInvalido_retorna400() throws Exception {
        String body = """
            {
              "profile":     "foot-walking",
              "origin":      {"lat": -23.5505, "lon": -46.6333, "label": "São Paulo"},
              "destination": {"lat": -22.9068, "lon": -43.1729, "label": "Rio de Janeiro"}
            }
            """;

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // -----------------------------------------------------------------------
    // Cenário 5: ORS 429 (cota) → traduzido para 503
    // -----------------------------------------------------------------------

    @Test
    void orsCotaExcedida_retorna503() throws Exception {
        stubOrs(429, "{\"error\":\"Quota exceeded\"}");

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyNoStops()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // -----------------------------------------------------------------------
    // Cenário 6: ORS indisponível (5xx) → tratamento gracioso (503)
    // -----------------------------------------------------------------------

    @Test
    void orsIndisponivel_retorna503() throws Exception {
        stubOrs(503, "{\"error\":\"Service unavailable\"}");

        mockMvc.perform(post(PLAN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyNoStops()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
