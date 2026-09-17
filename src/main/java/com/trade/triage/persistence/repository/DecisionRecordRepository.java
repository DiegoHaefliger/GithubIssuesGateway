package com.trade.triage.persistence.repository;

import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.DecisionRecordEntity;
import com.trade.triage.persistence.entity.Outcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecordEntity, String> {

    List<DecisionRecordEntity> findByFingerprint(String fingerprint);

    Optional<DecisionRecordEntity> findByPrRef(String prRef);

    List<DecisionRecordEntity> findByFingerprintAndDesfecho(String fingerprint, Outcome desfecho);

    long countByDecisao(Decision decisao);

    long countByFingerprintAndDecisaoNot(String fingerprint, Decision decisao);
}
