package com.trade.triage.persistence.entity;

import com.trade.triage.registry.model.BlastRadius;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "decision_record")
public class DecisionRecordEntity {

    @Id
    @Column(name = "job_id", length = 64, nullable = false)
    private String jobId;

    @Column(name = "card_ref", nullable = false)
    private String cardRef;

    @Column(name = "fingerprint", length = 64, nullable = false)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "decisao", nullable = false, length = 32)
    private Decision decisao;

    @Column(name = "regra_decisora", nullable = false)
    private String regraDecisora;

    @Column(name = "registro_versao", nullable = false)
    private String registroVersao;

    @Column(name = "diff_arquivos", nullable = false)
    private int diffArquivos;

    @Column(name = "diff_linhas", nullable = false)
    private int diffLinhas;

    @Column(name = "teste_reproduz", nullable = false)
    private boolean testeReproduz;

    @Column(name = "allowlist_ok", nullable = false)
    private boolean allowlistOk;

    @Enumerated(EnumType.STRING)
    @Column(name = "blast_radius", nullable = false, length = 16)
    private BlastRadius blastRadius;

    @Column(name = "pr_ref")
    private String prRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "desfecho", nullable = false, length = 32)
    private Outcome desfecho;

    @Column(name = "decidido_em", nullable = false)
    private Instant decididoEm;

    protected DecisionRecordEntity() {
    }

    public DecisionRecordEntity(String jobId, String cardRef, String fingerprint, Decision decisao,
                                String regraDecisora, String registroVersao, int diffArquivos,
                                int diffLinhas, boolean testeReproduz, boolean allowlistOk,
                                BlastRadius blastRadius, Instant decididoEm) {
        this.jobId = jobId;
        this.cardRef = cardRef;
        this.fingerprint = fingerprint;
        this.decisao = decisao;
        this.regraDecisora = regraDecisora;
        this.registroVersao = registroVersao;
        this.diffArquivos = diffArquivos;
        this.diffLinhas = diffLinhas;
        this.testeReproduz = testeReproduz;
        this.allowlistOk = allowlistOk;
        this.blastRadius = blastRadius;
        this.desfecho = Outcome.PENDENTE;
        this.decididoEm = decididoEm;
    }

    public void registrarPr(String prRef) {
        this.prRef = prRef;
    }

    public void registrarDesfecho(Outcome desfecho) {
        this.desfecho = desfecho;
    }

    public String getJobId() {
        return jobId;
    }

    public String getCardRef() {
        return cardRef;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Decision getDecisao() {
        return decisao;
    }

    public String getRegraDecisora() {
        return regraDecisora;
    }

    public String getRegistroVersao() {
        return registroVersao;
    }

    public int getDiffArquivos() {
        return diffArquivos;
    }

    public int getDiffLinhas() {
        return diffLinhas;
    }

    public boolean isTesteReproduz() {
        return testeReproduz;
    }

    public boolean isAllowlistOk() {
        return allowlistOk;
    }

    public BlastRadius getBlastRadius() {
        return blastRadius;
    }

    public String getPrRef() {
        return prRef;
    }

    public Outcome getDesfecho() {
        return desfecho;
    }

    public Instant getDecididoEm() {
        return decididoEm;
    }
}
