package com.trade.triage.gate;

import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiPredicate;

@Component
public class DecisionTablePolicyGate implements PolicyGate {

    private static final String SEVERIDADE_CRITICA = "critical";

    private final List<Clausula> clausulas = List.of(
            new Clausula("1", Decision.HUMAN, "incidente ativo declarado",
                    (fatos, projeto) -> fatos.incidenteAtivo()),
            new Clausula("2", Decision.HUMAN, "ja houve tentativa automatica para este fingerprint",
                    (fatos, projeto) -> fatos.autoAttempts() >= 1),
            new Clausula("3", Decision.HUMAN, "diff toca area de blast radius CRITICO",
                    (fatos, projeto) -> fatos.blastRadius() == BlastRadius.CRITICO),
            new Clausula("4", Decision.HUMAN, "diff toca arquivo fora da allowlist",
                    (fatos, projeto) -> !fatos.todosNaAllowlist()),
            new Clausula("5", Decision.HUMAN, "diff toca migracao, seguranca, IaC ou dependencia",
                    (fatos, projeto) -> fatos.tocaAreaProibida()),
            new Clausula("6", Decision.HUMAN, "nao existe teste que reproduza o erro",
                    (fatos, projeto) -> !fatos.testeReproduzOErro()),
            new Clausula("7", Decision.HUMAN, "suite completa nao passa",
                    (fatos, projeto) -> !fatos.suiteCompletaPassa()),
            new Clausula("8", Decision.PROPOSE_PATCH, "diff acima do limite do projeto",
                    this::diffAcimaDoLimite),
            new Clausula("9", Decision.PROPOSE_PATCH, "severidade critica",
                    (fatos, projeto) -> SEVERIDADE_CRITICA.equalsIgnoreCase(fatos.severidade())),
            new Clausula("10", Decision.AUTO_FIX, "allowlist, teste vermelho para verde e diff pequeno",
                    (fatos, projeto) -> true));

    private final GateProperties properties;

    public DecisionTablePolicyGate(GateProperties properties) {
        this.properties = properties;
    }

    @Override
    public GateDecision decidir(GateFacts fatos, ProjectEntry projeto) {
        Clausula decisora = clausulas.stream()
                .filter(clausula -> clausula.casa(fatos, projeto))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tabela de decisao sem clausula final"));
        return construir(decisora);
    }

    private GateDecision construir(Clausula clausula) {
        if (clausula.resultado() == Decision.AUTO_FIX && !properties.autoFixHabilitado()) {
            return new GateDecision(Decision.PROPOSE_PATCH, "clausula " + clausula.numero(),
                    clausula.explicacao() + ", mas AUTO_FIX esta desligado na postura de adocao");
        }
        return new GateDecision(clausula.resultado(), "clausula " + clausula.numero(), clausula.explicacao());
    }

    private boolean diffAcimaDoLimite(GateFacts fatos, ProjectEntry projeto) {
        return fatos.linhasDoDiff() > projeto.limites().diffMaxLinhas()
                || fatos.arquivosDoDiff() > projeto.limites().diffMaxArquivos();
    }

    private record Clausula(String numero, Decision resultado, String explicacao,
                            BiPredicate<GateFacts, ProjectEntry> condicao) {

        boolean casa(GateFacts fatos, ProjectEntry projeto) {
            return condicao.test(fatos, projeto);
        }
    }
}
