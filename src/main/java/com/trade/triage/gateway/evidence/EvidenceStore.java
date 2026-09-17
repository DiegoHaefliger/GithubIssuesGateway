package com.trade.triage.gateway.evidence;

import java.util.Optional;

public interface EvidenceStore {

    String store(EvidencePackage pacote);

    Optional<EvidencePackage> load(String uri);
}
