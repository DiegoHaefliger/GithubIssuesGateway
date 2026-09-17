package com.trade.triage.gateway.evidence;

import java.time.Instant;

public record OccurrenceSample(Instant momento, double valor) {
}
