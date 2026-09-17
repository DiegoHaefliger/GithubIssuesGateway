package com.trade.triage.gate;

import com.trade.triage.persistence.entity.Decision;

public record GateDecision(Decision decisao, String regraDecisora, String explicacao) {
}
