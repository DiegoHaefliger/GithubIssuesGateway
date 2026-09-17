package com.trade.triage.orchestrator.web;

import com.trade.triage.persistence.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({WebhookDeduplicator.class, WebhookDeliveryCleaner.class})
class WebhookDeduplicatorTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    @Autowired
    private WebhookDeduplicator deduplicator;

    @Autowired
    private WebhookDeliveryCleaner cleaner;

    @Autowired
    private WebhookDeliveryRepository repository;

    @MockitoBean
    private Clock clock;

    @Test
    void primeiraEntregaPassa() {
        comRelogioEm(AGORA);

        assertThat(deduplicator.primeiraEntrega("entrega-1", "issues")).isTrue();
    }

    @Test
    void segundaEntregaComMesmoIdEhDescartada() {
        comRelogioEm(AGORA);
        deduplicator.primeiraEntrega("entrega-1", "issues");

        assertThat(deduplicator.primeiraEntrega("entrega-1", "issues")).isFalse();
    }

    @Test
    void entregasDiferentesPassam() {
        comRelogioEm(AGORA);

        assertThat(deduplicator.primeiraEntrega("entrega-1", "issues")).isTrue();
        assertThat(deduplicator.primeiraEntrega("entrega-2", "issues")).isTrue();
    }

    @Test
    void semIdDeEntregaNaoBloqueia() {
        assertThat(deduplicator.primeiraEntrega(null, "issues")).isTrue();
        assertThat(deduplicator.primeiraEntrega("  ", "issues")).isTrue();
        assertThat(repository.count()).isZero();
    }

    @Test
    void limpezaRemoveEntregasAntigasEMantemRecentes() {
        comRelogioEm(AGORA.minus(Duration.ofDays(30)));
        deduplicator.primeiraEntrega("antiga", "issues");
        comRelogioEm(AGORA);
        deduplicator.primeiraEntrega("recente", "issues");

        cleaner.limparEntregasAntigas();

        assertThat(repository.existsById("antiga")).isFalse();
        assertThat(repository.existsById("recente")).isTrue();
    }

    private void comRelogioEm(Instant momento) {
        org.mockito.Mockito.when(clock.instant()).thenReturn(momento);
        org.mockito.Mockito.lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }
}
