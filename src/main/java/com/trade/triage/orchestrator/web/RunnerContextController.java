package com.trade.triage.orchestrator.web;

import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.orchestrator.job.JobContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class RunnerContextController {

    private final WebhookSignatureVerifier verifier;
    private final JobContextService contextService;

    public RunnerContextController(WebhookSignatureVerifier verifier, JobContextService contextService) {
        this.verifier = verifier;
        this.contextService = contextService;
    }

    @GetMapping("/{jobId}/contexto")
    public ResponseEntity<JobContextResponse> contexto(
            @PathVariable String jobId,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura) {

        verifier.verify(jobId, assinatura);
        return ResponseEntity.ok(contextService.contextoDe(jobId));
    }
}
