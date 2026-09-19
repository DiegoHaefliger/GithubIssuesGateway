package com.trade.triage.gateway.evidence.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

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

    @Bean
    public RestClient tempoRestClient(ObservabilityProperties properties) {
        return construir(properties.tempoUrl(), properties.timeout());
    }

    private RestClient construir(String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        // VALUES_ONLY: LogQL/PromQL sempre tem "{"/"}", que o modo padrao confunde com template
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl);
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .uriBuilderFactory(uriBuilderFactory)
                .requestFactory(requestFactory)
                .build();
    }
}
