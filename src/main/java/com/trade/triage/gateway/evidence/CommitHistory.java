package com.trade.triage.gateway.evidence;

import com.trade.triage.registry.model.ProjectEntry;

import java.time.Duration;
import java.util.List;

public interface CommitHistory {

    List<String> commitsRecentes(ProjectEntry projeto, Duration janela);
}
