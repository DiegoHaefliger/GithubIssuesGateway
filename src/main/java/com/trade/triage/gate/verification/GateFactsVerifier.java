package com.trade.triage.gate.verification;

import com.trade.triage.board.github.GitHubCloneUrls;
import com.trade.triage.gate.GateFacts;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.registry.PathPolicy;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.shared.control.IncidentWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class GateFactsVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(GateFactsVerifier.class);

    private static final List<Pattern> AREAS_PROIBIDAS = List.of(
            Pattern.compile("(?i).*/(db|database)/migration/.*"),
            Pattern.compile("(?i).*/flyway/.*"),
            Pattern.compile("(?i).*/liquibase/.*"),
            Pattern.compile("(?i).*\\.(tf|tfvars)$"),
            Pattern.compile("(?i).*/(helm|k8s|kubernetes)/.*"),
            Pattern.compile("(?i).*(dockerfile|docker-compose\\.ya?ml)$"),
            Pattern.compile("(?i).*/security/.*"),
            Pattern.compile("(?i).*(pom\\.xml|build\\.gradle(\\.kts)?|package(-lock)?\\.json|requirements\\.txt)$"),
            Pattern.compile("(?i).*\\.github/workflows/.*"));

    private final DiffAnalyzer diffAnalyzer;
    private final PathPolicy pathPolicy;
    private final CommandRunner commandRunner;
    private final IncidentWindow incidentWindow;
    private final VerificationProperties properties;
    private final GitHubCloneUrls cloneUrls;

    public GateFactsVerifier(DiffAnalyzer diffAnalyzer, PathPolicy pathPolicy, CommandRunner commandRunner,
                             IncidentWindow incidentWindow, VerificationProperties properties,
                             GitHubCloneUrls cloneUrls) {
        this.diffAnalyzer = diffAnalyzer;
        this.pathPolicy = pathPolicy;
        this.commandRunner = commandRunner;
        this.incidentWindow = incidentWindow;
        this.properties = properties;
        this.cloneUrls = cloneUrls;
    }

    public GateFacts verificar(TriageResult resultado, ProjectEntry projeto, String severidade, int autoAttempts) {
        List<FileDiff> arquivos = diffAnalyzer.analisar(resultado.diff());
        List<String> caminhos = arquivos.stream().map(FileDiff::caminho).toList();
        TestOutcome testes = resultado.temProposta()
                ? executarVerificacao(resultado, projeto, arquivos)
                : TestOutcome.semProposta();

        return new GateFacts(
                caminhos,
                arquivos.stream().mapToInt(FileDiff::linhasAlteradas).sum(),
                testes.testeReproduzOErro(),
                testes.suiteCompletaPassa(),
                !caminhos.isEmpty() && caminhos.stream().allMatch(caminho -> pathPolicy.dentroDaAllowlist(projeto, caminho)),
                caminhos.stream().anyMatch(this::areaProibida),
                blastRadiusMaisSevero(projeto, caminhos),
                severidade,
                incidentWindow.incidenteAtivo(),
                autoAttempts);
    }

    private TestOutcome executarVerificacao(TriageResult resultado, ProjectEntry projeto, List<FileDiff> arquivos) {
        String caminhoDoTeste = resultado.testeNovo().arquivo();
        String diffDoTeste = concatenar(arquivos.stream()
                .filter(arquivo -> arquivo.caminho().equals(caminhoDoTeste))
                .toList());
        String diffDaCorrecao = concatenar(arquivos.stream()
                .filter(arquivo -> !arquivo.caminho().equals(caminhoDoTeste))
                .toList());
        if (diffDoTeste.isBlank() || diffDaCorrecao.isBlank()) {
            LOG.warn("diff nao separa teste e correcao card={}", resultado.cardRef());
            return new TestOutcome(false, false);
        }

        try (ProjectWorkspace worktree = ProjectWorkspace.clonar(
                projeto, cloneUrls.de(projeto), Path.of(properties.diretorioDeTrabalho()), commandRunner)) {
            if (!worktree.aplicar(diffDoTeste)) {
                return new TestOutcome(false, false);
            }
            boolean vermelhoSemCorrecao = !worktree
                    .rodarSuite(projeto.comandoDeTeste(), properties.timeoutDaSuite()).sucesso();
            if (!worktree.aplicar(diffDaCorrecao)) {
                return new TestOutcome(vermelhoSemCorrecao, false);
            }
            boolean verdeComCorrecao = worktree
                    .rodarSuite(projeto.comandoDeTeste(), properties.timeoutDaSuite()).sucesso();
            return new TestOutcome(vermelhoSemCorrecao && verdeComCorrecao, verdeComCorrecao);
        }
    }

    private BlastRadius blastRadiusMaisSevero(ProjectEntry projeto, List<String> caminhos) {
        return caminhos.stream()
                .map(caminho -> pathPolicy.blastRadiusDe(projeto, caminho))
                .max(java.util.Comparator.comparingInt(Enum::ordinal))
                .orElse(BlastRadius.CRITICO);
    }

    private boolean areaProibida(String caminho) {
        return AREAS_PROIBIDAS.stream().anyMatch(padrao -> padrao.matcher(caminho).matches());
    }

    private String concatenar(List<FileDiff> arquivos) {
        return arquivos.stream().map(FileDiff::texto).reduce("", String::concat);
    }

    private record TestOutcome(boolean testeReproduzOErro, boolean suiteCompletaPassa) {

        private static TestOutcome semProposta() {
            return new TestOutcome(false, false);
        }
    }
}
