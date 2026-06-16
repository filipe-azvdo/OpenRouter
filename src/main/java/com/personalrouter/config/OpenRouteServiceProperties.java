package com.personalrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("ors.api")
public class OpenRouteServiceProperties {

    private String baseUrl;
    private String key;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private Retry retry = new Retry();

    public static class Retry {
        private int maxAttempts;
        private long periodMs;
        private long maxPeriodMs;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getPeriodMs() { return periodMs; }
        public void setPeriodMs(long periodMs) { this.periodMs = periodMs; }
        public long getMaxPeriodMs() { return maxPeriodMs; }
        public void setMaxPeriodMs(long maxPeriodMs) { this.maxPeriodMs = maxPeriodMs; }
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
}
