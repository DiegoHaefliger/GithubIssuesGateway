package com.trade.triage.board;

import com.trade.triage.board.model.CardContent;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.PullRequestContent;
import com.trade.triage.board.model.PullRequestRef;

public interface BoardClient {

    CardRef criarCard(String repositorio, CardContent conteudo);

    void comentar(CardRef card, String comentario);

    void aplicarLabel(CardRef card, String label);

    void removerLabel(CardRef card, String label);

    void reabrir(CardRef card);

    PullRequestRef abrirPullRequest(String repositorio, PullRequestContent conteudo);
}
