package com.trade.triage.orchestrator.job;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import com.trade.triage.registry.model.ProjectRegistrySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardPollingFallbackTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    @Mock
    private ProjectRegistry registry;
    @Mock
    private BoardClient board;
    @Mock
    private TriageJobService jobService;

    private BoardPollingFallback fallback;

    @BeforeEach
    void setUp() {
        fallback = new BoardPollingFallback(registry, board, jobService);
    }

    @Test
    void enfileiraCardComLabelQueOWebhookPerdeu() {
        comProjetos(projeto(true));
        when(board.cardsAbertosComLabel("acme/trade", "auto-triage"))
                .thenReturn(List.of(new CardRef("acme/trade", 123)));
        when(jobService.enfileirar("acme/trade#123", "polling"))
                .thenReturn(JobEnqueueResult.enfileirado("job-1"));

        fallback.varrerCardsPendentes();

        verify(jobService).enfileirar("acme/trade#123", "polling");
    }

    @Test
    void cardQueJaTemJobAtivoNaoEhReenfileirado() {
        comProjetos(projeto(true));
        when(board.cardsAbertosComLabel("acme/trade", "auto-triage"))
                .thenReturn(List.of(new CardRef("acme/trade", 123)));
        when(jobService.enfileirar("acme/trade#123", "polling")).thenReturn(
                JobEnqueueResult.recusado(JobEnqueueOutcome.JA_EXISTE_JOB_ATIVO, "ja existe job ativo"));

        fallback.varrerCardsPendentes();

        verify(jobService).enfileirar("acme/trade#123", "polling");
    }

    @Test
    void projetoInativoNaoEhVarrido() {
        comProjetos(projeto(false));

        fallback.varrerCardsPendentes();

        verify(board, never()).cardsAbertosComLabel(anyString(), anyString());
    }

    @Test
    void falhaEmUmProjetoNaoDerrubaAVarredura() {
        comProjetos(projeto(true));
        when(board.cardsAbertosComLabel("acme/trade", "auto-triage"))
                .thenThrow(new IllegalStateException("GitHub fora do ar"));

        fallback.varrerCardsPendentes();

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    private void comProjetos(ProjectEntry... projetos) {
        when(registry.snapshot()).thenReturn(new ProjectRegistrySnapshot("v1", List.of(projetos), AGORA));
    }

    private ProjectEntry projeto(boolean ativo) {
        return new ProjectEntry("trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"),
                "acme/trade", "master", List.of("mvn", "test"), List.of("producao"), "acme/trade",
                List.of(), Map.of(), ProjectLimits.conservador(), ativo);
    }
}
