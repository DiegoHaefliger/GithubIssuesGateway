package com.trade.triage.orchestrator.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "triage.orquestrador")
public record OrchestratorProperties(
        int maximoDeJobsSimultaneos,
        Duration prazoDoJob,
        String urlPublica) {

    public OrchestratorProperties {
        maximoDeJobsSimultaneos = maximoDeJobsSimultaneos <= 0 ? 2 : maximoDeJobsSimultaneos;
        prazoDoJob = prazoDoJob == null ? Duration.ofMinutes(30) : prazoDoJob;
        urlPublica = urlPublica == null ? "" : urlPublica;
    }
}
