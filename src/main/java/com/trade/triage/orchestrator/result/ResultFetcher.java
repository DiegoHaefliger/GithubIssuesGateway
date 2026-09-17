package com.trade.triage.orchestrator.result;

import com.trade.triage.persistence.entity.TriageJobEntity;

import java.util.Optional;

public interface ResultFetcher {

    Optional<String> fetchRawJson(TriageJobEntity job);
}
