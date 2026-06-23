package com.personalrouter.client;

import com.personalrouter.client.dto.OrsDirectionsRequest;
import com.personalrouter.client.dto.OrsDirectionsResponse;
import com.personalrouter.client.dto.OrsRoute;
import com.personalrouter.client.dto.OrsRouteSummary;
import com.personalrouter.dto.Coordinate;
import com.personalrouter.exception.OpenRouteServiceException;
import com.personalrouter.exception.OpenRouteServiceQuotaExceededException;
import com.personalrouter.exception.OpenRouteServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenRouteServiceGatewayTest {

    @Mock
    private OpenRouteServiceClient client;

    @Mock
    private CoordinateConverter converter;

    @InjectMocks
    private OpenRouteServiceGateway gateway;

    private List<Coordinate> points;
    private List<List<Double>> orsCoords;

    @BeforeEach
    void setUp() {
        points = List.of(new Coordinate(48.8566, 2.3522), new Coordinate(51.5074, -0.1278));
        orsCoords = List.of(List.of(2.3522, 48.8566), List.of(-0.1278, 51.5074));
    }

    @Test
    void getDirections_delegatesToConverterAndClient() {
        OrsDirectionsResponse expectedResponse = new OrsDirectionsResponse(
                List.of(new OrsRoute(new OrsRouteSummary(1000.0, 120.0), "encodedGeometry", List.of()))
        );
        when(converter.toOrsCoordinates(points)).thenReturn(orsCoords);
        when(client.getDirections(eq("driving-car"), any(OrsDirectionsRequest.class))).thenReturn(expectedResponse);

        OrsDirectionsResponse result = gateway.getDirections("driving-car", points);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void getDirections_drivingHgv_delegatesToConverterAndClient() {
        OrsDirectionsResponse expectedResponse = new OrsDirectionsResponse(
                List.of(new OrsRoute(new OrsRouteSummary(2000.0, 300.0), "hgvGeometry", List.of()))
        );
        when(converter.toOrsCoordinates(points)).thenReturn(orsCoords);
        when(client.getDirections(eq("driving-hgv"), any(OrsDirectionsRequest.class))).thenReturn(expectedResponse);

        OrsDirectionsResponse result = gateway.getDirections("driving-hgv", points);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void getDirections_rethrowsDomainExceptionFromErrorDecoder() {
        OpenRouteServiceQuotaExceededException domainException =
                new OpenRouteServiceQuotaExceededException("Cota excedida");
        when(converter.toOrsCoordinates(points)).thenReturn(orsCoords);
        when(client.getDirections(any(), any())).thenThrow(domainException);

        Throwable thrown = catchThrowable(() -> gateway.getDirections("driving-car", points));
        assertThat(thrown).isSameAs(domainException);
    }

    @Test
    void getDirections_convertsRetryableExceptionToUnavailable() {
        Request dummyRequest = Request.create(
                Request.HttpMethod.POST,
                "http://test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        RetryableException retryable = new RetryableException(
                503, "timeout", Request.HttpMethod.POST, new Date(), dummyRequest
        );
        when(converter.toOrsCoordinates(points)).thenReturn(orsCoords);
        when(client.getDirections(any(), any())).thenThrow(retryable);

        assertThatThrownBy(() -> gateway.getDirections("driving-car", points))
                .isInstanceOf(OpenRouteServiceUnavailableException.class)
                .hasMessageContaining("timeout/conexão");
    }

    @Test
    void getDirections_convertsFeignExceptionToBaseException() {
        FeignException feignException = FeignException.errorStatus(
                "test",
                feign.Response.builder()
                        .status(400)
                        .reason("bad request")
                        .request(Request.create(
                                Request.HttpMethod.POST,
                                "http://test",
                                Collections.emptyMap(),
                                null,
                                StandardCharsets.UTF_8,
                                null
                        ))
                        .headers(Collections.emptyMap())
                        .build()
        );
        when(converter.toOrsCoordinates(points)).thenReturn(orsCoords);
        when(client.getDirections(any(), any())).thenThrow(feignException);

        assertThatThrownBy(() -> gateway.getDirections("driving-car", points))
                .isInstanceOf(OpenRouteServiceException.class)
                .hasMessageContaining("comunicação com o ORS");
    }
}
