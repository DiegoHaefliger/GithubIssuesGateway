package com.trade.triage.orchestrator.publish;

import com.trade.triage.gate.GateDecision;
import com.trade.triage.gate.GateFacts;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.persistence.entity.FingerprintEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class PullRequestBodyBuilder {

    private static final int TAMANHO_MAXIMO_DO_TITULO = 100;
    private static final String PREFIXO_DO_TITULO = "fix: ";
    private static final String RETICENCIAS = "…";
    private static final Pattern FIM_DE_FRASE = Pattern.compile("(?<=[.!?])\\s");

    public String build(TriageResult resultado, GateDecision decisao, GateFacts fatos,
                        FingerprintEntity estado, String evidenciaUri) {
        return """
                ## Origem
                Closes %s

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
        String texto = resultado.justificativa().lines().findFirst().orElse(resultado.hipotese()).strip();
        String frase = FIM_DE_FRASE.split(texto, 2)[0];
        return limitar(PREFIXO_DO_TITULO + frase);
    }

    private String limitar(String titulo) {
        if (titulo.length() <= TAMANHO_MAXIMO_DO_TITULO) {
            return titulo;
        }
        String cortado = titulo.substring(0, TAMANHO_MAXIMO_DO_TITULO - RETICENCIAS.length());
        int ultimoEspaco = cortado.lastIndexOf(' ');
        return (ultimoEspaco > PREFIXO_DO_TITULO.length() ? cortado.substring(0, ultimoEspaco) : cortado).strip()
                + RETICENCIAS;
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
