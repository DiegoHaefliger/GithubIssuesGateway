package com.trade.triage.gateway.fingerprint;

import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintCalculatorTest {

    private static final String STACK_BREAKER = """
            java.lang.NullPointerException: exitReason is null
                at java.base/java.util.Objects.requireNonNull(Objects.java:259)
                at io.quarkus.arc.impl.InterceptorInvocation.invoke(InterceptorInvocation.java:41)
                at com.trade.execution.ExitReasonResolver.resolve(ExitReasonResolver.java:88)
                at com.trade.execution.PositionCloser.close(PositionCloser.java:44)
            """;

    private static final String STACK_OUTRO_CAMINHO = """
            java.lang.NullPointerException: exitReason is null
                at io.quarkus.arc.impl.InterceptorInvocation.invoke(InterceptorInvocation.java:41)
                at com.trade.risk.StopLossGuard.evaluate(StopLossGuard.java:120)
            """;

    private final FingerprintCalculator calculator =
            new FingerprintCalculator(new MessageNormalizer(), new StackFrameSelector());

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade",
            "master", List.of("producao"), "acme/trade", List.of(), Map.of(),
            ProjectLimits.conservador(), true);

    @Test
    void geraFingerprintEstavelDeTamanhoFixo() {
        String fingerprint = calculator.calculate(sinal("Falha ao fechar posicao 4821"), projeto);

        assertThat(fingerprint).hasSize(16).matches("[0-9a-f]{16}");
    }

    @Test
    void mesmoDefeitoComIdsDiferentesColideNoMesmoFingerprint() {
        String primeiro = calculator.calculate(sinal("Falha ao fechar posicao 4821 em 2026-09-07T10:00:00Z"), projeto);
        String segundo = calculator.calculate(sinal("Falha ao fechar posicao 9137 em 2026-09-08T22:31:12Z"), projeto);

        assertThat(primeiro).isEqualTo(segundo);
    }

    @Test
    void defeitosEmFramesDiferentesNaoColidem() {
        ErrorSignal breaker = sinal("Falha ao fechar posicao 1");
        ErrorSignal guard = new ErrorSignal("trade-backend", "producao", "r1", "critical", "t1",
                "com.trade.risk.StopLossGuard", "java.lang.NullPointerException",
                STACK_OUTRO_CAMINHO, "Falha ao fechar posicao 1", Instant.EPOCH);

        assertThat(calculator.calculate(breaker, projeto)).isNotEqualTo(calculator.calculate(guard, projeto));
    }

    @Test
    void servicosDiferentesNaoColidem() {
        ErrorSignal outroServico = new ErrorSignal("trade-worker", "producao", "r1", "critical", "t1",
                "com.trade.execution.ExitReasonResolver", "java.lang.NullPointerException",
                STACK_BREAKER, "Falha ao fechar posicao 1", Instant.EPOCH);

        assertThat(calculator.calculate(sinal("Falha ao fechar posicao 1"), projeto))
                .isNotEqualTo(calculator.calculate(outroServico, projeto));
    }

    @Test
    void stacktraceSemFrameDoProjetoAindaGeraFingerprint() {
        ErrorSignal semFrame = new ErrorSignal("trade-backend", "producao", "r1", "critical", "t1",
                "com.trade.X", "java.lang.NullPointerException",
                "    at io.quarkus.arc.impl.InterceptorInvocation.invoke(InterceptorInvocation.java:41)",
                "boom", Instant.EPOCH);

        assertThat(calculator.calculate(semFrame, projeto)).hasSize(16);
    }

    private ErrorSignal sinal(String message) {
        return new ErrorSignal("trade-backend", "producao", "r1", "critical", "t1",
                "com.trade.execution.ExitReasonResolver", "java.lang.NullPointerException",
                STACK_BREAKER, message, Instant.EPOCH);
    }
}
