package com.trade.triage.gateway.service;

import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class CardRateLimiter {

    private static final Duration JANELA = Duration.ofHours(1);
    private static final String PADRAO_DE_TEMPESTADE = "storm-%";

    private final FingerprintRepository repository;
    private final Clock clock;

    public CardRateLimiter(FingerprintRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean permiteNovoCard(ProjectEntry projeto) {
        return cardsNaJanela(projeto) < projeto.limites().cardsPorHora();
    }

    public boolean estourouTeto(ProjectEntry projeto) {
        return cardsNaJanela(projeto) >= projeto.limites().cardsPorHora();
    }

    private long cardsNaJanela(ProjectEntry projeto) {
        return repository.countByProjetoAndFirstSeenAfterAndFingerprintNotLike(
                projeto.projeto(), clock.instant().minus(JANELA), PADRAO_DE_TEMPESTADE);
    }
}
