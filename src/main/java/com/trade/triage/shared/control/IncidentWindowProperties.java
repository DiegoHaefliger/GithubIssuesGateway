package com.trade.triage.shared.control;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage.incidente")
public record IncidentWindowProperties(String arquivo) {

    public IncidentWindowProperties {
        arquivo = arquivo == null || arquivo.isBlank() ? "config/INCIDENTE_ATIVO" : arquivo;
    }
}
