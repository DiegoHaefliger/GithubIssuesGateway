package com.trade.triage.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.triage.registry.source.FileSystemRegistrySource;
import com.trade.triage.registry.source.HttpRegistrySource;
import com.trade.triage.registry.source.RegistrySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;

@Configuration
public class RegistryConfiguration {

    @Bean
    public RegistrySource registrySource(RegistryProperties properties) {
        return switch (properties.origem()) {
            case HTTP -> new HttpRegistrySource(
                    HttpClient.newBuilder().connectTimeout(properties.timeout()).build(),
                    URI.create(properties.url()),
                    properties.timeout());
            case ARQUIVO -> new FileSystemRegistrySource(Path.of(properties.arquivo()));
        };
    }

    @Bean
    public ProjectRegistry projectRegistry(RegistrySource source,
                                           ObjectMapper objectMapper,
                                           RegistryValidator validator) {
        CachedProjectRegistry registry = new CachedProjectRegistry(source, objectMapper, validator);
        registry.reload();
        return registry;
    }
}
