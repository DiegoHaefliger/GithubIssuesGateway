package com.trade.triage.orchestrator.publish;

import com.trade.triage.gate.GateDecision;
import com.trade.triage.orchestrator.result.TriageResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalysisCommentBuilder {

    public String build(TriageResult resultado, GateDecision decisao) {
        return """
                ## Analise do agente

                **Hipotese**
                %s

                **Evidencia a favor**
                %s

                **Evidencia contra**
                %s

                **Por que parou aqui**
                %s (%s)

                %s

                **A pergunta que destrava**
                %s
                """.formatted(
                resultado.hipotese(),
                lista(resultado.evidenciaAFavor()),
                lista(resultado.evidenciaContra()),
                decisao.explicacao(),
                decisao.regraDecisora(),
                decisao.detalhe(),
                pergunta(resultado));
    }

    private String pergunta(TriageResult resultado) {
        return resultado.perguntaQueDestrava() == null || resultado.perguntaQueDestrava().isBlank()
                ? "_o agente nao formulou pergunta; a analise precisa de revisao humana_"
                : resultado.perguntaQueDestrava();
    }

    private String lista(List<String> itens) {
        return itens.isEmpty() ? "- _nenhuma_" : itens.stream()
                .map(item -> "- " + item)
                .reduce((esquerda, direita) -> esquerda + "\n" + direita)
                .orElse("- _nenhuma_");
    }
}
