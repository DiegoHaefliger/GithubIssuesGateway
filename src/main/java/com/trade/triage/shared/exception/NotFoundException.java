package com.trade.triage.shared.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "NOT_FOUND";
    }
}
