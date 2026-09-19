package com.trade.triage.gateway.service;

import com.trade.triage.board.model.CardContent;
import com.trade.triage.gateway.evidence.EvidencePackage;
import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CardContentBuilder {

    public static final String LABEL_AUTO_TRIAGE = "auto-triage";
    public static final String LABEL_STORM = "storm";
    public static final String LABEL_AGUARDANDO_HUMANO = "aguardando-humano";
    private static final int LINHAS_DE_CABECALHO_DO_STACK = 8;
    private static final int FRAMES_DO_PROJETO_NO_CARD = 10;
    private static final String PREFIXO_DE_FRAME = "at ";

    private final SecretScrubber scrubber;

    public CardContentBuilder(SecretScrubber scrubber) {
        this.scrubber = scrubber;
    }

    public CardContent build(ErrorSignal signal, ProjectEntry projeto, FingerprintEntity estado,
                             EvidencePackage evidencia, String evidenciaUri) {
        return new CardContent(titulo(signal, estado), corpo(signal, projeto, estado, evidencia, evidenciaUri),
                labels(signal));
    }

    public CardContent storm(ErrorSignal signal, FingerprintEntity estado, int cardsPorHora) {
        return new CardContent(
                "[triagem] tempestade de erros em %s".formatted(estado.getProjeto()),
                """
                ## Identidade
                - Card agregado de tempestade
                - Projeto: `%s`
                - Servico: `%s`
                - Ambiente: `%s`
                - Janela: `%s`

                ## Por que este card existe
                O teto de %d cards por hora foi atingido. A partir daqui a triagem para
                de abrir um card por fingerprint nesta janela e agrega tudo aqui.

                ## Frequencia
                - Erros suprimidos nesta janela: %d
                - Primeiro: %s
                - Ultimo: %s

                ## Ultimo sintoma visto
                - Excecao: `%s`
                - Mensagem: %s

                ## O que fazer
                Tempestade e sinal de incidente ou de regra de alerta ruim. O agente nao
                foi acionado: quem decide aqui e gente.
                """.formatted(
                        estado.getProjeto(),
                        signal.service(),
                        signal.env(),
                        estado.getFingerprint(),
                        cardsPorHora,
                        estado.getOccurrenceCount(),
                        estado.getFirstSeen(),
                        estado.getLastSeen(),
                        textoOuTraco(signal.exceptionClass()),
                        scrubber.scrubText(textoOuTraco(signal.message()))),
                List.of(LABEL_STORM, LABEL_AGUARDANDO_HUMANO));
    }

    public String comentarioDeTempestade(FingerprintEntity estado, ErrorSignal signal) {
        return """
                Mais um erro suprimido pela tempestade.

                - Total na janela: %d
                - Ultimo: %s
                - Excecao: `%s`
                """.formatted(estado.getOccurrenceCount(), estado.getLastSeen(),
                textoOuTraco(signal.exceptionClass()));
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

    private String corpo(ErrorSignal signal, ProjectEntry projeto, FingerprintEntity estado,
                         EvidencePackage evidencia, String evidenciaUri) {
        return """
                ## Identidade
                - Projeto: `%s` (repositorio `%s`, branch `%s`)
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
                - Onde: `%s`
                - Mensagem: %s
                - Trace: `%s`

                ```
                %s
                ```

                ## Requisicao (spans do trace)
                %s

                ## Contexto
                %s

                ## Links
                - Pacote de evidencia: %s
                - Painel do alerta: %s
                - Lacunas conhecidas: %s

                ## Analise do agente
                _pendente_

                ## Decisao
                _pendente_
                """.formatted(
                projeto.projeto(),
                projeto.repositorio(),
                projeto.branchBase(),
                estado.getFingerprint(),
                signal.service(),
                signal.env(),
                textoOuTraco(signal.severity()),
                textoOuTraco(signal.ruleId()),
                estado.getOccurrenceCount(),
                estado.getFirstSeen(),
                estado.getLastSeen(),
                textoOuTraco(signal.exceptionClass()),
                textoOuTraco(signal.localizacao()),
                scrubber.scrubText(textoOuTraco(signal.message())),
                textoOuTraco(signal.traceId()),
                recorte(evidencia.stacktrace(), projeto.pacotesRaiz()),
                itensOuIndisponivel(evidencia.spansDoTrace()),
                contexto(evidencia),
                evidenciaUri,
                textoOuTraco(evidencia.painelUrl()),
                listaOuTraco(evidencia.lacunas()));
    }

    private String contexto(EvidencePackage evidencia) {
        return secao("Deploys nas ultimas 24h", evidencia.deploysRecentes())
                + "\n" + secao("Commits nas ultimas 24h", evidencia.commitsSuspeitos());
    }

    private String secao(String titulo, List<String> itens) {
        if (itens.isEmpty()) {
            return "- " + titulo + ": nao disponiveis";
        }
        return "- " + titulo + ":\n" + itens.stream()
                .map(item -> "  - " + item)
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

    /** Stack de framework tem centenas de frames; o card mostra a mensagem e so' os frames do projeto. */
    private String recorte(String stacktrace, List<String> pacotesRaiz) {
        if (stacktrace == null || stacktrace.isBlank()) {
            return "stacktrace indisponivel";
        }
        List<String> linhas = stacktrace.lines().toList();
        List<String> cabecalho = linhas.stream()
                .takeWhile(linha -> !linha.strip().startsWith(PREFIXO_DE_FRAME))
                .limit(LINHAS_DE_CABECALHO_DO_STACK)
                .toList();
        List<String> framesDoProjeto = linhas.stream()
                .map(String::strip)
                .filter(linha -> linha.startsWith(PREFIXO_DE_FRAME))
                .filter(linha -> pacotesRaiz.stream().anyMatch(pacote -> linha.startsWith(PREFIXO_DE_FRAME + pacote)))
                .filter(linha -> !linha.contains("(Unknown Source)"))
                .limit(FRAMES_DO_PROJETO_NO_CARD)
                .map(linha -> "    " + linha)
                .toList();
        List<String> recorte = new ArrayList<>(cabecalho);
        recorte.addAll(framesDoProjeto);
        long totalDeFrames = linhas.stream().filter(linha -> linha.strip().startsWith(PREFIXO_DE_FRAME)).count();
        if (totalDeFrames > framesDoProjeto.size()) {
            recorte.add("    ... %d frames de framework omitidos".formatted(totalDeFrames - framesDoProjeto.size()));
        }
        return String.join("\n", recorte);
    }

    private String itensOuIndisponivel(List<String> itens) {
        if (itens.isEmpty()) {
            return "- indisponivel";
        }
        return itens.stream().map(item -> "- `" + item + "`").reduce((a, b) -> a + "\n" + b).orElse("");
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
