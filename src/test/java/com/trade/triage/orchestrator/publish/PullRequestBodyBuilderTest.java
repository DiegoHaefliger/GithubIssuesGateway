package com.trade.triage.orchestrator.publish;

import com.trade.triage.gate.GateDecision;
import com.trade.triage.gate.GateFacts;
import com.trade.triage.orchestrator.result.ProposedTest;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.registry.model.BlastRadius;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PullRequestBodyBuilderTest {

    private static final String JUSTIFICATIVA_LONGA = "Troca a chamada value.toLocalDateTime() por delegar o"
            + " OffsetDateTime direto para PythonDateTime.format(OffsetDateTime), que ja trata nulo e preserva o"
            + " offset. Isso alinha AssetMapper aos demais mappers.";

    private final PullRequestBodyBuilder builder = new PullRequestBodyBuilder();

    @Test
    void tituloLongoCortaEmPalavraSemPassarDeCemCaracteres() {
        String titulo = builder.titulo(resultado(JUSTIFICATIVA_LONGA));

        assertThat(titulo).hasSizeLessThanOrEqualTo(100).startsWith("fix: Troca a chamada").endsWith("…");
        assertThat(titulo).doesNotContain("Isso alinha");
    }

    @Test
    void tituloCurtoUsaSoAPrimeiraFrase() {
        assertThat(builder.titulo(resultado("Delega o offset ao formatador. Resto do texto.")))
                .isEqualTo("fix: Delega o offset ao formatador.");
    }

    @Test
    void corpoVinculaOCardComPalavraChaveDoGithub() {
        FingerprintEntity estado = new FingerprintEntity(
                "f1", "crypto-alerts-java", "producao", "trade", "r1", "critical", Instant.EPOCH);
        estado.vincularCard("acme/trade#105");
        GateFacts fatos = new GateFacts(List.of("A.java"), 2, true, true, true, false, BlastRadius.BAIXO,
                "critical", false, 0);

        String corpo = builder.build(resultado("x"),
                new GateDecision(Decision.PROPOSE_PATCH, "clausula 9", "severidade critica", "detalhe"),
                fatos, estado, null);

        assertThat(corpo).contains("Closes acme/trade#105");
    }

    private TriageResult resultado(String justificativa) {
        return new TriageResult("job-1", "acme/trade#105", "hipotese", List.of(), List.of(), "diff",
                new ProposedTest("ATest.java", "ATest.x"), justificativa, null);
    }
}
