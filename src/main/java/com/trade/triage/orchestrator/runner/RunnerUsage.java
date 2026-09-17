package com.trade.triage.orchestrator.runner;

import com.trade.triage.persistence.entity.TriageJobEntity;

import java.time.Duration;
import java.util.Optional;

public interface RunnerUsage {

    Optional<Duration> tempoFaturavel(TriageJobEntity job);
}
