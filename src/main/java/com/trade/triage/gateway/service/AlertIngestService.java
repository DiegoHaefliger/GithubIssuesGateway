package com.trade.triage.gateway.service;

import com.trade.triage.gateway.web.GrafanaWebhookRequest;

import java.util.List;

public interface AlertIngestService {

    List<IngestResult> ingest(GrafanaWebhookRequest request);
}
