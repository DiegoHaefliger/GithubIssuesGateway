package com.trade.triage.gateway.service;

import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardRateLimiterTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master", List.of("mvn", "test"),
            List.of("producao"), "acme/trade", List.of(), Map.of(),
            new ProjectLimits(50, 3, 2, 10), true);

    @Mock
    private FingerprintRepository repository;

    @Test
    void permiteAbaixoDoTeto() {
        when(repository.countByProjetoAndFirstSeenAfterAndFingerprintNotLike(eq("trade"), any(), eq("storm-%"))).thenReturn(1L);

        assertThat(limiter().permiteNovoCard(projeto)).isTrue();
        assertThat(limiter().estourouTeto(projeto)).isFalse();
    }

    @Test
    void bloqueiaNoTeto() {
        when(repository.countByProjetoAndFirstSeenAfterAndFingerprintNotLike(eq("trade"), any(), eq("storm-%"))).thenReturn(2L);

        assertThat(limiter().permiteNovoCard(projeto)).isFalse();
        assertThat(limiter().estourouTeto(projeto)).isTrue();
    }

    @Test
    void bloqueiaAcimaDoTeto() {
        when(repository.countByProjetoAndFirstSeenAfterAndFingerprintNotLike(eq("trade"), any(), eq("storm-%"))).thenReturn(90L);

        assertThat(limiter().estourouTeto(projeto)).isTrue();
    }

    private CardRateLimiter limiter() {
        return new CardRateLimiter(repository, Clock.fixed(AGORA, ZoneOffset.UTC));
    }
}
