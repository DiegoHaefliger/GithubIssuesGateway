package com.trade.triage.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectRegistrySnapshot;
import com.trade.triage.registry.source.RegistrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class CachedProjectRegistry implements ProjectRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(CachedProjectRegistry.class);

    private final RegistrySource source;
    private final ObjectMapper objectMapper;
    private final RegistryValidator validator;
    private final AtomicReference<ProjectRegistrySnapshot> cache =
            new AtomicReference<>(ProjectRegistrySnapshot.vazio());

    public CachedProjectRegistry(RegistrySource source, ObjectMapper objectMapper, RegistryValidator validator) {
        this.source = source;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public ProjectRegistrySnapshot snapshot() {
        return cache.get();
    }

    @Override
    public Optional<ProjectEntry> findByServiceAndEnv(String service, String env) {
        return cache.get().findByServiceAndEnv(service, env);
    }

    @Override
    public Optional<ProjectEntry> findByRepository(String repositorio) {
        return cache.get().findByRepository(repositorio);
    }

    @Override
    public String version() {
        return cache.get().versao();
    }

    @Override
    public void reload() {
        try {
            ProjectRegistrySnapshot candidato = objectMapper.readValue(
                    source.fetchRawJson(), ProjectRegistrySnapshot.class);
            validator.validate(candidato);
            cache.set(candidato);
            LOG.info("registro carregado origem={} versao={} projetos={}",
                    source.describe(), candidato.versao(), candidato.projetos().size());
        } catch (RegistryLoadException exception) {
            LOG.error("registro rejeitado origem={} versao_vigente={} motivo={}",
                    source.describe(), version(), exception.getMessage());
        } catch (Exception exception) {
            LOG.error("falha ao carregar registro origem={} versao_vigente={}",
                    source.describe(), version(), exception);
        }
    }
}
