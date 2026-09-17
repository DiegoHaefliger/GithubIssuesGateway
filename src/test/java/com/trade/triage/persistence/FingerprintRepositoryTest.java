package com.trade.triage.persistence;

import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.repository.FingerprintRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FingerprintRepositoryTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    @Autowired
    private FingerprintRepository repository;

    @Test
    void gravaEEncontraPorCard() {
        FingerprintEntity entity = novo("a3f9c2d1");
        entity.vincularCard("acme/trade#123");
        repository.save(entity);

        assertThat(repository.findByCardRef("acme/trade#123"))
                .get()
                .extracting(FingerprintEntity::getFingerprint)
                .isEqualTo("a3f9c2d1");
    }

    @Test
    void incrementaOcorrenciaSemCriarRegistroNovo() {
        repository.save(novo("a3f9c2d1"));

        FingerprintEntity existente = repository.findById("a3f9c2d1").orElseThrow();
        existente.registrarOcorrencia(AGORA.plus(Duration.ofMinutes(5)));
        repository.saveAndFlush(existente);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById("a3f9c2d1").orElseThrow().getOccurrenceCount()).isEqualTo(2);
        assertThat(repository.findById("a3f9c2d1").orElseThrow().getLastSeen())
                .isEqualTo(AGORA.plus(Duration.ofMinutes(5)));
    }

    @Test
    void contaCardsDoProjetoNaJanela() {
        repository.save(novo("aaaa"));
        repository.save(novo("bbbb"));

        assertThat(repository.countByProjetoAndFirstSeenAfter("trade", AGORA.minus(Duration.ofHours(1))))
                .isEqualTo(2);
        assertThat(repository.countByProjetoAndFirstSeenAfter("trade", AGORA.plus(Duration.ofHours(1))))
                .isZero();
    }

    @Test
    void listaFingerprintsAtivosDoProjeto() {
        repository.save(novo("aaaa"));
        FingerprintEntity resolvido = novo("bbbb");
        resolvido.mudarEstado(FingerprintState.RESOLVIDO);
        repository.save(resolvido);

        assertThat(repository.findByProjetoAndStateNot("trade", FingerprintState.RESOLVIDO))
                .extracting(FingerprintEntity::getFingerprint)
                .containsExactly("aaaa");
    }

    private FingerprintEntity novo(String fingerprint) {
        return new FingerprintEntity(fingerprint, "trade-backend", "producao", "trade", "regra-1", "critical", AGORA);
    }
}
