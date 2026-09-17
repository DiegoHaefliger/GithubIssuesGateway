package com.trade.triage.gateway.fingerprint;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class MessageNormalizer {

    private static final List<Replacement> REPLACEMENTS = List.of(
            new Replacement(Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"), "<uuid>"),
            new Replacement(Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?"), "<timestamp>"),
            new Replacement(Pattern.compile("\\b\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\b"), "<time>"),
            new Replacement(Pattern.compile("(?<![\\w/])(/[\\w.-]+){2,}"), "<path>"),
            new Replacement(Pattern.compile("\\b0x[0-9a-fA-F]+\\b"), "<hex>"),
            new Replacement(Pattern.compile("\\b[0-9a-fA-F]{16,}\\b"), "<hash>"),
            new Replacement(Pattern.compile("[-+]?\\b\\d+[.,]\\d+([eE][-+]?\\d+)?\\b"), "<num>"),
            new Replacement(Pattern.compile("(?<![\\w<])\\d+(?![\\w>])"), "<num>"),
            new Replacement(Pattern.compile("\\s+"), " "));

    public String normalize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String normalized = message;
        for (Replacement replacement : REPLACEMENTS) {
            normalized = replacement.pattern().matcher(normalized).replaceAll(replacement.token());
        }
        return normalized.trim().toLowerCase();
    }

    private record Replacement(Pattern pattern, String token) {
    }
}
