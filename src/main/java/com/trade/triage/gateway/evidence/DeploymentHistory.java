package com.trade.triage.gateway.evidence;

import com.trade.triage.registry.model.ProjectEntry;

import java.time.Duration;
import java.util.List;

public interface DeploymentHistory {

    List<String> deploysRecentes(ProjectEntry projeto, String env, Duration janela);
}
