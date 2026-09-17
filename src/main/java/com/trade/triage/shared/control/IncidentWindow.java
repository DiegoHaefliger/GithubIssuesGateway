package com.trade.triage.shared.control;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class IncidentWindow {

    private final Path arquivo;

    public IncidentWindow(IncidentWindowProperties properties) {
        this.arquivo = Path.of(properties.arquivo());
    }

    public boolean incidenteAtivo() {
        return Files.exists(arquivo);
    }
}
