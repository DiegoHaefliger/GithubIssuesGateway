package com.trade.triage.registry.source;

import com.trade.triage.registry.RegistryLoadException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpRegistrySource implements RegistrySource {

    private final HttpClient client;
    private final URI uri;
    private final Duration timeout;

    public HttpRegistrySource(HttpClient client, URI uri, Duration timeout) {
        this.client = client;
        this.uri = uri;
        this.timeout = timeout;
    }

    @Override
    public String fetchRawJson() {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RegistryLoadException("Registro respondeu HTTP " + response.statusCode() + " em " + uri);
            }
            return response.body();
        } catch (IOException exception) {
            throw new RegistryLoadException("Falha ao buscar registro em " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RegistryLoadException("Busca do registro interrompida em " + uri, exception);
        }
    }

    @Override
    public String describe() {
        return "http:" + uri;
    }
}
