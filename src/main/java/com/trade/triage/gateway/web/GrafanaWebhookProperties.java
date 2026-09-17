package com.trade.triage.gateway.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage.grafana")
public record GrafanaWebhookProperties(String webhookToken, String webhookUsuario) {

    public GrafanaWebhookProperties {
        webhookUsuario = webhookUsuario == null || webhookUsuario.isBlank() ? "grafana" : webhookUsuario;
    }
}
