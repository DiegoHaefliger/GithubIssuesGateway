package com.trade.triage.board.github;

import com.trade.triage.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class WebhookSignatureVerifier {

    private static final String ALGORITMO = "HmacSHA256";
    private static final String PREFIXO = "sha256=";

    private final GitHubProperties properties;

    public WebhookSignatureVerifier(GitHubProperties properties) {
        this.properties = properties;
    }

    public void verify(String payload, String assinaturaRecebida) {
        String segredo = properties.webhookSecret();
        if (segredo == null || segredo.isBlank()) {
            throw new UnauthorizedException("Webhook secret nao configurado");
        }
        if (assinaturaRecebida == null || !assinaturaRecebida.startsWith(PREFIXO)) {
            throw new UnauthorizedException("Assinatura do webhook ausente ou malformada");
        }
        byte[] esperada = assinar(payload, segredo).getBytes(StandardCharsets.UTF_8);
        byte[] recebida = assinaturaRecebida.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperada, recebida)) {
            throw new UnauthorizedException("Assinatura do webhook nao confere");
        }
    }

    private String assinar(String payload, String segredo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return PREFIXO + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha ao calcular assinatura do webhook", exception);
        }
    }
}
