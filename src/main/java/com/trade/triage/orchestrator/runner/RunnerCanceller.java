package com.trade.triage.orchestrator.runner;

import com.trade.triage.persistence.entity.TriageJobEntity;

public interface RunnerCanceller {

    boolean cancelar(TriageJobEntity job);
}
