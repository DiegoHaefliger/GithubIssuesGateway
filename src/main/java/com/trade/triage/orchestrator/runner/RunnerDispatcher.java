package com.trade.triage.orchestrator.runner;

import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.registry.model.ProjectEntry;

public interface RunnerDispatcher {

    String dispatch(TriageJobEntity job, ProjectEntry projeto);
}
