package com.trade.triage.board.github;

import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubCloneUrlsTest {

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master",
            List.of("mvn", "test"), List.of("producao"), "acme/trade", List.of(), Map.of(),
            ProjectLimits.conservador(), true);

    @Test
    void embutiOTokenComoCredencialDaUrlHttps() {
        GitHubCloneUrls urls = new GitHubCloneUrls(
                new GitHubProperties(null, "ghp_segredo", null, null, Duration.ofSeconds(1)));

        assertThat(urls.de(projeto)).isEqualTo("https://x-access-token:ghp_segredo@github.com/acme/trade.git");
    }
}
