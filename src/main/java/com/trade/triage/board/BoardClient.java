package com.trade.triage.board;

import com.trade.triage.board.model.CardComment;
import com.trade.triage.board.model.CardContent;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.CardSnapshot;
import com.trade.triage.board.model.PullRequestContent;
import com.trade.triage.board.model.PullRequestRef;

import java.util.List;

public interface BoardClient {

    /**
     * Marca todo comentario postado pelo orquestrador. {@code sender.type == "Bot"}
     * nao basta: {@code TRIAGE_GITHUB_TOKEN} e um PAT pessoal, entao o webhook
     * ve o comentario do orquestrador como se fosse humano. Sem esse marcador,
     * cada analise publicada dispara outro job nela mesma (loop).
     */
    String MARCADOR_COMENTARIO_ORQUESTRADOR = "<!-- triage-orquestrador: nao retriagem -->";

    CardRef criarCard(String repositorio, CardContent conteudo);

    CardSnapshot lerCard(CardRef card);

    List<CardComment> lerComentarios(CardRef card);

    List<CardRef> cardsAbertosComLabel(String repositorio, String label);

    void comentar(CardRef card, String comentario);

    void atualizarCorpo(CardRef card, String corpo);

    void aplicarLabel(CardRef card, String label);

    void removerLabel(CardRef card, String label);

    void reabrir(CardRef card);

    PullRequestRef abrirPullRequest(String repositorio, PullRequestContent conteudo);

    void habilitarAutoMerge(PullRequestRef pullRequest);
}
