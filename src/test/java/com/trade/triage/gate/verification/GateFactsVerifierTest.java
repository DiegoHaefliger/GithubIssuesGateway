package com.trade.triage.gate.verification;

import com.trade.triage.gate.GateFacts;
import com.trade.triage.orchestrator.result.ProposedTest;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.registry.PathPolicy;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import com.trade.triage.shared.control.IncidentWindow;
import com.trade.triage.shared.control.IncidentWindowProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GateFactsVerifierTest {

    private static final String DIFF_SOMENTE_FONTE = """
            diff --git a/src/main/java/com/trade/parser/Candle.java b/src/main/java/com/trade/parser/Candle.java
            --- a/src/main/java/com/trade/parser/Candle.java
            +++ b/src/main/java/com/trade/parser/Candle.java
            @@ -1,1 +1,1 @@
            -a
            +b
            """;

    private static final String DIFF_COM_MIGRACAO = """
            diff --git a/src/main/resources/db/migration/V2__x.sql b/src/main/resources/db/migration/V2__x.sql
            --- a/src/main/resources/db/migration/V2__x.sql
            +++ b/src/main/resources/db/migration/V2__x.sql
            @@ -0,0 +1,1 @@
            +alter table posicao add column x int;
            """;

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master",
            List.of("mvn", "test"), List.of("producao"), "acme/trade",
            List.of("src/main/java/**/parser/**"),
            Map.of("src/main/java/**/parser/**", BlastRadius.BAIXO,
                    "src/main/resources/db/**", BlastRadius.CRITICO),
            new ProjectLimits(50, 3, 10, 10), true);

    @Mock
    private CommandRunner commandRunner;

    @Test
    void semDiffNaoRodaNadaEOsTestesSaoFatoNegativo() {
        GateFacts fatos = verifier().verificar(resultado(null, null), projeto, "warning", 0);

        assertThat(fatos.testeReproduzOErro()).isFalse();
        assertThat(fatos.suiteCompletaPassa()).isFalse();
        assertThat(fatos.arquivosDoDiff()).isZero();
        verifyNoInteractions(commandRunner);
    }

    @Test
    void semTesteNovoNaoExecutaSuite() {
        GateFacts fatos = verifier().verificar(resultado(DIFF_SOMENTE_FONTE, null), projeto, "warning", 0);

        assertThat(fatos.testeReproduzOErro()).isFalse();
        verifyNoInteractions(commandRunner);
    }

    @Test
    void medeArquivosELinhasDoDiff() {
        GateFacts fatos = verifier().verificar(resultado(DIFF_SOMENTE_FONTE, null), projeto, "warning", 0);

        assertThat(fatos.arquivosTocados()).containsExactly("src/main/java/com/trade/parser/Candle.java");
        assertThat(fatos.linhasDoDiff()).isEqualTo(2);
    }

    @Test
    void reconheceCaminhoDentroDaAllowlist() {
        GateFacts fatos = verifier().verificar(resultado(DIFF_SOMENTE_FONTE, null), projeto, "warning", 0);

        assertThat(fatos.todosNaAllowlist()).isTrue();
        assertThat(fatos.blastRadius()).isEqualTo(BlastRadius.BAIXO);
    }

    @Test
    void migracaoDeBancoEhAreaProibidaEBlastRadiusCritico() {
        GateFacts fatos = verifier().verificar(resultado(DIFF_COM_MIGRACAO, null), projeto, "warning", 0);

        assertThat(fatos.tocaAreaProibida()).isTrue();
        assertThat(fatos.todosNaAllowlist()).isFalse();
        assertThat(fatos.blastRadius()).isEqualTo(BlastRadius.CRITICO);
    }

    @Test
    void propagaSeveridadeEAutoAttempts() {
        GateFacts fatos = verifier().verificar(resultado(DIFF_SOMENTE_FONTE, null), projeto, "critical", 2);

        assertThat(fatos.severidade()).isEqualTo("critical");
        assertThat(fatos.autoAttempts()).isEqualTo(2);
    }

    @Test
    void incidenteAtivoEntraNosFatos() {
        GateFactsVerifier comIncidente = new GateFactsVerifier(new DiffAnalyzer(), new PathPolicy(), commandRunner,
                new IncidentWindow(new IncidentWindowProperties("pom.xml")),
                new VerificationProperties("build/worktrees", Duration.ofMinutes(1)));

        assertThat(comIncidente.verificar(resultado(DIFF_SOMENTE_FONTE, null), projeto, "warning", 0)
                .incidenteAtivo()).isTrue();
    }

    private GateFactsVerifier verifier() {
        return new GateFactsVerifier(new DiffAnalyzer(), new PathPolicy(), commandRunner,
                new IncidentWindow(new IncidentWindowProperties("build/nao-existe")),
                new VerificationProperties("build/worktrees", Duration.ofMinutes(1)));
    }

    private TriageResult resultado(String diff, ProposedTest teste) {
        return new TriageResult("job-1", "acme/trade#123", "hipotese", List.of("a favor"), List.of("contra"),
                diff, teste, "justificativa", null);
    }
}
