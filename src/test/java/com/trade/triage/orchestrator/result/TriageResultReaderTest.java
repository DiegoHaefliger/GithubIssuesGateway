package com.trade.triage.orchestrator.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.triage.persistence.entity.TriageJobEntity;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageResultReaderTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    private static final String COMPLETO = """
            {
              "job_id": "job-1",
              "card": "acme/trade#123",
              "hipotese": "exitReason nao e preenchido no caminho do breaker",
              "evidencia_a_favor": ["os outros tres caminhos preenchem"],
              "evidencia_contra": ["nao ocorre em homologacao"],
              "diff": "diff --git a/x b/x\\n",
              "teste_novo": {"arquivo": "src/test/java/XTest.java", "identificador": "XTest#y"},
              "justificativa": "alinha o caminho do breaker aos demais"
            }
            """;

    @Test
    void leResultadoValido() {
        TriageResult resultado = reader(COMPLETO).read(job());

        assertThat(resultado.hipotese()).startsWith("exitReason");
        assertThat(resultado.temProposta()).isTrue();
    }

    @Test
    void resultadoSemDiffEhAnaliseSemProposta() {
        TriageResult resultado = reader(COMPLETO
                .replace("\"diff\": \"diff --git a/x b/x\\\\n\",", "")
                .replace("\"teste_novo\": {\"arquivo\": \"src/test/java/XTest.java\", \"identificador\": \"XTest#y\"},", ""))
                .read(job());

        assertThat(resultado.temProposta()).isFalse();
    }

    @Test
    void arquivoAusenteEhInvalido() {
        TriageResultReader reader = new TriageResultReader(
                jobEntity -> Optional.empty(), new ObjectMapper(), validator());

        assertThatThrownBy(() -> reader.read(job()))
                .isInstanceOf(InvalidResultException.class)
                .hasMessageContaining("ausente");
    }

    @Test
    void jsonForaDoSchemaEhInvalido() {
        assertThatThrownBy(() -> reader("{").read(job()))
                .isInstanceOf(InvalidResultException.class)
                .hasMessageContaining("fora do schema");
    }

    @Test
    void hipoteseVaziaEhInvalida() {
        assertThatThrownBy(() -> reader(COMPLETO.replace("\"exitReason nao e preenchido no caminho do breaker\"", "\"\"")).read(job()))
                .isInstanceOf(InvalidResultException.class)
                .hasMessageContaining("hipotese");
    }

    @Test
    void evidenciaContraAusenteEhInvalida() {
        assertThatThrownBy(() -> reader(COMPLETO.replace("\"evidencia_contra\": [\"nao ocorre em homologacao\"],", "")).read(job()))
                .isInstanceOf(InvalidResultException.class)
                .hasMessageContaining("evidenciaContra");
    }

    @Test
    void resultadoDeOutroJobEhRecusado() {
        assertThatThrownBy(() -> reader(COMPLETO.replace("job-1", "job-9")).read(job()))
                .isInstanceOf(InvalidResultException.class)
                .hasMessageContaining("outro job");
    }

    @Test
    void resultadoDeOutroCardEhRecusado() {
        assertThatThrownBy(() -> reader(COMPLETO.replace("acme/trade#123", "acme/trade#999")).read(job()))
                .isInstanceOf(InvalidResultException.class)
                .hasMessageContaining("outro job ou card");
    }

    private TriageResultReader reader(String conteudo) {
        return new TriageResultReader(jobEntity -> Optional.of(conteudo), new ObjectMapper(), validator());
    }

    private jakarta.validation.Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private TriageJobEntity job() {
        return new TriageJobEntity("job-1", "f1", "trade", "acme/trade", "acme/trade#123",
                "issues.labeled", AGORA, AGORA.plus(Duration.ofMinutes(30)));
    }
}
