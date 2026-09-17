package com.trade.triage.board.github;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardContent;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.PullRequestContent;
import com.trade.triage.board.model.PullRequestRef;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GitHubBoardClient implements BoardClient {

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
    public void comentar(CardRef card, String comentario) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{numero}/comments", owner(card.repositorio()), nome(card.repositorio()), card.numero())
                .body(Map.of("body", comentario))
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
        return new PullRequestRef(repositorio, resposta.number(), resposta.htmlUrl());
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
