package com.trade.triage.shared.control;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage.kill-switch")
public record KillSwitchProperties(String arquivo, boolean desligadoPorPadrao) {

    public KillSwitchProperties {
        arquivo = arquivo == null || arquivo.isBlank() ? "config/PARAR_TRIAGEM" : arquivo;
    }
}
