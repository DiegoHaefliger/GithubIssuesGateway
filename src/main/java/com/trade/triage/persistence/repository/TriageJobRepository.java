package com.trade.triage.persistence.repository;

import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TriageJobRepository extends JpaRepository<TriageJobEntity, String> {

    List<TriageJobEntity> findByStateOrderByCriadoEmAsc(JobState state);

    List<TriageJobEntity> findByStateInAndPrazoBefore(List<JobState> states, Instant momento);

    Optional<TriageJobEntity> findByCardRefAndStateIn(String cardRef, List<JobState> states);

    long countByProjetoAndCriadoEmAfter(String projeto, Instant desde);

    long countByStateIn(List<JobState> states);
}
