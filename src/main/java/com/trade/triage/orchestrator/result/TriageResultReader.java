package com.trade.triage.orchestrator.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.triage.persistence.entity.TriageJobEntity;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class TriageResultReader {

    private final ResultFetcher fetcher;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TriageResultReader(ResultFetcher fetcher, ObjectMapper objectMapper, Validator validator) {
        this.fetcher = fetcher;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public TriageResult read(TriageJobEntity job) {
        String bruto = fetcher.fetchRawJson(job)
                .orElseThrow(() -> new InvalidResultException("resultado.json ausente para o job " + job.getJobId()));
        TriageResult resultado = desserializar(bruto, job);
        validar(resultado, job);
        conferirIdentidade(resultado, job);
        return resultado;
    }

    private TriageResult desserializar(String bruto, TriageJobEntity job) {
        try {
            return objectMapper.readValue(bruto, TriageResult.class);
        } catch (Exception exception) {
            throw new InvalidResultException("resultado.json fora do schema no job " + job.getJobId(), exception);
        }
    }

    private void validar(TriageResult resultado, TriageJobEntity job) {
        Set<ConstraintViolation<TriageResult>> violacoes = validator.validate(resultado);
        if (!violacoes.isEmpty()) {
            throw new InvalidResultException("resultado.json invalido no job " + job.getJobId() + ": "
                    + violacoes.stream()
                    .map(violacao -> violacao.getPropertyPath() + " " + violacao.getMessage())
                    .sorted()
                    .reduce((esquerda, direita) -> esquerda + "; " + direita)
                    .orElse("sem detalhe"));
        }
    }

    private void conferirIdentidade(TriageResult resultado, TriageJobEntity job) {
        if (!job.getJobId().equals(resultado.jobId()) || !job.getCardRef().equals(resultado.cardRef())) {
            throw new InvalidResultException("resultado.json aponta para outro job ou card: "
                    + resultado.jobId() + " / " + resultado.cardRef());
        }
    }
}
