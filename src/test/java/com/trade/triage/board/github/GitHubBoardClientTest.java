package com.trade.triage.board.github;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardContent;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.PullRequestContent;
import com.trade.triage.board.model.PullRequestRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubBoardClientTest {

    private MockRestServiceServer server;
    private GitHubBoardClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubBoardClient(builder.build());
    }

    @Test
    void criaCardComTituloCorpoELabels() {
        server.expect(requestTo("https://api.github.com/repos/acme/trade/issues"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.title").value("[triagem] NPE em ExitReasonResolver"))
                .andExpect(jsonPath("$.labels[0]").value("auto-triage"))
                .andRespond(withSuccess("{\"number\": 123, \"html_url\": \"u\", \"state\": \"open\"}",
                        MediaType.APPLICATION_JSON));

        CardRef card = client.criarCard("acme/trade", new CardContent(
                "[triagem] NPE em ExitReasonResolver", "corpo", List.of("auto-triage", "severity/critical")));

        assertThat(card.asString()).isEqualTo("acme/trade#123");
        server.verify();
    }

    @Test
    void comentaNoCard() {
        server.expect(requestTo("https://api.github.com/repos/acme/trade/issues/123/comments"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.body").value(
                        BoardClient.MARCADOR_COMENTARIO_ORQUESTRADOR + "\nrecorrencia registrada"))
                .andRespond(withSuccess());

        client.comentar(new CardRef("acme/trade", 123), "recorrencia registrada");

        server.verify();
    }

    @Test
    void aplicaLabel() {
        server.expect(requestTo("https://api.github.com/repos/acme/trade/issues/123/labels"))
                .andExpect(jsonPath("$.labels[0]").value("aguardando-humano"))
                .andRespond(withSuccess());

        client.aplicarLabel(new CardRef("acme/trade", 123), "aguardando-humano");

        server.verify();
    }

    @Test
    void reabreCardDeRegressao() {
        server.expect(requestTo("https://api.github.com/repos/acme/trade/issues/123"))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andExpect(jsonPath("$.state").value("open"))
                .andRespond(withSuccess());

        client.reabrir(new CardRef("acme/trade", 123));

        server.verify();
    }

    @Test
    void abrePullRequestApontandoParaBranchBase() {
        server.expect(requestTo("https://api.github.com/repos/acme/trade/pulls"))
                .andExpect(jsonPath("$.head").value("triagem/a3f9c2d1"))
                .andExpect(jsonPath("$.base").value("master"))
                .andRespond(withSuccess("""
                        {"number": 77, "html_url": "https://github.com/acme/trade/pull/77", "node_id": "PR_node"}
                        """, MediaType.APPLICATION_JSON));

        PullRequestRef pr = client.abrirPullRequest("acme/trade",
                new PullRequestContent("fix: preenche exitReason", "corpo", "triagem/a3f9c2d1", "master"));

        assertThat(pr.numero()).isEqualTo(77);
        assertThat(pr.nodeId()).isEqualTo("PR_node");
        assertThat(pr.url()).isEqualTo("https://github.com/acme/trade/pull/77");
        server.verify();
    }

    @Test
    void habilitaAutoMergePorGraphql() {
        server.expect(requestTo("https://api.github.com/graphql"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.variables.pullRequestId").value("PR_node"))
                .andRespond(withSuccess("{\"data\": {\"enablePullRequestAutoMerge\": {}}}",
                        MediaType.APPLICATION_JSON));

        client.habilitarAutoMerge(new PullRequestRef("acme/trade", 77, "u", "PR_node"));

        server.verify();
    }

    @Test
    void erroDoGraphqlAoHabilitarAutoMergeViraFalha() {
        server.expect(requestTo("https://api.github.com/graphql"))
                .andRespond(withSuccess("{\"errors\": [{\"message\": \"auto-merge desabilitado\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.habilitarAutoMerge(new PullRequestRef("acme/trade", 77, "u", "PR_node")))
                .isInstanceOf(BoardOperationException.class)
                .hasMessageContaining("auto-merge");
    }

    @Test
    void pullRequestSemNodeIdNaoHabilitaAutoMerge() {
        assertThatThrownBy(() -> client.habilitarAutoMerge(new PullRequestRef("acme/trade", 77, "u", null)))
                .isInstanceOf(BoardOperationException.class);
    }

    @Test
    void listaCardsAbertosComALabelDaTriagem() {
        server.expect(requestTo(
                        "https://api.github.com/repos/acme/trade/issues?labels=auto-triage&state=open&per_page=100"))
                .andRespond(withSuccess("""
                        [{"number": 123, "title": "t", "body": "b", "state": "open"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.cardsAbertosComLabel("acme/trade", "auto-triage"))
                .containsExactly(new CardRef("acme/trade", 123));
        server.verify();
    }

    @Test
    void naoMandaCorpoVazioAoCriarCard() {
        server.expect(requestTo("https://api.github.com/repos/acme/trade/issues"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"number\": 1, \"html_url\": \"u\", \"state\": \"open\"}",
                        MediaType.APPLICATION_JSON));

        client.criarCard("acme/trade", new CardContent("t", "c", List.of()));

        server.verify();
    }
}
