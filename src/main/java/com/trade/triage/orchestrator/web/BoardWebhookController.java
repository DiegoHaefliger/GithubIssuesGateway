package com.trade.triage.orchestrator.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.triage.board.BoardClient;
import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.gateway.service.CardContentBuilder;
import com.trade.triage.orchestrator.job.JobEnqueueOutcome;
import com.trade.triage.orchestrator.job.JobEnqueueResult;
import com.trade.triage.orchestrator.job.TriageJobService;
import com.trade.triage.orchestrator.outcome.OutcomeService;
import com.trade.triage.shared.exception.InvalidPayloadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/webhooks/board")
public class BoardWebhookController {

    private static final String EVENTO_ISSUES = "issues";
    private static final String EVENTO_COMENTARIO = "issue_comment";
    private static final String EVENTO_PULL_REQUEST = "pull_request";
    private static final String EVENTO_PUSH = "push";

    private final WebhookSignatureVerifier verifier;
    private final WebhookDeduplicator deduplicator;
    private final TriageJobService jobService;
    private final OutcomeService outcomeService;
    private final ObjectMapper objectMapper;

    public BoardWebhookController(WebhookSignatureVerifier verifier,
                                  WebhookDeduplicator deduplicator,
                                  TriageJobService jobService,
                                  OutcomeService outcomeService,
                                  ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.deduplicator = deduplicator;
        this.jobService = jobService;
        this.outcomeService = outcomeService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<JobEnqueueResult> receber(
            @RequestHeader("X-GitHub-Event") String evento,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody String payload) {

        verifier.verify(payload, assinatura);
        if (!deduplicator.primeiraEntrega(deliveryId, evento)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(registrado("entrega repetida descartada"));
        }
        JsonNode corpo = ler(payload);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(switch (evento) {
            case EVENTO_ISSUES -> tratarIssue(corpo);
            case EVENTO_COMENTARIO -> tratarComentario(corpo);
            case EVENTO_PULL_REQUEST -> tratarPullRequest(corpo);
            case EVENTO_PUSH -> tratarPush(corpo);
            default -> JobEnqueueResult.recusado(JobEnqueueOutcome.CARD_DESCONHECIDO,
                    "evento ignorado: " + evento);
        });
    }

    private JobEnqueueResult tratarPullRequest(JsonNode corpo) {
        if (!"closed".equals(texto(corpo, "action"))) {
            return ignorado("acao de pull request ignorada: " + texto(corpo, "action"));
        }
        String repositorio = corpo.path("repository").path("full_name").asText("");
        int numero = corpo.path("pull_request").path("number").asInt(-1);
        if (repositorio.isBlank() || numero < 0) {
            throw new InvalidPayloadException("Webhook de pull request sem repositorio ou numero");
        }
        outcomeService.registrarPullRequestFechado(repositorio + "#" + numero,
                corpo.path("pull_request").path("merged").asBoolean(false));
        return registrado("desfecho do pull request registrado");
    }

    private JobEnqueueResult tratarPush(JsonNode corpo) {
        List<String> mensagens = new ArrayList<>();
        corpo.path("commits").forEach(commit -> mensagens.add(commit.path("message").asText("")));
        outcomeService.registrarReversoes(mensagens);
        return registrado("push avaliado para reversao");
    }

    private JobEnqueueResult tratarIssue(JsonNode corpo) {
        if ("closed".equals(texto(corpo, "action"))) {
            outcomeService.registrarCardFechado(cardRef(corpo));
            return registrado("card fechado, fingerprint resolvido");
        }
        if (!"labeled".equals(texto(corpo, "action"))) {
            return ignorado("acao de issue ignorada: " + texto(corpo, "action"));
        }
        String label = corpo.path("label").path("name").asText("");
        if (!CardContentBuilder.LABEL_AUTO_TRIAGE.equals(label)) {
            return ignorado("label ignorada: " + label);
        }
        return jobService.enfileirar(cardRef(corpo), "issues.labeled");
    }

    private JobEnqueueResult tratarComentario(JsonNode corpo) {
        if (!"created".equals(texto(corpo, "action"))) {
            return ignorado("acao de comentario ignorada: " + texto(corpo, "action"));
        }
        if (corpo.path("sender").path("type").asText("").equalsIgnoreCase("Bot")) {
            return ignorado("comentario do proprio bot");
        }
        // TRIAGE_GITHUB_TOKEN e um PAT pessoal: sender.type nunca vem "Bot" para
        // comentario postado pelo proprio orquestrador. Sem este marcador, a
        // analise publicada retriagem a si mesma (loop).
        if (texto(corpo, "comment", "body").contains(BoardClient.MARCADOR_COMENTARIO_ORQUESTRADOR)) {
            return ignorado("comentario do proprio orquestrador");
        }
        return jobService.enfileirar(cardRef(corpo), "issue_comment.created");
    }

    private String cardRef(JsonNode corpo) {
        String repositorio = corpo.path("repository").path("full_name").asText("");
        int numero = corpo.path("issue").path("number").asInt(-1);
        if (repositorio.isBlank() || numero < 0) {
            throw new InvalidPayloadException("Webhook sem repository.full_name ou issue.number");
        }
        return repositorio + "#" + numero;
    }

    private JobEnqueueResult registrado(String motivo) {
        return JobEnqueueResult.recusado(JobEnqueueOutcome.EVENTO_REGISTRADO, motivo);
    }

    private JobEnqueueResult ignorado(String motivo) {
        return JobEnqueueResult.recusado(JobEnqueueOutcome.CARD_DESCONHECIDO, motivo);
    }

    private String texto(JsonNode corpo, String campo) {
        return corpo.path(campo).asText("");
    }

    private String texto(JsonNode corpo, String campo, String subcampo) {
        return corpo.path(campo).path(subcampo).asText("");
    }

    private JsonNode ler(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException exception) {
            throw new InvalidPayloadException("Payload do webhook do board nao e JSON valido", exception);
        }
    }
}
