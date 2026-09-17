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
@Table(name = "fingerprint")
public class FingerprintEntity {

    @Id
    @Column(name = "fingerprint", length = 64, nullable = false)
    private String fingerprint;

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "env", nullable = false)
    private String env;

    @Column(name = "projeto", nullable = false)
    private String projeto;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "card_ref")
    private String cardRef;

    @Column(name = "occurrence_count", nullable = false)
    private long occurrenceCount;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private FingerprintState state;

    @Column(name = "auto_attempts", nullable = false)
    private int autoAttempts;

    @Version
    @Column(name = "versao_registro")
    private Long versaoRegistro;

    protected FingerprintEntity() {
    }

    public FingerprintEntity(String fingerprint, String service, String env, String projeto,
                             String ruleId, String severity, Instant occurredAt) {
        this.fingerprint = fingerprint;
        this.service = service;
        this.env = env;
        this.projeto = projeto;
        this.ruleId = ruleId;
        this.severity = severity;
        this.occurrenceCount = 1;
        this.firstSeen = occurredAt;
        this.lastSeen = occurredAt;
        this.state = FingerprintState.NOVO;
        this.autoAttempts = 0;
    }

    public void registrarOcorrencia(Instant occurredAt) {
        this.occurrenceCount++;
        if (occurredAt.isAfter(this.lastSeen)) {
            this.lastSeen = occurredAt;
        }
    }

    public void vincularCard(String cardRef) {
        this.cardRef = cardRef;
        this.state = FingerprintState.TRIADO;
    }

    public void mudarEstado(FingerprintState novoEstado) {
        this.state = novoEstado;
    }

    public void contarTentativaAutomatica() {
        this.autoAttempts++;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getService() {
        return service;
    }

    public String getEnv() {
        return env;
    }

    public String getProjeto() {
        return projeto;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCardRef() {
        return cardRef;
    }

    public long getOccurrenceCount() {
        return occurrenceCount;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public FingerprintState getState() {
        return state;
    }

    public int getAutoAttempts() {
        return autoAttempts;
    }
}
