package com.trade.triage.gateway.web;

import com.trade.triage.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class GrafanaWebhookAuthenticator {

    private static final String ESQUEMA_BEARER = "Bearer ";
    private static final String ESQUEMA_BASIC = "Basic ";

    private final GrafanaWebhookProperties properties;

    public GrafanaWebhookAuthenticator(GrafanaWebhookProperties properties) {
        this.properties = properties;
    }

    public void authenticate(String authorization) {
        String esperado = properties.webhookToken();
        if (esperado == null || esperado.isBlank()) {
            throw new UnauthorizedException("Token do webhook do Grafana nao configurado");
        }
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("Webhook do Grafana sem cabecalho Authorization");
        }
        if (!confere(esperado, credencialRecebida(authorization))) {
            throw new UnauthorizedException("Credencial do webhook do Grafana nao confere");
        }
    }

    private String credencialRecebida(String authorization) {
        if (authorization.startsWith(ESQUEMA_BEARER)) {
            return authorization.substring(ESQUEMA_BEARER.length()).trim();
        }
        if (authorization.startsWith(ESQUEMA_BASIC)) {
            return senhaDoBasic(authorization.substring(ESQUEMA_BASIC.length()).trim());
        }
        throw new UnauthorizedException("Esquema de autenticacao nao suportado no webhook do Grafana");
    }

    private String senhaDoBasic(String codificado) {
        try {
            String decodificado = new String(Base64.getDecoder().decode(codificado), StandardCharsets.UTF_8);
            int separador = decodificado.indexOf(':');
            if (separador < 0) {
                throw new UnauthorizedException("Credencial Basic malformada no webhook do Grafana");
            }
            if (!properties.webhookUsuario().equals(decodificado.substring(0, separador))) {
                throw new UnauthorizedException("Usuario do webhook do Grafana nao confere");
            }
            return decodificado.substring(separador + 1);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Credencial Basic ilegivel no webhook do Grafana");
        }
    }

    private boolean confere(String esperado, String recebido) {
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8));
    }
}
