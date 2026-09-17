package com.trade.triage.orchestrator.job;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.gateway.service.CardContentBuilder;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "triage.orquestrador.polling-habilitado", havingValue = "true")
public class BoardPollingFallback {

    private static final Logger LOG = LoggerFactory.getLogger(BoardPollingFallback.class);
    private static final String EVENTO = "polling";

    private final ProjectRegistry registry;
    private final BoardClient board;
    private final TriageJobService jobService;

    public BoardPollingFallback(ProjectRegistry registry, BoardClient board, TriageJobService jobService) {
        this.registry = registry;
        this.board = board;
        this.jobService = jobService;
    }

    @Scheduled(fixedDelayString = "${triage.orquestrador.intervalo-do-polling:5m}")
    public void varrerCardsPendentes() {
        registry.snapshot().projetos().stream()
                .filter(ProjectEntry::ativo)
                .forEach(this::varrer);
    }

    private void varrer(ProjectEntry projeto) {
        try {
            for (CardRef card : board.cardsAbertosComLabel(projeto.board(), CardContentBuilder.LABEL_AUTO_TRIAGE)) {
                JobEnqueueResult resultado = jobService.enfileirar(card.asString(), EVENTO);
                if (resultado.resultado() == JobEnqueueOutcome.ENFILEIRADO) {
                    LOG.info("job enfileirado por polling card={} job={}", card.asString(), resultado.jobId());
                }
            }
        } catch (RuntimeException exception) {
            LOG.error("falha ao varrer cards do projeto projeto={} motivo={}",
                    projeto.projeto(), exception.getMessage());
        }
    }
}
