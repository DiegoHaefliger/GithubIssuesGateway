package com.trade.triage.shared.config;

import com.trade.triage.gateway.web.GrafanaWebhookAuthInterceptor;
import com.trade.triage.gateway.web.GrafanaWebhookAuthenticator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private static final String ROTA_DO_WEBHOOK_DO_GRAFANA = "/webhooks/grafana/**";

    private final ObjectProvider<GrafanaWebhookAuthenticator> authenticator;

    public WebMvcConfiguration(ObjectProvider<GrafanaWebhookAuthenticator> authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GrafanaWebhookAuthInterceptor(authenticator.getIfAvailable()))
                .addPathPatterns(ROTA_DO_WEBHOOK_DO_GRAFANA);
    }
}
