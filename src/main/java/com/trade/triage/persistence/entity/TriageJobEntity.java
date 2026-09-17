package com.trade.triage.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "triage_job")
public class TriageJobEntity {

    @Id
    @Column(name = "job_id", length = 64, nullable = false)
    private String jobId;

    @Column(name = "fingerprint", length = 64, nullable = false)
    private String fingerprint;

    @Column(name = "projeto", nullable = false)
    private String projeto;

    @Column(name = "repositorio", nullable = false)
    private String repositorio;

    @Column(name = "card_ref", nullable = false)
    private String cardRef;

    @Column(name = "evento_origem", nullable = false, length = 128)
    private String eventoOrigem;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private JobState state;

    @Column(name = "runner_ref")
    private String runnerRef;

    @Column(name = "runner_run_id")
    private Long runnerRunId;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "prazo", nullable = false)
    private Instant prazo;

    @Version
    @Column(name = "versao_registro")
    private Long versaoRegistro;

    protected TriageJobEntity() {
    }

    public TriageJobEntity(String jobId, String fingerprint, String projeto, String repositorio,
                           String cardRef, String eventoOrigem, Instant criadoEm, Instant prazo) {
        this.jobId = jobId;
        this.fingerprint = fingerprint;
        this.projeto = projeto;
        this.repositorio = repositorio;
        this.cardRef = cardRef;
        this.eventoOrigem = eventoOrigem;
        this.state = JobState.PENDENTE;
        this.criadoEm = criadoEm;
        this.atualizadoEm = criadoEm;
        this.prazo = prazo;
    }

    public void transicionar(JobState destino, Instant momento) {
        if (!state.podeIrPara(destino)) {
            throw new IllegalJobTransitionException(jobId, state, destino);
        }
        this.state = destino;
        this.atualizadoEm = momento;
    }

    public void transicionar(JobState destino, Instant momento, String motivo) {
        transicionar(destino, momento);
        this.motivo = motivo;
    }

    public void registrarRunner(String runnerRef) {
        this.runnerRef = runnerRef;
    }

    public void registrarExecucaoDoRunner(Long runnerRunId) {
        this.runnerRunId = runnerRunId;
    }

    public boolean vencido(Instant momento) {
        return !state.terminal() && momento.isAfter(prazo);
    }

    public String getJobId() {
        return jobId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getProjeto() {
        return projeto;
    }

    public String getRepositorio() {
        return repositorio;
    }

    public String getCardRef() {
        return cardRef;
    }

    public String getEventoOrigem() {
        return eventoOrigem;
    }

    public JobState getState() {
        return state;
    }

    public String getRunnerRef() {
        return runnerRef;
    }

    public Long getRunnerRunId() {
        return runnerRunId;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Instant getPrazo() {
        return prazo;
    }
}
