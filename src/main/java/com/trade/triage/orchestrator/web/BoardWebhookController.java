package com.trade.triage.orchestrator.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.gateway.service.CardContentBuilder;
import com.trade.triage.orchestrator.job.JobEnqueueOutcome;
import com.trade.triage.orchestrator.job.JobEnqueueResult;
import com.trade.triage.orchestrator.job.TriageJobService;
import com.trade.triage.shared.exception.InvalidPayloadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/webhooks/board")
public class BoardWebhookController {

    private static final String EVENTO_ISSUES = "issues";
    private static final String EVENTO_COMENTARIO = "issue_comment";

    private final WebhookSignatureVerifier verifier;
    private final TriageJobService jobService;
    private final ObjectMapper objectMapper;

    public BoardWebhookController(WebhookSignatureVerifier verifier,
                                  TriageJobService jobService,
                                  ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<JobEnqueueResult> receber(
            @RequestHeader("X-GitHub-Event") String evento,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura,
            @RequestBody String payload) {

        verifier.verify(payload, assinatura);
        JsonNode corpo = ler(payload);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(switch (evento) {
            case EVENTO_ISSUES -> tratarIssue(corpo);
            case EVENTO_COMENTARIO -> tratarComentario(corpo);
            default -> JobEnqueueResult.recusado(JobEnqueueOutcome.CARD_DESCONHECIDO,
                    "evento ignorado: " + evento);
        });
    }

    private JobEnqueueResult tratarIssue(JsonNode corpo) {
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

    private JobEnqueueResult ignorado(String motivo) {
        return JobEnqueueResult.recusado(JobEnqueueOutcome.CARD_DESCONHECIDO, motivo);
    }

    private String texto(JsonNode corpo, String campo) {
        return corpo.path(campo).asText("");
    }

    private JsonNode ler(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException exception) {
            throw new InvalidPayloadException("Payload do webhook do board nao e JSON valido", exception);
        }
    }
}
