package com.trade.triage.registry;

import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectRegistrySnapshot;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RegistryValidator {

    private final Validator validator;

    public RegistryValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(ProjectRegistrySnapshot snapshot) {
        Set<ConstraintViolation<ProjectRegistrySnapshot>> violations = validator.validate(snapshot);
        if (!violations.isEmpty()) {
            throw new RegistryLoadException("Registro invalido: " + describe(violations));
        }
        rejectDuplicatedServices(snapshot.projetos());
    }

    private void rejectDuplicatedServices(List<ProjectEntry> projetos) {
        Set<String> vistos = new HashSet<>();
        for (ProjectEntry projeto : projetos) {
            for (String servico : projeto.servicos()) {
                if (!vistos.add(servico)) {
                    throw new RegistryLoadException("Servico mapeado em mais de um projeto: " + servico);
                }
            }
        }
    }

    private String describe(Set<ConstraintViolation<ProjectRegistrySnapshot>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .reduce((left, right) -> left + "; " + right)
                .orElse("sem detalhe");
    }
}
