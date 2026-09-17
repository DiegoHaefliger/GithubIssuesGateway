package com.trade.triage.registry;

import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectRegistrySnapshot;

import java.util.Optional;

public interface ProjectRegistry {

    ProjectRegistrySnapshot snapshot();

    Optional<ProjectEntry> findByServiceAndEnv(String service, String env);

    Optional<ProjectEntry> findByRepository(String repositorio);

    String version();

    void reload();
}
