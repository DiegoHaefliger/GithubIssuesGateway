package com.trade.triage.gate.verification;

import com.trade.triage.board.github.GitHubCloneUrls;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Mock
    private GitHubCloneUrls cloneUrls;

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
                new VerificationProperties("build/worktrees", Duration.ofMinutes(1)), cloneUrls);

        assertThat(comIncidente.verificar(resultado(DIFF_SOMENTE_FONTE, null), projeto, "warning", 0)
                .incidenteAtivo()).isTrue();
    }

    @Test
    void suiteVermelhaSemCorrecaoEVerdeComCorrecaoReproduzEPassa() {
        prepararSuite(new CommandResult(1, "FAIL"), new CommandResult(0, "OK"));

        GateFacts fatos = verifier().verificar(resultadoComTeste(), projeto, "warning", 0);

        assertThat(fatos.testeReproduzOErro()).isTrue();
        assertThat(fatos.suiteCompletaPassa()).isTrue();
    }

    @Test
    void suiteQueFalhaComCorrecaoAindaReproduzMasNaoPassa() {
        prepararSuite(new CommandResult(1, "FAIL"), new CommandResult(1, "FAIL"));

        GateFacts fatos = verifier().verificar(resultadoComTeste(), projeto, "warning", 0);

        assertThat(fatos.testeReproduzOErro()).isTrue();
        assertThat(fatos.suiteCompletaPassa()).isFalse();
    }

    @Test
    void suiteVerdeSemCorrecaoNaoReproduz() {
        prepararSuite(new CommandResult(0, "OK"), new CommandResult(0, "OK"));

        GateFacts fatos = verifier().verificar(resultadoComTeste(), projeto, "warning", 0);

        assertThat(fatos.testeReproduzOErro()).isFalse();
    }

    private void prepararSuite(CommandResult semCorrecao, CommandResult comCorrecao) {
        when(cloneUrls.de(projeto)).thenReturn("https://example.invalid/acme/trade.git");
        when(commandRunner.run(anyList(), any(), any())).thenAnswer(new Answer<CommandResult>() {
            private final Iterator<CommandResult> suites = List.of(semCorrecao, comCorrecao).iterator();

            @Override
            public CommandResult answer(InvocationOnMock invocacao) {
                List<String> comando = invocacao.getArgument(0);
                return "git".equals(comando.get(0)) ? new CommandResult(0, "") : suites.next();
            }
        });
    }

    private TriageResult resultadoComTeste() {
        String diff = DIFF_SOMENTE_FONTE + """
                diff --git a/src/test/java/com/trade/parser/CandleTest.java b/src/test/java/com/trade/parser/CandleTest.java
                --- a/src/test/java/com/trade/parser/CandleTest.java
                +++ b/src/test/java/com/trade/parser/CandleTest.java
                @@ -1,1 +1,1 @@
                -a
                +b
                """;
        return resultado(diff, new ProposedTest("src/test/java/com/trade/parser/CandleTest.java", "x"));
    }

    private GateFactsVerifier verifier() {
        return new GateFactsVerifier(new DiffAnalyzer(), new PathPolicy(), commandRunner,
                new IncidentWindow(new IncidentWindowProperties("build/nao-existe")),
                new VerificationProperties("build/worktrees", Duration.ofMinutes(1)), cloneUrls);
    }

    private TriageResult resultado(String diff, ProposedTest teste) {
        return new TriageResult("job-1", "acme/trade#123", "hipotese", List.of("a favor"), List.of("contra"),
                diff, teste, "justificativa", null);
    }
}
