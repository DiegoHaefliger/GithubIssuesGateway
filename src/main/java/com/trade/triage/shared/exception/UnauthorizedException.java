package com.trade.triage.shared.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "UNAUTHORIZED";
    }
}
