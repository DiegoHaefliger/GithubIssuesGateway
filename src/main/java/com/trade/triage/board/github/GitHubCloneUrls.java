package com.trade.triage.board.github;

import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

/**
 * URL de clone/push autenticada por token, para rodar sem checkout local do
 * projeto monitorado (gateway roda em deployable proprio, sem acesso ao disco
 * de quem hospeda os repositorios).
 */
@Component
public class GitHubCloneUrls {

    private final GitHubProperties properties;

    public GitHubCloneUrls(GitHubProperties properties) {
        this.properties = properties;
    }

    public String de(ProjectEntry projeto) {
        return "https://x-access-token:%s@github.com/%s.git".formatted(properties.token(), projeto.repositorio());
    }
}
