package com.trade.triage.orchestrator.job;

import com.trade.triage.orchestrator.web.JobContextResponse;

public interface JobContextService {

    JobContextResponse contextoDe(String jobId, Long runId);
}
