package com.trade.triage.gate.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "triage.verificacao")
public record VerificationProperties(String diretorioDeTrabalho, Duration timeoutDaSuite) {

    public VerificationProperties {
        diretorioDeTrabalho = diretorioDeTrabalho == null || diretorioDeTrabalho.isBlank()
                ? "var/worktrees" : diretorioDeTrabalho;
        timeoutDaSuite = timeoutDaSuite == null ? Duration.ofMinutes(20) : timeoutDaSuite;
    }
}
