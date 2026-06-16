package com.personalrouter.client;

import com.personalrouter.exception.OpenRouteServiceException;
import com.personalrouter.exception.OpenRouteServiceQuotaExceededException;
import com.personalrouter.exception.OpenRouteServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenRouteServiceErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.warn("ORS retornou status {} em {}", status, methodKey);
        // body não lido intencionalmente — evita vazar internals do ORS; Feign fecha o response
        if (status == 429) {
            return new OpenRouteServiceQuotaExceededException("Cota do OpenRouteService excedida");
        }
        if (status >= 500) {
            return new OpenRouteServiceUnavailableException("OpenRouteService indisponível (HTTP " + status + ")");
        }
        return new OpenRouteServiceException("Falha na requisição ao OpenRouteService (HTTP " + status + ")");
    }
}
