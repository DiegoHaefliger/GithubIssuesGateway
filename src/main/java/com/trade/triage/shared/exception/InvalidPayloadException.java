package com.trade.triage.shared.exception;

public class InvalidPayloadException extends BusinessException {

    public InvalidPayloadException(String message) {
        super(message);
    }

    public InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "INVALID_PAYLOAD";
    }
}
