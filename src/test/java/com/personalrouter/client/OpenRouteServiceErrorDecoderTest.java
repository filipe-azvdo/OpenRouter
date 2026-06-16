package com.personalrouter.client;

import com.personalrouter.exception.OpenRouteServiceException;
import com.personalrouter.exception.OpenRouteServiceQuotaExceededException;
import com.personalrouter.exception.OpenRouteServiceUnavailableException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouteServiceErrorDecoderTest {

    private final OpenRouteServiceErrorDecoder decoder = new OpenRouteServiceErrorDecoder();

    private Response buildResponse(int status) {
        return Response.builder()
                .status(status)
                .reason("reason")
                .request(Request.create(
                        Request.HttpMethod.POST,
                        "http://test/v2/directions/driving-car",
                        Collections.emptyMap(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                ))
                .headers(Collections.emptyMap())
                .build();
    }

    @Test
    void decode_429_returnsQuotaExceededException() {
        Exception result = decoder.decode("methodKey", buildResponse(429));

        assertThat(result).isInstanceOf(OpenRouteServiceQuotaExceededException.class);
        assertThat(result.getMessage()).doesNotContain("{", "}", "error", "body");
    }

    @Test
    void decode_500_returnsUnavailableException() {
        Exception result = decoder.decode("methodKey", buildResponse(500));

        assertThat(result).isInstanceOf(OpenRouteServiceUnavailableException.class);
        assertThat(result.getMessage()).contains("HTTP 500");
    }

    @Test
    void decode_503_returnsUnavailableException() {
        Exception result = decoder.decode("methodKey", buildResponse(503));

        assertThat(result).isInstanceOf(OpenRouteServiceUnavailableException.class);
    }

    @Test
    void decode_400_returnsBaseException() {
        Exception result = decoder.decode("methodKey", buildResponse(400));

        assertThat(result).isInstanceOf(OpenRouteServiceException.class);
        assertThat(result).isNotInstanceOf(OpenRouteServiceUnavailableException.class);
        assertThat(result).isNotInstanceOf(OpenRouteServiceQuotaExceededException.class);
        assertThat(result.getMessage()).contains("HTTP 400");
    }

    @Test
    void decode_404_returnsBaseException() {
        Exception result = decoder.decode("methodKey", buildResponse(404));

        assertThat(result).isInstanceOf(OpenRouteServiceException.class);
        assertThat(result).isNotInstanceOf(OpenRouteServiceUnavailableException.class);
    }
}
