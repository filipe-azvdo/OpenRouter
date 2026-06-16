package com.personalrouter.client;

import com.personalrouter.client.dto.OrsDirectionsRequest;
import com.personalrouter.client.dto.OrsDirectionsResponse;
import com.personalrouter.dto.Coordinate;
import com.personalrouter.exception.OpenRouteServiceException;
import com.personalrouter.exception.OpenRouteServiceUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenRouteServiceGateway {

    private final OpenRouteServiceClient client;
    private final CoordinateConverter converter;

    public OpenRouteServiceGateway(OpenRouteServiceClient client, CoordinateConverter converter) {
        this.client = client;
        this.converter = converter;
    }

    public OrsDirectionsResponse getDirections(String profile, List<Coordinate> orderedPoints) {
        OrsDirectionsRequest request = new OrsDirectionsRequest(
                converter.toOrsCoordinates(orderedPoints),
                false
        );
        try {
            return client.getDirections(profile, request);
        } catch (OpenRouteServiceException e) {
            throw e;
        } catch (RetryableException e) {
            throw new OpenRouteServiceUnavailableException("ORS indisponível (timeout/conexão)", e);
        } catch (FeignException e) {
            throw new OpenRouteServiceException("Falha na comunicação com o ORS", e);
        }
    }
}
