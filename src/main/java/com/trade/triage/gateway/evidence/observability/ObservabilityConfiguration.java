package com.trade.triage.gateway.evidence.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ObservabilityConfiguration {

    @Bean
    public RestClient lokiRestClient(ObservabilityProperties properties) {
        return construir(properties.lokiUrl(), properties.timeout());
    }

    @Bean
    public RestClient prometheusRestClient(ObservabilityProperties properties) {
        return construir(properties.prometheusUrl(), properties.timeout());
    }

    private RestClient construir(String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
