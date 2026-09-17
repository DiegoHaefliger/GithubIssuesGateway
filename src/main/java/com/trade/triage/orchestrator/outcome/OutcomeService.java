package com.trade.triage.orchestrator.outcome;

import java.util.List;

public interface OutcomeService {

    void registrarPullRequestFechado(String prRef, boolean merged);

    void registrarCardFechado(String cardRef);

    void registrarReversoes(List<String> mensagensDeCommit);
}
