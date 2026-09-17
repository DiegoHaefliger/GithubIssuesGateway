package com.trade.triage.orchestrator.outcome;

import com.trade.triage.metrics.TriageMetrics;
import com.trade.triage.persistence.entity.DecisionRecordEntity;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.entity.Outcome;
import com.trade.triage.persistence.repository.DecisionRecordRepository;
import com.trade.triage.persistence.repository.FingerprintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultOutcomeService implements OutcomeService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultOutcomeService.class);
    private static final Pattern TRAILER_DE_FINGERPRINT =
            Pattern.compile("^Fingerprint:\\s*(\\S+)\\s*$", Pattern.MULTILINE);
    private static final Pattern MARCA_DE_REVERSAO =
            Pattern.compile("^(Revert \"|This reverts commit )", Pattern.MULTILINE);

    private final DecisionRecordRepository decisionRepository;
    private final FingerprintRepository fingerprintRepository;
    private final TriageMetrics metrics;

    public DefaultOutcomeService(DecisionRecordRepository decisionRepository,
                                 FingerprintRepository fingerprintRepository,
                                 TriageMetrics metrics) {
        this.decisionRepository = decisionRepository;
        this.fingerprintRepository = fingerprintRepository;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public void registrarPullRequestFechado(String prRef, boolean merged) {
        Optional<DecisionRecordEntity> registro = decisionRepository.findByPrRef(prRef);
        if (registro.isEmpty()) {
            return;
        }
        DecisionRecordEntity decisao = registro.get();
        if (decisao.getDesfecho() != Outcome.PENDENTE) {
            return;
        }
        Outcome desfecho = merged ? Outcome.MERGED : Outcome.REJEITADO;
        decisao.registrarDesfecho(desfecho);
        decisionRepository.save(decisao);
        metrics.contarDesfecho(desfecho, decisao.getDecisao());

        if (merged) {
            marcarFingerprint(decisao.getFingerprint(), FingerprintState.RESOLVIDO);
        }
        LOG.info("desfecho registrado pr={} desfecho={}", prRef, desfecho);
    }

    @Override
    @Transactional
    public void registrarCardFechado(String cardRef) {
        fingerprintRepository.findByCardRef(cardRef).ifPresent(estado -> {
            estado.mudarEstado(FingerprintState.RESOLVIDO);
            fingerprintRepository.save(estado);
            LOG.info("card fechado, fingerprint resolvido card={} fingerprint={}",
                    cardRef, estado.getFingerprint());
        });
    }

    @Override
    @Transactional
    public void registrarReversoes(List<String> mensagensDeCommit) {
        mensagensDeCommit.stream()
                .filter(mensagem -> MARCA_DE_REVERSAO.matcher(mensagem).find())
                .flatMap(mensagem -> fingerprintDe(mensagem).stream())
                .distinct()
                .forEach(this::reverter);
    }

    private void reverter(String fingerprint) {
        List<DecisionRecordEntity> merged =
                decisionRepository.findByFingerprintAndDesfecho(fingerprint, Outcome.MERGED);
        merged.forEach(decisao -> {
            decisao.registrarDesfecho(Outcome.REVERTIDO);
            decisionRepository.save(decisao);
            metrics.contarDesfecho(Outcome.REVERTIDO, decisao.getDecisao());
            LOG.warn("pr de triagem revertido pr={} fingerprint={}", decisao.getPrRef(), fingerprint);
        });
        if (!merged.isEmpty()) {
            marcarFingerprint(fingerprint, FingerprintState.AGUARDANDO_HUMANO);
        }
    }

    private void marcarFingerprint(String fingerprint, FingerprintState estado) {
        Optional<FingerprintEntity> encontrado = fingerprintRepository.findById(fingerprint);
        encontrado.ifPresent(entidade -> {
            entidade.mudarEstado(estado);
            fingerprintRepository.save(entidade);
        });
    }

    private Optional<String> fingerprintDe(String mensagem) {
        Matcher matcher = TRAILER_DE_FINGERPRINT.matcher(mensagem);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
