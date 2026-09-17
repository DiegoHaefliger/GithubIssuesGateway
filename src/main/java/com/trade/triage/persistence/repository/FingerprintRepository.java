package com.trade.triage.persistence.repository;

import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FingerprintRepository extends JpaRepository<FingerprintEntity, String> {

    Optional<FingerprintEntity> findByCardRef(String cardRef);

    List<FingerprintEntity> findByProjetoAndStateNot(String projeto, FingerprintState state);

    long countByProjetoAndFirstSeenAfterAndFingerprintNotLike(String projeto, Instant desde, String padrao);

    long countByStateAndLastSeenAfter(FingerprintState state, Instant desde);
}
