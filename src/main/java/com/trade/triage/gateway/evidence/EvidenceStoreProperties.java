package com.trade.triage.gateway.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage.evidencia")
public record EvidenceStoreProperties(String diretorio) {

    public EvidenceStoreProperties {
        diretorio = diretorio == null || diretorio.isBlank() ? "var/evidencia" : diretorio;
    }
}
