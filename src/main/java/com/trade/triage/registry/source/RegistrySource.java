package com.trade.triage.registry.source;

public interface RegistrySource {

    String fetchRawJson();

    String describe();
}
