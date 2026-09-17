package com.trade.triage.registry;

import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Map;

@Component
public class PathPolicy {

    public boolean dentroDaAllowlist(ProjectEntry projeto, String caminho) {
        return projeto.allowlist().stream().anyMatch(glob -> matches(glob, caminho));
    }

    public BlastRadius blastRadiusDe(ProjectEntry projeto, String caminho) {
        BlastRadius maisSevero = null;
        for (Map.Entry<String, BlastRadius> regra : projeto.blastRadius().entrySet()) {
            if (matches(regra.getKey(), caminho) && isMaisSevero(regra.getValue(), maisSevero)) {
                maisSevero = regra.getValue();
            }
        }
        return maisSevero == null ? BlastRadius.CRITICO : maisSevero;
    }

    private boolean isMaisSevero(BlastRadius candidato, BlastRadius atual) {
        return atual == null || candidato.ordinal() > atual.ordinal();
    }

    private boolean matches(String glob, String caminho) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        return matcher.matches(java.nio.file.Path.of(caminho));
    }
}
