package com.personalrouter.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.personalrouter.client.dto.OrsDirectionsResponse;
import com.personalrouter.dto.Coordinate;
import com.personalrouter.repository.PlannedRouteRepository;
import com.personalrouter.repository.TollPlazaImportRepository;
import com.personalrouter.repository.TollPlazaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = NONE,
        properties = {
                "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                "ors.api.key=test-key-never-real"
        }
)
class OpenRouteServiceClientWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ors.api.base-url", wireMock::baseUrl);
    }

    @Autowired
    private OpenRouteServiceGateway gateway;

    @MockitoBean
    private PlannedRouteRepository plannedRouteRepository;

    @MockitoBean
    private TollPlazaRepository tollPlazaRepository;

    @MockitoBean
    private TollPlazaImportRepository tollPlazaImportRepository;

    @Test
    void getDirections_happyPath_deserializesResponseCorrectly() throws Exception {
        String responseBody = new String(
                OpenRouteServiceClientWireMockTest.class
                        .getResourceAsStream("/ors/directions-response.json")
                        .readAllBytes()
        );

        wireMock.stubFor(post(urlEqualTo("/v2/directions/driving-car"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        List<Coordinate> points = List.of(
                new Coordinate(48.8566, 2.3522),
                new Coordinate(51.5074, -0.1278)
        );

        OrsDirectionsResponse response = gateway.getDirections("driving-car", points);

        assertThat(response.routes()).hasSize(1);
        assertThat(response.routes().get(0).summary().distance()).isEqualTo(1234.5);
        assertThat(response.routes().get(0).summary().duration()).isEqualTo(180.0);
        assertThat(response.routes().get(0).geometry()).isEqualTo("encodedPolylineString");
        assertThat(response.routes().get(0).segments()).hasSize(2);
        assertThat(response.routes().get(0).segments().get(0).distance()).isEqualTo(800.0);
        assertThat(response.routes().get(0).segments().get(1).duration()).isEqualTo(60.0);
    }
}
