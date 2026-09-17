package com.trade.triage.registry;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RegistryReloadScheduler {

    private final ProjectRegistry registry;

    public RegistryReloadScheduler(ProjectRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${triage.registry.recarga-intervalo:5m}")
    public void reload() {
        registry.reload();
    }
}
