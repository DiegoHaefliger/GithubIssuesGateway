package com.trade.triage.gateway.evidence;

import com.trade.triage.shared.exception.BusinessException;

public class EvidenceStoreException extends BusinessException {

    public EvidenceStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "EVIDENCE_STORE_FAILED";
    }
}
