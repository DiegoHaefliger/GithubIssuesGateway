package com.trade.triage.orchestrator.publish;

import com.trade.triage.gate.GateDecision;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;

public interface PatchPublisher {

    String publicarBranch(TriageJobEntity job, ProjectEntry projeto, TriageResult resultado,
                          GateDecision decisao, BlastRadius blastRadius);
}
