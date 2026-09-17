package com.trade.triage.shared.control;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class KillSwitch {

    private final Path arquivo;
    private final boolean desligadoPorPadrao;

    public KillSwitch(KillSwitchProperties properties) {
        this.arquivo = Path.of(properties.arquivo());
        this.desligadoPorPadrao = properties.desligadoPorPadrao();
    }

    public boolean acionado() {
        return desligadoPorPadrao || Files.exists(arquivo);
    }

    public String motivo() {
        return desligadoPorPadrao
                ? "triagem desligada por configuracao"
                : "arquivo de parada presente em " + arquivo.toAbsolutePath();
    }
}
