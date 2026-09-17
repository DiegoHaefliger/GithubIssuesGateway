package com.trade.triage.registry;

import com.trade.triage.shared.exception.BusinessException;

public class RegistryLoadException extends BusinessException {

    public RegistryLoadException(String message) {
        super(message);
    }

    public RegistryLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "REGISTRY_LOAD_FAILED";
    }
}
