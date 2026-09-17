package com.trade.triage.gate;

import com.trade.triage.registry.model.ProjectEntry;

public interface PolicyGate {

    GateDecision decidir(GateFacts fatos, ProjectEntry projeto);
}
