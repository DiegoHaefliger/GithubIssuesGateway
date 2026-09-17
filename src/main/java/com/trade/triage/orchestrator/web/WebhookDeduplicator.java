package com.trade.triage.orchestrator.web;

import com.trade.triage.persistence.entity.WebhookDeliveryEntity;
import com.trade.triage.persistence.repository.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class WebhookDeduplicator {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDeduplicator.class);

    private final WebhookDeliveryRepository repository;
    private final Clock clock;

    public WebhookDeduplicator(WebhookDeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean primeiraEntrega(String deliveryId, String evento) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return true;
        }
        if (repository.existsById(deliveryId)) {
            LOG.info("entrega repetida descartada delivery={} evento={}", deliveryId, evento);
            return false;
        }
        try {
            repository.saveAndFlush(new WebhookDeliveryEntity(deliveryId, evento, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException exception) {
            LOG.info("entrega concorrente descartada delivery={} evento={}", deliveryId, evento);
            return false;
        }
    }
}
