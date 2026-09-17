package com.trade.triage.gateway.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GrafanaWebhookRequest(
        @JsonProperty("status") String status,
        @JsonProperty("alerts") @NotEmpty List<GrafanaAlert> alerts) {

    public GrafanaWebhookRequest {
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
    }
}
