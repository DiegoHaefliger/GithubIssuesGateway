package com.trade.triage.gateway.web;

import com.trade.triage.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrafanaWebhookAuthenticatorTest {

    private static final String TOKEN = "token-do-grafana";

    private final GrafanaWebhookAuthenticator authenticator =
            new GrafanaWebhookAuthenticator(new GrafanaWebhookProperties(TOKEN, "grafana"));

    @Test
    void aceitaBearerComTokenCorreto() {
        assertThatCode(() -> authenticator.authenticate("Bearer " + TOKEN)).doesNotThrowAnyException();
    }

    @Test
    void aceitaBasicComUsuarioESenhaCorretos() {
        assertThatCode(() -> authenticator.authenticate(basic("grafana", TOKEN))).doesNotThrowAnyException();
    }

    @Test
    void rejeitaTokenErrado() {
        assertThatThrownBy(() -> authenticator.authenticate("Bearer outro"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejeitaUsuarioErradoNoBasic() {
        assertThatThrownBy(() -> authenticator.authenticate(basic("outro", TOKEN)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Usuario");
    }

    @Test
    void rejeitaCabecalhoAusente() {
        assertThatThrownBy(() -> authenticator.authenticate(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejeitaEsquemaNaoSuportado() {
        assertThatThrownBy(() -> authenticator.authenticate("Digest algo"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Esquema");
    }

    @Test
    void rejeitaBasicIlegivel() {
        assertThatThrownBy(() -> authenticator.authenticate("Basic nao-e-base64!!"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void falhaFechadaQuandoTokenNaoEstaConfigurado() {
        GrafanaWebhookAuthenticator semToken =
                new GrafanaWebhookAuthenticator(new GrafanaWebhookProperties("  ", "grafana"));

        assertThatThrownBy(() -> semToken.authenticate("Bearer " + TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("nao configurado");
    }

    private String basic(String usuario, String senha) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((usuario + ":" + senha).getBytes(StandardCharsets.UTF_8));
    }
}
