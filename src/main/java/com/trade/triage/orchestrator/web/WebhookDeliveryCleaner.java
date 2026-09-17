package com.trade.triage.orchestrator.web;

import com.trade.triage.persistence.repository.WebhookDeliveryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Component
public class WebhookDeliveryCleaner {

    private static final Duration RETENCAO = Duration.ofDays(7);

    private final WebhookDeliveryRepository repository;
    private final Clock clock;

    public WebhookDeliveryCleaner(WebhookDeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${triage.orquestrador.intervalo-da-limpeza:6h}")
    @Transactional
    public void limparEntregasAntigas() {
        repository.deleteByRecebidoEmBefore(clock.instant().minus(RETENCAO));
    }
}
