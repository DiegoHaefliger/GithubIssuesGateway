package com.trade.triage.registry;

import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathPolicyTest {

    private final PathPolicy policy = new PathPolicy();

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.acme"), "acme/trade", "master",
            List.of("producao"), "acme/trade",
            List.of("src/main/java/**/parser/**"),
            Map.of("src/main/java/**/parser/**", BlastRadius.BAIXO,
                    "src/main/java/**/execution/**", BlastRadius.CRITICO,
                    "src/main/java/**", BlastRadius.MEDIO),
            ProjectLimits.conservador(), true);

    @Test
    void reconheceCaminhoNaAllowlist() {
        assertThat(policy.dentroDaAllowlist(projeto, "src/main/java/com/acme/parser/Candle.java")).isTrue();
    }

    @Test
    void rejeitaCaminhoForaDaAllowlist() {
        assertThat(policy.dentroDaAllowlist(projeto, "src/main/java/com/acme/execution/Order.java")).isFalse();
    }

    @Test
    void caminhoNaoClassificadoEhCritico() {
        assertThat(policy.blastRadiusDe(projeto, "infra/terraform/main.tf")).isEqualTo(BlastRadius.CRITICO);
    }

    @Test
    void escolheOGlobMaisSeveroQuandoVariosCasam() {
        assertThat(policy.blastRadiusDe(projeto, "src/main/java/com/acme/execution/Order.java"))
                .isEqualTo(BlastRadius.CRITICO);
    }

    @Test
    void classificaCaminhoDeBaixoRiscoComoMedioQuandoGlobMaisAmploEhMaisSevero() {
        assertThat(policy.blastRadiusDe(projeto, "src/main/java/com/acme/parser/Candle.java"))
                .isEqualTo(BlastRadius.MEDIO);
    }

    @Test
    void allowlistVaziaNaoElegeNada() {
        ProjectEntry semAllowlist = new ProjectEntry(
                "x", "/tmp/x", List.of("x"), List.of("com.acme"), "acme/x", "main", List.of("producao"), "acme/x",
                List.of(), Map.of(), ProjectLimits.conservador(), true);

        assertThat(policy.dentroDaAllowlist(semAllowlist, "src/Any.java")).isFalse();
    }
}
