package com.trade.triage.persistence.entity;

import com.trade.triage.shared.exception.BusinessException;

public class IllegalJobTransitionException extends BusinessException {

    public IllegalJobTransitionException(String jobId, JobState origem, JobState destino) {
        super("Job " + jobId + " nao pode ir de " + origem + " para " + destino);
    }

    @Override
    public String code() {
        return "ILLEGAL_JOB_TRANSITION";
    }
}
