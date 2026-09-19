package com.trade.triage.gateway.service;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.gateway.evidence.EvidenceCollector;
import com.trade.triage.gateway.evidence.EvidencePackage;
import com.trade.triage.gateway.evidence.EvidenceStore;
import com.trade.triage.gateway.fingerprint.FingerprintCalculator;
import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.gateway.web.GrafanaAlert;
import com.trade.triage.gateway.web.GrafanaWebhookRequest;
import com.trade.triage.metrics.TriageMetrics;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.shared.control.IncidentWindow;
import com.trade.triage.shared.control.KillSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultAlertIngestService implements AlertIngestService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAlertIngestService.class);
    private static final String LABEL_REGRESSAO = "regressao";

    private final ProjectRegistry registry;
    private final FingerprintCalculator fingerprintCalculator;
    private final ErrorSignalExtractor extractor;
    private final ErrorSignalEnricher enricher;
    private final EvidenceCollector evidenceCollector;
    private final EvidenceStore evidenceStore;
    private final CardContentBuilder cardBuilder;
    private final CardRateLimiter rateLimiter;
    private final FingerprintRepository repository;
    private final BoardClient board;
    private final KillSwitch killSwitch;
    private final IncidentWindow incidentWindow;
    private final TriageMetrics metrics;
    private final Clock clock;

    public DefaultAlertIngestService(ProjectRegistry registry,
                                     FingerprintCalculator fingerprintCalculator,
                                     ErrorSignalExtractor extractor,
                                     ErrorSignalEnricher enricher,
                                     EvidenceCollector evidenceCollector,
                                     EvidenceStore evidenceStore,
                                     CardContentBuilder cardBuilder,
                                     CardRateLimiter rateLimiter,
                                     FingerprintRepository repository,
                                     BoardClient board,
                                     KillSwitch killSwitch,
                                     IncidentWindow incidentWindow,
                                     TriageMetrics metrics,
                                     Clock clock) {
        this.registry = registry;
        this.fingerprintCalculator = fingerprintCalculator;
        this.extractor = extractor;
        this.enricher = enricher;
        this.evidenceCollector = evidenceCollector;
        this.evidenceStore = evidenceStore;
        this.cardBuilder = cardBuilder;
        this.rateLimiter = rateLimiter;
        this.repository = repository;
        this.board = board;
        this.killSwitch = killSwitch;
        this.incidentWindow = incidentWindow;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<IngestResult> ingest(GrafanaWebhookRequest request) {
        if (killSwitch.acionado()) {
            return List.of(IngestResult.de(IngestOutcome.SUPRIMIDO_POR_KILL_SWITCH, killSwitch.motivo()));
        }
        if (incidentWindow.incidenteAtivo()) {
            return List.of(IngestResult.de(IngestOutcome.SUPRIMIDO_POR_INCIDENTE, "incidente declarado"));
        }
        return request.alerts().stream().map(this::processar).toList();
    }

    private IngestResult processar(GrafanaAlert alerta) {
        if (!alerta.firing()) {
            return IngestResult.de(IngestOutcome.IGNORADO, "alerta com status " + alerta.status());
        }
        ErrorSignal signal = extractor.extract(alerta);
        if (signal.service() == null || signal.service().isBlank()) {
            return IngestResult.de(IngestOutcome.LOG_ORFAO, "alerta sem label service");
        }
        Optional<ProjectEntry> projeto = registry.findByServiceAndEnv(signal.service(), signal.env());
        if (projeto.isEmpty()) {
            LOG.warn("log orfao service={} env={} registro_versao={}",
                    signal.service(), signal.env(), registry.version());
            metrics.contarLogOrfao(signal.service());
            return IngestResult.de(IngestOutcome.LOG_ORFAO,
                    "service " + signal.service() + " fora do registro de escopo");
        }
        return processar(enricher.enriquecer(signal, projeto.get()), projeto.get());
    }

    private IngestResult processar(ErrorSignal signal, ProjectEntry projeto) {
        String fingerprint = fingerprintCalculator.calculate(signal, projeto);
        Optional<FingerprintEntity> conhecido = repository.findById(fingerprint);
        if (conhecido.isPresent()) {
            return registrarRecorrencia(conhecido.get(), signal);
        }
        if (rateLimiter.estourouTeto(projeto)) {
            LOG.warn("teto de cards por hora estourado projeto={} fingerprint={}", projeto.projeto(), fingerprint);
            return agregarNaTempestade(signal, projeto);
        }
        return abrirCard(signal, projeto, fingerprint);
    }

    private IngestResult registrarRecorrencia(FingerprintEntity estado, ErrorSignal signal) {
        estado.registrarOcorrencia(signal.occurredAt());
        boolean regressao = estado.getState() == FingerprintState.RESOLVIDO;
        if (regressao) {
            estado.mudarEstado(FingerprintState.TRIADO);
        }
        repository.save(estado);

        if (estado.getCardRef() == null) {
            return IngestResult.comCard(IngestOutcome.DEDUPLICADO, estado.getFingerprint(), null);
        }
        CardRef card = CardRef.parse(estado.getCardRef());
        if (regressao) {
            board.reabrir(card);
            board.aplicarLabel(card, LABEL_REGRESSAO);
            board.comentar(card, cardBuilder.comentarioDeRegressao(estado));
            return IngestResult.comCard(IngestOutcome.REGRESSAO_REABERTA, estado.getFingerprint(), card.asString());
        }
        board.comentar(card, cardBuilder.comentarioDeRecorrencia(estado));
        return IngestResult.comCard(IngestOutcome.DEDUPLICADO, estado.getFingerprint(), card.asString());
    }

    private IngestResult agregarNaTempestade(ErrorSignal signal, ProjectEntry projeto) {
        String janela = StormWindow.identificadorDe(projeto.projeto(), momento(signal));
        Optional<FingerprintEntity> aberta = repository.findById(janela);
        if (aberta.isPresent()) {
            FingerprintEntity estado = aberta.get();
            estado.registrarOcorrencia(momento(signal));
            repository.save(estado);
            if (estado.getCardRef() != null) {
                board.comentar(CardRef.parse(estado.getCardRef()),
                        cardBuilder.comentarioDeTempestade(estado, signal));
            }
            return IngestResult.comCard(IngestOutcome.STORM, janela, estado.getCardRef());
        }

        FingerprintEntity estado = new FingerprintEntity(janela, signal.service(), signal.env(),
                projeto.projeto(), signal.ruleId(), signal.severity(), momento(signal));
        CardRef card = board.criarCard(projeto.board(),
                cardBuilder.storm(signal, estado, projeto.limites().cardsPorHora()));
        estado.vincularCard(card.asString());
        estado.mudarEstado(FingerprintState.AGUARDANDO_HUMANO);
        repository.save(estado);

        metrics.contarTempestade(projeto.projeto());
        LOG.warn("card de tempestade aberto projeto={} janela={} card={}",
                projeto.projeto(), janela, card.asString());
        return IngestResult.comCard(IngestOutcome.STORM, janela, card.asString());
    }

    private IngestResult abrirCard(ErrorSignal signal, ProjectEntry projeto, String fingerprint) {
        FingerprintEntity estado = new FingerprintEntity(fingerprint, signal.service(), signal.env(),
                projeto.projeto(), signal.ruleId(), signal.severity(), momento(signal));

        EvidencePackage evidencia = evidenceCollector.collect(signal, projeto, fingerprint);
        String evidenciaUri = evidenceStore.store(evidencia);
        estado.vincularEvidencia(evidenciaUri);

        CardRef card = board.criarCard(projeto.board(),
                cardBuilder.build(signal, projeto, estado, evidencia, evidenciaUri));
        estado.vincularCard(card.asString());
        repository.save(estado);

        metrics.contarCardCriado(projeto.projeto());
        LOG.info("card criado projeto={} fingerprint={} card={}", projeto.projeto(), fingerprint, card.asString());
        return IngestResult.comCard(IngestOutcome.CARD_CRIADO, fingerprint, card.asString());
    }

    private java.time.Instant momento(ErrorSignal signal) {
        return signal.occurredAt() == null ? clock.instant() : signal.occurredAt();
    }
}
