package com.personalrouter.client;

import com.personalrouter.config.OpenRouteServiceProperties;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class OpenRouteServiceClientConfiguration {

    @Bean
    public Request.Options orsOptions(OpenRouteServiceProperties p) {
        return new Request.Options(
                p.getConnectTimeoutMs(), MILLISECONDS,
                p.getReadTimeoutMs(), MILLISECONDS,
                true
        );
    }

    @Bean
    public Retryer orsRetryer(OpenRouteServiceProperties p) {
        return new Retryer.Default(
                p.getRetry().getPeriodMs(),
                p.getRetry().getMaxPeriodMs(),
                p.getRetry().getMaxAttempts()
        );
    }

    @Bean
    public RequestInterceptor orsAuth(OpenRouteServiceProperties p) {
        return requestTemplate -> requestTemplate.header("Authorization", p.getKey());
    }

    @Bean
    public Logger.Level orsLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public ErrorDecoder orsErrorDecoder() {
        return new OpenRouteServiceErrorDecoder();
    }
}
