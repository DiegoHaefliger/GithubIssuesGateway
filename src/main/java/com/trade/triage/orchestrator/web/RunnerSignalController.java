package com.trade.triage.orchestrator.web;

import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.orchestrator.job.TriageJobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/runner")
public class RunnerSignalController {

    private final WebhookSignatureVerifier verifier;
    private final TriageJobService jobService;

    public RunnerSignalController(WebhookSignatureVerifier verifier, TriageJobService jobService) {
        this.verifier = verifier;
        this.jobService = jobService;
    }

    @PostMapping("/{jobId}")
    public ResponseEntity<Void> receberSinal(
            @PathVariable String jobId,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura,
            @Valid @RequestBody RunnerSignalRequest sinal) {

        verifier.verify(jobId, assinatura);
        if (sinal.concluiuComSucesso()) {
            jobService.marcarPronto(jobId);
        } else {
            jobService.marcarFalha(jobId, sinal.motivo());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
