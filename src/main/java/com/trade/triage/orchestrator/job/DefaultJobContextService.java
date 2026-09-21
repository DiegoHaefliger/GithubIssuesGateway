package com.trade.triage.orchestrator.job;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.CardSnapshot;
import com.trade.triage.gateway.evidence.EvidenceStore;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.orchestrator.web.ComentarioDoCard;
import com.trade.triage.orchestrator.web.JobContextResponse;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.persistence.repository.TriageJobRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.shared.exception.NotFoundException;
import com.trade.triage.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DefaultJobContextService implements JobContextService {

    private final TriageJobRepository jobRepository;
    private final FingerprintRepository fingerprintRepository;
    private final ProjectRegistry registry;
    private final BoardClient board;
    private final EvidenceStore evidenceStore;
    private final SecretScrubber scrubber;

    public DefaultJobContextService(TriageJobRepository jobRepository,
                                    FingerprintRepository fingerprintRepository,
                                    ProjectRegistry registry,
                                    BoardClient board,
                                    EvidenceStore evidenceStore,
                                    SecretScrubber scrubber) {
        this.jobRepository = jobRepository;
        this.fingerprintRepository = fingerprintRepository;
        this.registry = registry;
        this.board = board;
        this.evidenceStore = evidenceStore;
        this.scrubber = scrubber;
    }

    @Override
    @Transactional
    public JobContextResponse contextoDe(String jobId, Long runId) {
        TriageJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job desconhecido: " + jobId));
        if (job.getState() != JobState.EXECUTANDO) {
            throw new UnauthorizedException("Job " + jobId + " nao esta em execucao");
        }
        boolean execucaoNova = runId != null && !runId.equals(job.getRunnerRunId());
        if (execucaoNova) {
            job.registrarExecucaoDoRunner(runId);
            jobRepository.save(job);
        }
        FingerprintEntity estado = fingerprintRepository.findById(job.getFingerprint())
                .orElseThrow(() -> new NotFoundException("Fingerprint desconhecido: " + job.getFingerprint()));
        ProjectEntry projeto = registry.findByRepository(job.getRepositorio())
                .orElseThrow(() -> new NotFoundException("Projeto fora do registro: " + job.getRepositorio()));

        CardRef card = CardRef.parse(job.getCardRef());
        CardSnapshot conteudo = board.lerCard(card);
        List<ComentarioDoCard> comentarios = comentarios(card);
        if (execucaoNova) {
            board.comentar(card, "Triagem automatica em execucao: %s/actions/runs/%d (job `%s`)"
                    .formatted(urlDoRepositorio(projeto.repositorio()), runId, job.getJobId()));
        }

        return new JobContextResponse(
                job.getJobId(),
                job.getCardRef(),
                job.getFingerprint(),
                projeto.repositorio(),
                projeto.branchBase(),
                JobContextResponse.AVISO_DE_CONTEUDO_HOSTIL,
                scrubber.scrubText(conteudo.titulo()),
                scrubber.scrubText(conteudo.corpo()),
                comentarios,
                evidenceStore.load(estado.getEvidenciaUri()).orElse(null));
    }

    private String urlDoRepositorio(String repositorio) {
        return "https://github.com/" + repositorio;
    }

    private List<ComentarioDoCard> comentarios(CardRef card) {
        return board.lerComentarios(card).stream()
                .map(comentario -> new ComentarioDoCard(
                        comentario.autor(),
                        comentario.tipoDeAutor(),
                        comentario.criadoEm(),
                        scrubber.scrubText(comentario.corpo())))
                .toList();
    }
}
