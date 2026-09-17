package com.trade.triage.gateway.service;

import com.trade.triage.board.model.CardContent;
import com.trade.triage.gateway.evidence.EvidencePackage;
import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.persistence.entity.FingerprintEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CardContentBuilder {

    public static final String LABEL_AUTO_TRIAGE = "auto-triage";
    private static final int LINHAS_DE_STACK_NO_CARD = 12;

    private final SecretScrubber scrubber;

    public CardContentBuilder(SecretScrubber scrubber) {
        this.scrubber = scrubber;
    }

    public CardContent build(ErrorSignal signal, FingerprintEntity estado,
                             EvidencePackage evidencia, String evidenciaUri) {
        return new CardContent(titulo(signal, estado), corpo(signal, estado, evidencia, evidenciaUri), labels(signal));
    }

    public String comentarioDeRecorrencia(FingerprintEntity estado) {
        return """
                Recorrencia registrada.

                - Ocorrencias: %d
                - Primeira: %s
                - Ultima: %s
                """.formatted(estado.getOccurrenceCount(), estado.getFirstSeen(), estado.getLastSeen());
    }

    public String comentarioDeRegressao(FingerprintEntity estado) {
        return """
                Regressao: fingerprint `%s` voltou a ocorrer depois de resolvido.

                - Ocorrencias acumuladas: %d
                - Ultima: %s
                """.formatted(estado.getFingerprint(), estado.getOccurrenceCount(), estado.getLastSeen());
    }

    private String titulo(ErrorSignal signal, FingerprintEntity estado) {
        String excecao = nomeSimples(signal.exceptionClass());
        return "[triagem] %s em %s (%s)".formatted(excecao, signal.service(), estado.getFingerprint());
    }

    private String corpo(ErrorSignal signal, FingerprintEntity estado,
                         EvidencePackage evidencia, String evidenciaUri) {
        return """
                ## Identidade
                - Fingerprint: `%s`
                - Servico: `%s`
                - Ambiente: `%s`
                - Severidade: `%s`
                - Regra: `%s`

                ## Frequencia
                - Ocorrencias: %d
                - Primeira: %s
                - Ultima: %s

                ## Sintoma
                - Excecao: `%s`
                - Mensagem: %s

                ```
                %s
                ```

                ## Contexto
                %s

                ## Links
                - Pacote de evidencia: %s
                - Lacunas conhecidas: %s

                ## Analise do agente
                _pendente_

                ## Decisao
                _pendente_
                """.formatted(
                estado.getFingerprint(),
                signal.service(),
                signal.env(),
                textoOuTraco(signal.severity()),
                textoOuTraco(signal.ruleId()),
                estado.getOccurrenceCount(),
                estado.getFirstSeen(),
                estado.getLastSeen(),
                textoOuTraco(signal.exceptionClass()),
                scrubber.scrubText(textoOuTraco(signal.message())),
                recorte(evidencia.stacktrace()),
                contexto(evidencia),
                evidenciaUri,
                listaOuTraco(evidencia.lacunas()));
    }

    private String contexto(EvidencePackage evidencia) {
        if (evidencia.commitsSuspeitos().isEmpty()) {
            return "- Commits recentes: nao disponiveis";
        }
        return "- Commits nas ultimas 24h:\n" + evidencia.commitsSuspeitos().stream()
                .map(commit -> "  - " + commit)
                .reduce((esquerda, direita) -> esquerda + "\n" + direita)
                .orElse("");
    }

    private List<String> labels(ErrorSignal signal) {
        List<String> labels = new ArrayList<>();
        labels.add(LABEL_AUTO_TRIAGE);
        if (signal.severity() != null && !signal.severity().isBlank()) {
            labels.add("severity/" + signal.severity());
        }
        if (signal.service() != null && !signal.service().isBlank()) {
            labels.add("service/" + signal.service());
        }
        return List.copyOf(labels);
    }

    private String recorte(String stacktrace) {
        if (stacktrace == null || stacktrace.isBlank()) {
            return "stacktrace indisponivel";
        }
        return stacktrace.lines().limit(LINHAS_DE_STACK_NO_CARD)
                .reduce((esquerda, direita) -> esquerda + "\n" + direita)
                .orElse("stacktrace indisponivel");
    }

    private String nomeSimples(String classe) {
        if (classe == null || classe.isBlank()) {
            return "erro";
        }
        int separador = classe.lastIndexOf('.');
        return separador < 0 ? classe : classe.substring(separador + 1);
    }

    private String textoOuTraco(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private String listaOuTraco(List<String> valores) {
        return valores.isEmpty() ? "-" : String.join("; ", valores);
    }
}
