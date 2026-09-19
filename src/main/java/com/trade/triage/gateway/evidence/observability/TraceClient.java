package com.trade.triage.gateway.evidence.observability;

import java.util.List;

public interface TraceClient {

    List<String> spansDoTrace(String traceId);
}
