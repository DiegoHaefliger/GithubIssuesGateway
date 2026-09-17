package com.trade.triage.gateway.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class StormWindow {

    private static final DateTimeFormatter JANELA =
            DateTimeFormatter.ofPattern("yyyyMMdd'H'HH").withZone(ZoneOffset.UTC);
    private static final String PREFIXO = "storm-";

    private StormWindow() {
    }

    public static String identificadorDe(String projeto, Instant momento) {
        return PREFIXO + projeto + "-" + JANELA.format(momento);
    }

    public static boolean ehTempestade(String fingerprint) {
        return fingerprint != null && fingerprint.startsWith(PREFIXO);
    }
}
