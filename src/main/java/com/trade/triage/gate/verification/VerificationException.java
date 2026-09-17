package com.trade.triage.gate.verification;

import com.trade.triage.shared.exception.BusinessException;

public class VerificationException extends BusinessException {

    public VerificationException(String message) {
        super(message);
    }

    public VerificationException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "VERIFICATION_FAILED";
    }
}
