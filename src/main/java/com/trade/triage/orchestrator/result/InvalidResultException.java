package com.trade.triage.orchestrator.result;

import com.trade.triage.shared.exception.BusinessException;

public class InvalidResultException extends BusinessException {

    public InvalidResultException(String message) {
        super(message);
    }

    public InvalidResultException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "INVALID_TRIAGE_RESULT";
    }
}
