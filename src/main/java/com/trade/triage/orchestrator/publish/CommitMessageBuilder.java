package com.trade.triage.orchestrator.publish;

import com.trade.triage.gate.GateDecision;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.registry.model.BlastRadius;
import org.springframework.stereotype.Component;

@Component
public class CommitMessageBuilder {

    private static final int TAMANHO_MAXIMO_DO_ASSUNTO = 68;

    public String build(TriageJobEntity job, TriageResult resultado, GateDecision decisao, BlastRadius blastRadius) {
        return """
                fix: %s

                Card: %s
                Fingerprint: %s
                Decisao-Gate: %s (%s)
                Blast-Radius: %s
                """.formatted(
                assunto(resultado),
                job.getCardRef(),
                job.getFingerprint(),
                decisao.decisao(),
                decisao.regraDecisora(),
                blastRadius);
    }

    private String assunto(TriageResult resultado) {
        String primeiraLinha = resultado.justificativa().lines().findFirst().orElse(resultado.hipotese());
        return primeiraLinha.length() <= TAMANHO_MAXIMO_DO_ASSUNTO
                ? primeiraLinha
                : primeiraLinha.substring(0, TAMANHO_MAXIMO_DO_ASSUNTO);
    }
}
