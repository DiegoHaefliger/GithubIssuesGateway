package com.trade.triage.gate;

import com.trade.triage.registry.model.BlastRadius;

import java.util.List;

public record GateFacts(
        List<String> arquivosTocados,
        int linhasDoDiff,
        boolean testeReproduzOErro,
        boolean suiteCompletaPassa,
        boolean todosNaAllowlist,
        boolean tocaAreaProibida,
        BlastRadius blastRadius,
        String severidade,
        boolean incidenteAtivo,
        int autoAttempts) {

    public GateFacts {
        arquivosTocados = arquivosTocados == null ? List.of() : List.copyOf(arquivosTocados);
        blastRadius = blastRadius == null ? BlastRadius.CRITICO : blastRadius;
    }

    public int arquivosDoDiff() {
        return arquivosTocados.size();
    }
}
