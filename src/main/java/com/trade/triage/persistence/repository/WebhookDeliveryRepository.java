package com.trade.triage.persistence.repository;

import com.trade.triage.persistence.entity.WebhookDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, String> {

    long deleteByRecebidoEmBefore(Instant limite);
}
