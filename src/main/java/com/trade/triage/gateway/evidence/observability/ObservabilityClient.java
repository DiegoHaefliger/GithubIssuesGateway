package com.trade.triage.gateway.evidence.observability;

import com.trade.triage.gateway.evidence.OccurrenceSample;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface ObservabilityClient {

    List<String> logsPorTrace(String traceId, Instant inicio, Instant fim);

    List<String> logsPorServico(String service, String env, Instant inicio, Instant fim);

    List<OccurrenceSample> serieDeErros(String service, String env, Instant inicio, Instant fim);

    Map<String, String> metricasDoServico(String service, Instant inicio, Instant fim);
}
