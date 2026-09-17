package com.trade.triage.persistence.entity;

import java.util.Map;
import java.util.Set;

public enum JobState {
    PENDENTE,
    EXECUTANDO,
    PRONTO,
    FALHOU,
    EXPIRADO,
    AVALIADO,
    INVALIDO,
    PUBLICADO,
    BARRADO;

    private static final Map<JobState, Set<JobState>> TRANSICOES = Map.of(
            PENDENTE, Set.of(EXECUTANDO, EXPIRADO),
            EXECUTANDO, Set.of(PRONTO, FALHOU, EXPIRADO),
            PRONTO, Set.of(AVALIADO, INVALIDO),
            AVALIADO, Set.of(PUBLICADO, BARRADO),
            FALHOU, Set.of(),
            EXPIRADO, Set.of(),
            INVALIDO, Set.of(),
            PUBLICADO, Set.of(),
            BARRADO, Set.of());

    private static final Set<JobState> TERMINAIS =
            Set.of(FALHOU, EXPIRADO, INVALIDO, PUBLICADO, BARRADO);

    public boolean terminal() {
        return TERMINAIS.contains(this);
    }

    public boolean escalaParaHumano() {
        return terminal() && this != PUBLICADO;
    }

    public boolean podeIrPara(JobState destino) {
        return TRANSICOES.get(this).contains(destino);
    }
}
