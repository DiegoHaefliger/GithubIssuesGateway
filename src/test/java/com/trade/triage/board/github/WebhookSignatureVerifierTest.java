package com.trade.triage.board.github;

import com.trade.triage.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureVerifierTest {

    private static final String SEGREDO = "segredo-do-webhook";
    private static final String PAYLOAD = "{\"action\":\"labeled\"}";

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(
            new GitHubProperties(null, "t", SEGREDO, null, Duration.ofSeconds(5)));

    @Test
    void aceitaAssinaturaValida() {
        assertThatCode(() -> verifier.verify(PAYLOAD, assinar(PAYLOAD, SEGREDO))).doesNotThrowAnyException();
    }

    @Test
    void rejeitaAssinaturaDeOutroSegredo() {
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, assinar(PAYLOAD, "outro")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejeitaPayloadAdulterado() {
        String assinatura = assinar(PAYLOAD, SEGREDO);

        assertThatThrownBy(() -> verifier.verify("{\"action\":\"closed\"}", assinatura))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejeitaAssinaturaAusente() {
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejeitaQuandoSegredoNaoEstaConfigurado() {
        WebhookSignatureVerifier semSegredo = new WebhookSignatureVerifier(
                new GitHubProperties(null, "t", "  ", null, Duration.ofSeconds(5)));

        assertThatThrownBy(() -> semSegredo.verify(PAYLOAD, assinar(PAYLOAD, SEGREDO)))
                .isInstanceOf(UnauthorizedException.class);
    }

    private String assinar(String payload, String segredo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
