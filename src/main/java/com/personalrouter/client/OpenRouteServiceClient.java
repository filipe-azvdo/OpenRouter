package com.personalrouter.client;

import com.personalrouter.client.dto.OrsDirectionsRequest;
import com.personalrouter.client.dto.OrsDirectionsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "openRouteServiceClient",
        url = "${ors.api.base-url}",
        configuration = OpenRouteServiceClientConfiguration.class
)
public interface OpenRouteServiceClient {

    @PostMapping(value = "/v2/directions/{profile}", consumes = MediaType.APPLICATION_JSON_VALUE)
    OrsDirectionsResponse getDirections(
            @PathVariable("profile") String profile,
            @RequestBody OrsDirectionsRequest request
    );
}
