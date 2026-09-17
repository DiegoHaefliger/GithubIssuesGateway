package com.trade.triage.gateway.web;

import com.trade.triage.gateway.service.AlertIngestService;
import com.trade.triage.gateway.service.IngestResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/webhooks/grafana")
public class GrafanaWebhookController {

    private final AlertIngestService ingestService;

    public GrafanaWebhookController(AlertIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public ResponseEntity<IngestResponse> receber(@Valid @RequestBody GrafanaWebhookRequest request) {
        List<IngestResult> resultados = ingestService.ingest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestResponse(request.alerts().size(), resultados));
    }
}
