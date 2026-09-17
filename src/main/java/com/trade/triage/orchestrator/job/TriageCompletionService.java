package com.trade.triage.orchestrator.job;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.PullRequestContent;
import com.trade.triage.board.model.PullRequestRef;
import com.trade.triage.gate.GateDecision;
import com.trade.triage.gate.GateFacts;
import com.trade.triage.gate.PolicyGate;
import com.trade.triage.gate.verification.GateFactsVerifier;
import com.trade.triage.metrics.TriageMetrics;
import com.trade.triage.orchestrator.publish.AnalysisCommentBuilder;
import com.trade.triage.orchestrator.publish.PatchPublisher;
import com.trade.triage.orchestrator.publish.PullRequestBodyBuilder;
import com.trade.triage.orchestrator.result.InvalidResultException;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.orchestrator.result.TriageResultReader;
import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.DecisionRecordEntity;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.DecisionRecordRepository;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.persistence.repository.TriageJobRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class TriageCompletionService {

    private static final Logger LOG = LoggerFactory.getLogger(TriageCompletionService.class);
    private static final String LABEL_AGUARDANDO_HUMANO = "aguardando-humano";

    private final TriageJobRepository jobRepository;
    private final FingerprintRepository fingerprintRepository;
    private final DecisionRecordRepository decisionRepository;
    private final ProjectRegistry registry;
    private final TriageResultReader resultReader;
    private final GateFactsVerifier verifier;
    private final PolicyGate gate;
    private final PatchPublisher patchPublisher;
    private final AnalysisCommentBuilder commentBuilder;
    private final PullRequestBodyBuilder pullRequestBuilder;
    private final BoardClient board;
    private final TriageMetrics metrics;
    private final Clock clock;

    public TriageCompletionService(TriageJobRepository jobRepository,
                                   FingerprintRepository fingerprintRepository,
                                   DecisionRecordRepository decisionRepository,
                                   ProjectRegistry registry,
                                   TriageResultReader resultReader,
                                   GateFactsVerifier verifier,
                                   PolicyGate gate,
                                   PatchPublisher patchPublisher,
                                   AnalysisCommentBuilder commentBuilder,
                                   PullRequestBodyBuilder pullRequestBuilder,
                                   BoardClient board,
                                   TriageMetrics metrics,
                                   Clock clock) {
        this.jobRepository = jobRepository;
        this.fingerprintRepository = fingerprintRepository;
        this.decisionRepository = decisionRepository;
        this.registry = registry;
        this.resultReader = resultReader;
        this.verifier = verifier;
        this.gate = gate;
        this.patchPublisher = patchPublisher;
        this.commentBuilder = commentBuilder;
        this.pullRequestBuilder = pullRequestBuilder;
        this.board = board;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void concluir(String jobId) {
        TriageJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job desconhecido: " + jobId));
        if (job.getState() != JobState.PRONTO) {
            return;
        }
        FingerprintEntity estado = fingerprintRepository.findById(job.getFingerprint())
                .orElseThrow(() -> new NotFoundException("Fingerprint desconhecido: " + job.getFingerprint()));
        ProjectEntry projeto = registry.findByRepository(job.getRepositorio())
                .orElseThrow(() -> new NotFoundException("Projeto fora do registro: " + job.getRepositorio()));

        TriageResult resultado;
        try {
            resultado = resultReader.read(job);
        } catch (InvalidResultException exception) {
            invalidar(job, estado, exception.getMessage());
            return;
        }

        GateFacts fatos = verifier.verificar(resultado, projeto, estado.getSeverity(), estado.getAutoAttempts());
        GateDecision decisao = gate.decidir(fatos, projeto);
        job.transicionar(JobState.AVALIADO, clock.instant());

        CardRef card = CardRef.parse(job.getCardRef());
        board.comentar(card, commentBuilder.build(resultado, decisao));

        DecisionRecordEntity registro = registrar(job, estado, fatos, decisao);
        if (decisao.decisao() == Decision.HUMAN || !resultado.temProposta()) {
            barrar(job, estado, card);
            return;
        }
        publicar(job, estado, projeto, resultado, fatos, decisao, registro, card);
    }

    private void publicar(TriageJobEntity job, FingerprintEntity estado, ProjectEntry projeto,
                          TriageResult resultado, GateFacts fatos, GateDecision decisao,
                          DecisionRecordEntity registro, CardRef card) {
        String branch = patchPublisher.publicarBranch(job, projeto, resultado, decisao, fatos.blastRadius());
        PullRequestRef pullRequest = board.abrirPullRequest(projeto.repositorio(), new PullRequestContent(
                pullRequestBuilder.titulo(resultado),
                pullRequestBuilder.build(resultado, decisao, fatos, estado, estado.getEvidenciaUri()),
                branch,
                projeto.branchBase()));

        registro.registrarPr(pullRequest.asString());
        decisionRepository.save(registro);
        board.comentar(card, "PR aberto pela triagem: " + pullRequest.url());
        board.aplicarLabel(card, rotuloDaDecisao(decisao));

        estado.contarTentativaAutomatica();
        estado.mudarEstado(FingerprintState.EM_CORRECAO);
        fingerprintRepository.save(estado);

        job.transicionar(JobState.PUBLICADO, clock.instant());
        jobRepository.save(job);
        LOG.info("pr publicado job={} card={} pr={}", job.getJobId(), job.getCardRef(), pullRequest.asString());
    }

    private void barrar(TriageJobEntity job, FingerprintEntity estado, CardRef card) {
        board.aplicarLabel(card, LABEL_AGUARDANDO_HUMANO);
        estado.mudarEstado(FingerprintState.AGUARDANDO_HUMANO);
        fingerprintRepository.save(estado);
        job.transicionar(JobState.BARRADO, clock.instant(), "gate mandou para humano");
        jobRepository.save(job);
    }

    private void invalidar(TriageJobEntity job, FingerprintEntity estado, String motivo) {
        job.transicionar(JobState.INVALIDO, clock.instant(), motivo);
        jobRepository.save(job);
        estado.mudarEstado(FingerprintState.AGUARDANDO_HUMANO);
        fingerprintRepository.save(estado);

        CardRef card = CardRef.parse(job.getCardRef());
        board.comentar(card, """
                A triagem automatica nao produziu um resultado valido.

                - Job: `%s`
                - Motivo: %s

                O card fica para analise humana.
                """.formatted(job.getJobId(), motivo));
        board.aplicarLabel(card, LABEL_AGUARDANDO_HUMANO);
        LOG.warn("resultado invalido job={} motivo={}", job.getJobId(), motivo);
    }

    private DecisionRecordEntity registrar(TriageJobEntity job, FingerprintEntity estado,
                                           GateFacts fatos, GateDecision decisao) {
        DecisionRecordEntity registro = new DecisionRecordEntity(
                job.getJobId(), job.getCardRef(), job.getFingerprint(), decisao.decisao(),
                decisao.regraDecisora(), registry.version(), fatos.arquivosDoDiff(), fatos.linhasDoDiff(),
                fatos.testeReproduzOErro(), fatos.todosNaAllowlist(), fatos.blastRadius(), clock.instant());
        metrics.contarDecisao(decisao.decisao(), decisao.regraDecisora());
        return decisionRepository.save(registro);
    }

    private String rotuloDaDecisao(GateDecision decisao) {
        return switch (decisao.decisao()) {
            case AUTO_FIX -> "decisao/auto-fix";
            case PROPOSE_PATCH -> "decisao/proposta";
            case HUMAN -> "decisao/humano";
        };
    }
}
