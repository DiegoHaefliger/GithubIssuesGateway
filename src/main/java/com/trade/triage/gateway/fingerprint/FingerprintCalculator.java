package com.trade.triage.gateway.fingerprint;

import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class FingerprintCalculator {

    private static final String ALGORITMO = "SHA-256";
    private static final int TAMANHO_HEX = 16;
    private static final String SEPARADOR = "|#|";

    private final MessageNormalizer normalizer;
    private final StackFrameSelector frameSelector;

    public FingerprintCalculator(MessageNormalizer normalizer, StackFrameSelector frameSelector) {
        this.normalizer = normalizer;
        this.frameSelector = frameSelector;
    }

    public String calculate(ErrorSignal signal, ProjectEntry projeto) {
        String material = String.join(SEPARADOR,
                nullSafe(signal.service()),
                nullSafe(signal.exceptionClass()),
                onde(signal, projeto),
                normalizer.normalize(signal.message()));
        return HexFormat.of().formatHex(digest(material)).substring(0, TAMANHO_HEX);
    }

    private String onde(ErrorSignal signal, ProjectEntry projeto) {
        return signal.localizacaoOpcional()
                .orElseGet(() -> frameSelector.topFrameDoProjeto(signal.stacktrace(), projeto.pacotesRaiz()));
    }

    private byte[] digest(String material) {
        try {
            return MessageDigest.getInstance(ALGORITMO).digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(ALGORITMO + " indisponivel na JVM", exception);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
