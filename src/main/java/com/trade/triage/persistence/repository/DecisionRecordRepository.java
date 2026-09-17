package com.trade.triage.persistence.repository;

import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.DecisionRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecordEntity, String> {

    List<DecisionRecordEntity> findByFingerprint(String fingerprint);

    long countByDecisao(Decision decisao);

    long countByFingerprintAndDecisaoNot(String fingerprint, Decision decisao);
}
