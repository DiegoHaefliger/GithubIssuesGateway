package com.trade.triage.gateway.evidence;

import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.registry.model.ProjectEntry;

public interface EvidenceCollector {

    EvidencePackage collect(ErrorSignal signal, ProjectEntry projeto, String fingerprint);
}
