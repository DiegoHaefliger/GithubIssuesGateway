package com.trade.triage.gateway.fingerprint;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StackFrameSelector {

    private static final Pattern FRAME = Pattern.compile("^\\s*at\\s+([\\w.$<>]+)\\(([^)]*)\\)");
    private static final String FRAME_DESCONHECIDO = "<sem-frame-do-projeto>";

    public String topFrameDoProjeto(String stacktrace, List<String> pacotesRaiz) {
        if (stacktrace == null || stacktrace.isBlank()) {
            return FRAME_DESCONHECIDO;
        }
        return stacktrace.lines()
                .map(FRAME::matcher)
                .filter(Matcher::find)
                .filter(matcher -> pertenceAoProjeto(matcher.group(1), pacotesRaiz))
                .map(this::formatar)
                .findFirst()
                .orElse(FRAME_DESCONHECIDO);
    }

    private boolean pertenceAoProjeto(String metodo, List<String> pacotesRaiz) {
        return pacotesRaiz.stream().anyMatch(metodo::startsWith);
    }

    private String formatar(Matcher matcher) {
        return matcher.group(1) + "(" + linhaDe(matcher.group(2)).orElse("?") + ")";
    }

    private Optional<String> linhaDe(String origem) {
        int separador = origem.lastIndexOf(':');
        return separador < 0 ? Optional.empty() : Optional.of(origem.substring(separador + 1));
    }
}
