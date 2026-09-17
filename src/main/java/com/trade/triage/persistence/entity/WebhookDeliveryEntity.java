package com.trade.triage.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhook_delivery")
public class WebhookDeliveryEntity {

    @Id
    @Column(name = "delivery_id", length = 128, nullable = false)
    private String deliveryId;

    @Column(name = "evento", nullable = false, length = 64)
    private String evento;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    protected WebhookDeliveryEntity() {
    }

    public WebhookDeliveryEntity(String deliveryId, String evento, Instant recebidoEm) {
        this.deliveryId = deliveryId;
        this.evento = evento;
        this.recebidoEm = recebidoEm;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEvento() {
        return evento;
    }

    public Instant getRecebidoEm() {
        return recebidoEm;
    }
}
