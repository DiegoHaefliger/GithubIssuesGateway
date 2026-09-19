package com.trade.triage.orchestrator.publish;

import com.trade.triage.gate.GateDecision;
import com.trade.triage.gate.GateFacts;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.persistence.entity.FingerprintEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PullRequestBodyBuilder {

    public String build(TriageResult resultado, GateDecision decisao, GateFacts fatos,
                        FingerprintEntity estado, String evidenciaUri) {
        return """
                ## Origem
                Card: %s · Fingerprint: `%s` · Servico: `%s` · %d ocorrencias desde %s

                ## Hipotese
                %s

                ## Mudanca
                %s

                ## Teste
                `%s`
                Falha antes do patch e passa depois. Verificado pelo gate, nao afirmado pelo agente.

                ## Evidencia contra a hipotese
                %s

                ## Decisao do gate
                %s — %s (%s). Blast radius: %s.

                %s

                ## Reverter
                `git revert <sha>`

                ---
                PR gerado por triagem automatizada. Evidencia completa: %s
                """.formatted(
                estado.getCardRef(),
                estado.getFingerprint(),
                estado.getService(),
                estado.getOccurrenceCount(),
                estado.getFirstSeen(),
                resultado.hipotese(),
                resultado.justificativa(),
                resultado.testeNovo().identificador(),
                lista(resultado.evidenciaContra()),
                decisao.decisao(),
                decisao.explicacao(),
                decisao.regraDecisora(),
                fatos.blastRadius(),
                decisao.detalhe(),
                textoOuAusente(evidenciaUri));
    }

    public String titulo(TriageResult resultado) {
        return "fix: " + resultado.justificativa().lines().findFirst().orElse(resultado.hipotese());
    }

    private String textoOuAusente(String evidenciaUri) {
        return evidenciaUri == null || evidenciaUri.isBlank()
                ? "_pacote de evidencia indisponivel_"
                : evidenciaUri;
    }

    private String lista(List<String> itens) {
        return itens.isEmpty() ? "_nenhuma — analise rasa, revise com atencao_" : itens.stream()
                .map(item -> "- " + item)
                .reduce((esquerda, direita) -> esquerda + "\n" + direita)
                .orElse("");
    }
}
