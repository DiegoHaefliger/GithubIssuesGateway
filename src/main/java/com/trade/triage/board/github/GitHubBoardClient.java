package com.trade.triage.board.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardComment;
import com.trade.triage.board.model.CardContent;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.CardSnapshot;
import com.trade.triage.board.model.PullRequestContent;
import com.trade.triage.board.model.PullRequestRef;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GitHubBoardClient implements BoardClient {

    private static final int PAGINA_DE_COMENTARIOS = 100;
    private static final String MUTACAO_DE_AUTO_MERGE = """
            mutation($pullRequestId: ID!) {
              enablePullRequestAutoMerge(input: {pullRequestId: $pullRequestId, mergeMethod: SQUASH}) {
                pullRequest { number }
              }
            }
            """;

    private final RestClient restClient;

    public GitHubBoardClient(RestClient githubRestClient) {
        this.restClient = githubRestClient;
    }

    @Override
    public CardRef criarCard(String repositorio, CardContent conteudo) {
        IssueResponse resposta = restClient.post()
                .uri("/repos/{owner}/{repo}/issues", owner(repositorio), nome(repositorio))
                .body(Map.of("title", conteudo.titulo(), "body", conteudo.corpo(), "labels", conteudo.labels()))
                .retrieve()
                .body(IssueResponse.class);
        if (resposta == null) {
            throw new BoardOperationException("GitHub nao devolveu a issue criada em " + repositorio);
        }
        return new CardRef(repositorio, resposta.number());
    }

    @Override
    public CardSnapshot lerCard(CardRef card) {
        IssueDetailResponse resposta = restClient.get()
                .uri("/repos/{owner}/{repo}/issues/{numero}",
                        owner(card.repositorio()), nome(card.repositorio()), card.numero())
                .retrieve()
                .body(IssueDetailResponse.class);
        if (resposta == null) {
            throw new BoardOperationException("GitHub nao devolveu a issue " + card.asString());
        }
        return new CardSnapshot(resposta.title(), resposta.body(), resposta.state());
    }

    @Override
    public List<CardComment> lerComentarios(CardRef card) {
        IssueCommentResponse[] respostas = restClient.get()
                .uri(builder -> builder.path("/repos/{owner}/{repo}/issues/{numero}/comments")
                        .queryParam("per_page", PAGINA_DE_COMENTARIOS)
                        .build(owner(card.repositorio()), nome(card.repositorio()), card.numero()))
                .retrieve()
                .body(IssueCommentResponse[].class);
        if (respostas == null) {
            return List.of();
        }
        return java.util.Arrays.stream(respostas)
                .map(resposta -> new CardComment(resposta.autor(), resposta.tipoDeAutor(),
                        resposta.createdAt(), resposta.body()))
                .toList();
    }

    @Override
    public List<CardRef> cardsAbertosComLabel(String repositorio, String label) {
        IssueDetailResponse[] respostas = restClient.get()
                .uri(builder -> builder.path("/repos/{owner}/{repo}/issues")
                        .queryParam("labels", label)
                        .queryParam("state", "open")
                        .queryParam("per_page", PAGINA_DE_COMENTARIOS)
                        .build(owner(repositorio), nome(repositorio)))
                .retrieve()
                .body(IssueDetailResponse[].class);
        if (respostas == null) {
            return List.of();
        }
        return java.util.Arrays.stream(respostas)
                .map(resposta -> new CardRef(repositorio, resposta.number()))
                .toList();
    }

    @Override
    public void comentar(CardRef card, String comentario) {
        String corpo = MARCADOR_COMENTARIO_ORQUESTRADOR + "\n" + comentario;
        restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{numero}/comments", owner(card.repositorio()), nome(card.repositorio()), card.numero())
                .body(Map.of("body", corpo))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void atualizarCorpo(CardRef card, String corpo) {
        restClient.patch()
                .uri("/repos/{owner}/{repo}/issues/{numero}", owner(card.repositorio()), nome(card.repositorio()), card.numero())
                .body(Map.of("body", corpo))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void aplicarLabel(CardRef card, String label) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{numero}/labels", owner(card.repositorio()), nome(card.repositorio()), card.numero())
                .body(Map.of("labels", List.of(label)))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void removerLabel(CardRef card, String label) {
        restClient.delete()
                .uri("/repos/{owner}/{repo}/issues/{numero}/labels/{label}", owner(card.repositorio()), nome(card.repositorio()), card.numero(), label)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void reabrir(CardRef card) {
        restClient.patch()
                .uri("/repos/{owner}/{repo}/issues/{numero}", owner(card.repositorio()), nome(card.repositorio()), card.numero())
                .body(Map.of("state", "open"))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public PullRequestRef abrirPullRequest(String repositorio, PullRequestContent conteudo) {
        PullRequestResponse resposta = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls", owner(repositorio), nome(repositorio))
                .body(Map.of(
                        "title", conteudo.titulo(),
                        "body", conteudo.corpo(),
                        "head", conteudo.branchOrigem(),
                        "base", conteudo.branchBase()))
                .retrieve()
                .body(PullRequestResponse.class);
        if (resposta == null) {
            throw new BoardOperationException("GitHub nao devolveu o pull request criado em " + repositorio);
        }
        return new PullRequestRef(repositorio, resposta.number(), resposta.htmlUrl(), resposta.nodeId());
    }

    @Override
    public void habilitarAutoMerge(PullRequestRef pullRequest) {
        if (pullRequest.nodeId() == null || pullRequest.nodeId().isBlank()) {
            throw new BoardOperationException("Pull request sem node id: " + pullRequest.asString());
        }
        JsonNode resposta = restClient.post()
                .uri("/graphql")
                .body(Map.of(
                        "query", MUTACAO_DE_AUTO_MERGE,
                        "variables", Map.of("pullRequestId", pullRequest.nodeId())))
                .retrieve()
                .body(JsonNode.class);
        if (resposta == null || resposta.has("errors")) {
            throw new BoardOperationException("GitHub recusou habilitar auto-merge em "
                    + pullRequest.asString() + ": " + (resposta == null ? "sem resposta" : resposta.path("errors")));
        }
    }

    private String owner(String repositorio) {
        return parte(repositorio, 0);
    }

    private String nome(String repositorio) {
        return parte(repositorio, 1);
    }

    private String parte(String repositorio, int indice) {
        String[] partes = repositorio.split("/");
        if (partes.length != 2) {
            throw new BoardOperationException("Repositorio fora do formato owner/repo: " + repositorio);
        }
        return partes[indice];
    }
}
