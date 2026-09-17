package com.trade.triage.gate;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage.gate")
public record GateProperties(boolean autoFixHabilitado) {
}
