package com.trade.triage.gateway.scrub;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component
public class SecretScrubber {

    public static final String REDIGIDO = "[REDIGIDO]";

    private static final List<Pattern> PADROES_DE_SEGREDO = List.of(
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
            Pattern.compile("\\b(?:sk|pk|rk)_(?:live|test)_[A-Za-z0-9]{8,}\\b"),
            Pattern.compile("\\bghp_[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("(?i)\\b(?:password|senha|token|secret|api[_-]?key|authorization)\\b\\s*[=:]\\s*\"?[^\\s\",;}]+"),
            Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b"),
            Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"));

    private final ScrubProperties properties;

    public SecretScrubber(ScrubProperties properties) {
        this.properties = properties;
    }

    public String scrubText(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        String limpo = texto;
        for (Pattern padrao : PADROES_DE_SEGREDO) {
            limpo = padrao.matcher(limpo).replaceAll(REDIGIDO);
        }
        return limpo;
    }

    public Map<String, String> scrubFields(Map<String, String> campos) {
        Map<String, String> limpos = new TreeMap<>();
        campos.forEach((chave, valor) -> limpos.put(chave, valorDe(chave, valor)));
        return Map.copyOf(limpos);
    }

    private String valorDe(String chave, String valor) {
        String normalizada = chave.toLowerCase(Locale.ROOT);
        if (ehSensivel(normalizada) || !ehConhecido(normalizada)) {
            return REDIGIDO;
        }
        return scrubText(valor);
    }

    private boolean ehSensivel(String chave) {
        return properties.camposSensiveis().stream().anyMatch(chave::contains);
    }

    private boolean ehConhecido(String chave) {
        return properties.camposConhecidos().contains(chave);
    }
}
