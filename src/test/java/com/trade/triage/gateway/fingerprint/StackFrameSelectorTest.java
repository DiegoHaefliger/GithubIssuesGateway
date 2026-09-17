package com.trade.triage.gateway.fingerprint;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StackFrameSelectorTest {

    private final StackFrameSelector selector = new StackFrameSelector();

    @Test
    void ignoraFramesDeFrameworkEPegaOPrimeiroDoProjeto() {
        String stack = """
                java.lang.NullPointerException
                    at java.base/java.util.Objects.requireNonNull(Objects.java:259)
                    at io.quarkus.arc.impl.InterceptorInvocation.invoke(InterceptorInvocation.java:41)
                    at com.trade.execution.ExitReasonResolver.resolve(ExitReasonResolver.java:88)
                """;

        assertThat(selector.topFrameDoProjeto(stack, List.of("com.trade")))
                .isEqualTo("com.trade.execution.ExitReasonResolver.resolve(88)");
    }

    @Test
    void semFrameDoProjetoRetornaMarcador() {
        String stack = "    at io.quarkus.arc.impl.InterceptorInvocation.invoke(InterceptorInvocation.java:41)";

        assertThat(selector.topFrameDoProjeto(stack, List.of("com.trade")))
                .isEqualTo("<sem-frame-do-projeto>");
    }

    @Test
    void stacktraceVazioRetornaMarcador() {
        assertThat(selector.topFrameDoProjeto("  ", List.of("com.trade")))
                .isEqualTo("<sem-frame-do-projeto>");
    }

    @Test
    void frameNativoSemLinhaUsaInterrogacao() {
        String stack = "    at com.trade.native.Bridge.call(Native Method)";

        assertThat(selector.topFrameDoProjeto(stack, List.of("com.trade")))
                .isEqualTo("com.trade.native.Bridge.call(?)");
    }
}
