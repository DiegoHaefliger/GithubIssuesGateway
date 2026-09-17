package com.trade.triage.gateway.web;

import com.trade.triage.shared.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

public class GrafanaWebhookAuthInterceptor implements HandlerInterceptor {

    private final GrafanaWebhookAuthenticator authenticator;

    public GrafanaWebhookAuthInterceptor(GrafanaWebhookAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (authenticator == null) {
            throw new UnauthorizedException("Autenticacao do webhook do Grafana indisponivel");
        }
        authenticator.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION));
        return true;
    }
}
