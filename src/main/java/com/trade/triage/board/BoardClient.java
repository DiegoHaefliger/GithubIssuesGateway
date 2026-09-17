package com.trade.triage.board;

import com.trade.triage.board.model.CardComment;
import com.trade.triage.board.model.CardContent;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.CardSnapshot;
import com.trade.triage.board.model.PullRequestContent;
import com.trade.triage.board.model.PullRequestRef;

import java.util.List;

public interface BoardClient {

    CardRef criarCard(String repositorio, CardContent conteudo);

    CardSnapshot lerCard(CardRef card);

    List<CardComment> lerComentarios(CardRef card);

    void comentar(CardRef card, String comentario);

    void aplicarLabel(CardRef card, String label);

    void removerLabel(CardRef card, String label);

    void reabrir(CardRef card);

    PullRequestRef abrirPullRequest(String repositorio, PullRequestContent conteudo);
}
